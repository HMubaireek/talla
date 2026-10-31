package sa.talla;

import android.location.Location;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The nearest petrol station, from OpenStreetMap (the Overpass API, free, no key): asked only while the fuel is low, around the
 * phone's last known location, within 15 km. The distance is a straight line, so the screen says «نحو».
 */
final class Stations {
    private static final String API = "https://overpass-api.de/api/interpreter?data=";
    private static final int RADIUS = 15_000;

    final String name;
    final float km;
    /** where it was asked from, and when */
    final double fromLat, fromLon;
    final long at;

    private Stations(String name, float km, double fromLat, double fromLon, long at) {
        this.name = name; this.km = km; this.fromLat = fromLat; this.fromLon = fromLon; this.at = at;
    }

    /** asked again after 10 minutes, or once the car is 2 km from where it was asked */
    boolean stale(Location here, long now) {
        if (now - at > 10 * 60_000L) return true;
        if (here == null) return false;
        float[] d = new float[1];
        Location.distanceBetween(fromLat, fromLon, here.getLatitude(), here.getLongitude(), d);
        return d[0] > 2000;
    }

    /** Blocking. The nearest station around `here`, or null (none within 15 km, or offline). */
    static Stations nearest(Location here) {
        if (here == null) return null;
        double lat = here.getLatitude(), lon = here.getLongitude();
        String q = "[out:json][timeout:15];nwr[\"amenity\"=\"fuel\"](around:" + RADIUS + "," + lat + "," + lon + ");out center 60;";
        try {
            JSONArray els = new JSONObject(Net.text(API + Uri.encode(q))).optJSONArray("elements");
            if (els == null) return null;
            String best = null;
            float bestM = Float.MAX_VALUE;
            float[] d = new float[1];
            for (int i = 0; i < els.length(); i++) {
                JSONObject e = els.getJSONObject(i);
                JSONObject c = e.has("lat") ? e : e.optJSONObject("center");
                if (c == null) continue;
                Location.distanceBetween(lat, lon, c.optDouble("lat"), c.optDouble("lon"), d);
                if (d[0] >= bestM) continue;
                bestM = d[0];
                best = name(e.optJSONObject("tags"));
            }
            return best == null ? null : new Stations(best, bestM / 1000f, lat, lon, System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
    }

    /** its name in Arabic when the map has one, else its brand, else plainly «محطة وقود» */
    private static String name(JSONObject tags) {
        if (tags == null) return "محطة وقود";
        for (String k : new String[]{"name:ar", "brand:ar", "name", "brand", "operator"}) {
            String v = tags.optString(k, "").trim();
            if (!v.isEmpty()) return v;
        }
        return "محطة وقود";
    }

    /** the preview's demo */
    static Stations demo() { return new Stations("محطة وقود (مثال)", 2.3f, 0, 0, Long.MAX_VALUE); }
}
