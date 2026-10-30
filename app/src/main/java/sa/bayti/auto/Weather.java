package sa.bayti.auto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * The weather where the car is (the phone's last known place) or in the settings' city: Open-Meteo's temperature, sky and the day's
 * high and low, and its air-quality dust as Bayti TV reads it («غبار»). Asked at most every 30 minutes; the last answer is kept on the
 * phone, and one older than 3 hours is not shown.
 */
final class Weather {
    static final long REFRESH_MS = 30 * 60 * 1000L;
    private static final long STALE_MS = 3 * 60 * 60 * 1000L;

    final double temp, high, low, dust;
    final int code;
    final String place;
    final long at;

    private Weather(double temp, double high, double low, double dust, int code, String place, long at) {
        this.temp = temp; this.high = high; this.low = low; this.dust = dust; this.code = code; this.place = place; this.at = at;
    }

    boolean fresh(long now) { return now - at < STALE_MS; }

    /** the last answer kept, or null */
    static Weather kept(Context c) {
        SharedPreferences sp = prefs(c);
        if (!sp.contains("code")) return null;
        return new Weather(sp.getFloat("temp", 0), sp.getFloat("high", 0), sp.getFloat("low", 0), sp.getFloat("dust", 0),
                sp.getInt("code", -1), sp.getString("place", ""), sp.getLong("at", 0));
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
            Weather w = new Weather(cur.getDouble("temperature_2m"), high, low, dust, cur.getInt("weather_code"), place, System.currentTimeMillis());
            prefs(c).edit().putFloat("temp", (float) w.temp).putFloat("high", (float) w.high).putFloat("low", (float) w.low)
                    .putFloat("dust", (float) w.dust).putInt("code", w.code).putString("place", w.place).putLong("at", w.at).apply();
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    /** the phone's newest known place from any provider, without asking for a new fix (no GPS woken); null without the permission */
    private static Location lastKnown(Context c) {
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
