package sa.talla;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * The weather where the car is (the phone's last known place) or in the settings' city: Open-Meteo's temperature, sky and the day's
 * high and low, and its air-quality dust as Bayti TV reads it («غبار»). For the warnings it also reads the next three hours' sky,
 * strongest gusts and lowest visibility. Asked at most every 30 minutes; the last answer is kept on the phone, and one older than
 * 3 hours is not shown.
 */
final class Weather {
    static final long REFRESH_MS = 30 * 60 * 1000L;
    private static final long STALE_MS = 3 * 60 * 60 * 1000L;

    final double temp, high, low, dust;
    /** the next three hours: the strongest gust (km/h), the lowest visibility (m), the worst sky (WMO code); NaN / -1 unknown */
    final double gust, visibility;
    final int code, worst;
    final String place;
    final long at;

    private Weather(double temp, double high, double low, double dust, double gust, double visibility, int code, int worst,
                    String place, long at) {
        this.temp = temp; this.high = high; this.low = low; this.dust = dust; this.gust = gust; this.visibility = visibility;
        this.code = code; this.worst = worst; this.place = place; this.at = at;
    }

    /**
     * the one warning worth a driver's glance, the most serious first, «» for none: storms, thick dust, poor visibility, heavy rain,
     * strong wind, fog, dust, great heat
     */
    String warning(boolean latin) {
        int c = Math.max(code, worst);
        if (c >= 95) return "عواصف رعدية متوقعة";
        if (dust >= 400) return "غبار كثيف · الرؤية ضعيفة";
        if (!Double.isNaN(visibility) && visibility < 1000) return "رؤية منخفضة: أقل من كيلومتر";
        if (c == 65 || c == 67 || c == 82 || c == 75 || c == 86) return "أمطار غزيرة متوقعة";
        if (!Double.isNaN(gust) && gust >= 50) return "رياح شديدة، هبات " + Texts.digits(Long.toString(Math.round(gust)), latin) + " كم/س";
        if (c == 45 || c == 48) return "ضباب";
        if (dust >= 150) return "غبار · انتبه للرؤية";
        if (!Double.isNaN(high) && high >= 47) return "حر شديد";
        return "";
    }

    boolean fresh(long now) { return now - at < STALE_MS; }

    /** the last answer kept, or null */
    static Weather kept(Context c) {
        SharedPreferences sp = prefs(c);
        if (!sp.contains("code")) return null;
        return new Weather(sp.getFloat("temp", 0), sp.getFloat("high", 0), sp.getFloat("low", 0), sp.getFloat("dust", 0),
                sp.getFloat("gust", Float.NaN), sp.getFloat("vis", Float.NaN), sp.getInt("code", -1), sp.getInt("worst", -1),
                sp.getString("place", ""), sp.getLong("at", 0));
    }

    static boolean due(Context c) { return System.currentTimeMillis() - prefs(c).getLong("tried", 0) >= REFRESH_MS; }

    /** Blocking. Asks Open-Meteo and keeps the answer; null when offline. */
    static Weather fetch(Context c, Settings s) {
        prefs(c).edit().putLong("tried", System.currentTimeMillis()).apply();
        double lat = s.lat(), lon = s.lon();
        String place = s.city();
        Location here = s.weatherHere() ? lastKnown(c) : null;
        if (here != null) {
            lat = here.getLatitude();
            lon = here.getLongitude();
            String named = placeName(c, lat, lon);
            place = named != null ? named : place;
        }
        try {
            String q = "?latitude=" + String.format(Locale.US, "%.4f", lat) + "&longitude=" + String.format(Locale.US, "%.4f", lon)
                    + "&timezone=" + URLEncoder.encode(s.tz(), "UTF-8");
            JSONObject root = new JSONObject(Net.text("https://api.open-meteo.com/v1/forecast" + q
                    + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1"));
            JSONObject cur = root.getJSONObject("current");
            JSONObject daily = root.optJSONObject("daily");
            // the warnings' readings, asked apart: when this fails, the weather still shows, without them
            double gust = Double.NaN, vis = Double.NaN;
            int worst = -1;
            try {
                JSONObject next = new JSONObject(Net.text("https://api.open-meteo.com/v1/forecast" + q
                        + "&current=wind_gusts_10m,visibility&hourly=weather_code,wind_gusts_10m,visibility&forecast_hours=3"));
                JSONObject soon = next.optJSONObject("current");
                if (soon != null) { gust = soon.optDouble("wind_gusts_10m"); vis = soon.optDouble("visibility"); }
                JSONObject hourly = next.optJSONObject("hourly");
                if (hourly != null) {
                    JSONArray codes = hourly.optJSONArray("weather_code"), gusts = hourly.optJSONArray("wind_gusts_10m"),
                            viss = hourly.optJSONArray("visibility");
                    for (int i = 0; i < 3; i++) {
                        if (codes != null && i < codes.length()) worst = Math.max(worst, codes.optInt(i, -1));
                        double g = gusts == null ? Double.NaN : gusts.optDouble(i);
                        if (!Double.isNaN(g) && (Double.isNaN(gust) || g > gust)) gust = g;
                        double v = viss == null ? Double.NaN : viss.optDouble(i);
                        if (!Double.isNaN(v) && (Double.isNaN(vis) || v < vis)) vis = v;
                    }
                }
            } catch (Exception ignored) {
                // no warnings this time
            }
            double high = daily == null ? Double.NaN : daily.getJSONArray("temperature_2m_max").optDouble(0);
            double low = daily == null ? Double.NaN : daily.getJSONArray("temperature_2m_min").optDouble(0);
            double dust = 0;
            try {
                JSONObject air = new JSONObject(Net.text("https://air-quality-api.open-meteo.com/v1/air-quality" + q + "&current=pm10,dust"))
                        .getJSONObject("current");
                dust = Math.max(air.optDouble("pm10", 0), air.optDouble("dust", 0));
            } catch (Exception ignored) {
                // no air reading: the sky's word stands
            }
            Weather w = new Weather(cur.getDouble("temperature_2m"), high, low, dust, gust, vis, cur.getInt("weather_code"), worst, place,
                    System.currentTimeMillis());
            prefs(c).edit().putFloat("temp", (float) w.temp).putFloat("high", (float) w.high).putFloat("low", (float) w.low)
                    .putFloat("dust", (float) w.dust).putFloat("gust", (float) w.gust).putFloat("vis", (float) w.visibility)
                    .putInt("code", w.code).putInt("worst", w.worst).putString("place", w.place).putLong("at", w.at).apply();
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    /** the phone's newest known place from any provider, without asking for a new fix (no GPS woken); null without the permission */
    /** the phone's last known location (no new fix), null without the permission */
    static Location lastKnown(Context c) {
        if (c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (SecurityException e) {
            return null;
        }
    }

    /** «القطيف» for a place, in Arabic; null when the phone can't name it */
    @SuppressWarnings("deprecation")
    private static String placeName(Context c, double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return null;
            List<Address> a = new Geocoder(c, new Locale("ar")).getFromLocation(lat, lon, 1);
            if (a == null || a.isEmpty()) return null;
            Address ad = a.get(0);
            String n = ad.getLocality() != null ? ad.getLocality() : ad.getSubAdminArea() != null ? ad.getSubAdminArea() : ad.getAdminArea();
            return n == null || n.trim().isEmpty() ? null : n.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("weather", Context.MODE_PRIVATE);
    }
}
