package sa.talla;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the car itself says, through Android Auto (DashScreen listens with the Car App Library's CarInfo, car API 3 and up): the
 * fuel, the range left, whether the car calls it low, the speed, the odometer and the model. Only while Talla is on the car's screen.
 * Every field belongs to the hub's thread. A car that sends nothing leaves them unknown (NaN), and the screen shows nothing of it.
 *
 * What came in last (or why nothing did) is kept in the prefs file "car", so the phone's settings can say what this car sends.
 */
final class CarData {
    /** CarValue's statuses, as the Car App Library numbers them, and ours for a listener the host refused */
    static final int UNKNOWN = 0, SUCCESS = 1, UNIMPLEMENTED = 2, UNAVAILABLE = 3, REFUSED = 100;

    /** at or below this the fuel counts as low when the car gives no range (percent) */
    static final float LOW_PERCENT = 12;
    /** faster than this (m/s, about 5 km/h) the car is moving */
    private static final float MOVING = 1.5f;

    float fuelPercent = Float.NaN, rangeKm = Float.NaN, speed = Float.NaN, odometerKm = Float.NaN;
    boolean carSaysLow;
    long speedAt;
    String model = "";

    private final Context app;
    private long speedSavedAt;

    CarData(Context app) { this.app = app; }

    /** the car left the screen: nothing it said is current any more */
    void clear() {
        fuelPercent = rangeKm = speed = odometerKm = Float.NaN;
        carSaysLow = false;
        speedAt = 0;
    }

    boolean hasFuel() { return !Float.isNaN(fuelPercent) || !Float.isNaN(rangeKm); }

    /** low: the car says so, the range is under the owner's limit, or (no range) the tank is nearly empty */
    boolean low(int limitKm) {
        if (carSaysLow) return true;
        if (!Float.isNaN(rangeKm)) return rangeKm < limitKm;
        return !Float.isNaN(fuelPercent) && fuelPercent <= LOW_PERCENT;
    }

    /** moving now: a speed came in the last 15 seconds and it was more than a walk */
    boolean moving(long now) { return !Float.isNaN(speed) && speed > MOVING && now - speedAt < 15_000; }

    // ---------------------------------------------------------------- from DashScreen's listeners (the hub's thread)

    void energy(int fuelStatus, Float fuel, int rangeStatus, Float rangeMeters, int lowStatus, Boolean low) {
        fuelPercent = fuelStatus == SUCCESS && fuel != null ? fuel : Float.NaN;
        rangeKm = rangeStatus == SUCCESS && rangeMeters != null ? rangeMeters / 1000f : Float.NaN;
        carSaysLow = lowStatus == SUCCESS && low != null && low;
        save("fuel", fuelStatus, Float.isNaN(fuelPercent) ? "" : Math.round(fuelPercent) + "%");
        save("range", rangeStatus, Float.isNaN(rangeKm) ? "" : Math.round(rangeKm) + " km");
        save("low", lowStatus, lowStatus == SUCCESS ? String.valueOf(carSaysLow) : "");
    }

    void speed(int status, Float metersPerSecond, long now) {
        speed = status == SUCCESS && metersPerSecond != null ? metersPerSecond : Float.NaN;
        speedAt = now;
        if (now - speedSavedAt > 60_000) {   // it comes many times a second: kept at most once a minute
            speedSavedAt = now;
            save("speed", status, Float.isNaN(speed) ? "" : Math.round(speed * 3.6f) + " km/h");
        }
    }

    void mileage(int status, Float meters) {
        odometerKm = status == SUCCESS && meters != null ? meters / 1000f : Float.NaN;
        save("odometer", status, Float.isNaN(odometerKm) ? "" : Math.round(odometerKm) + " km");
    }

    void model(int status, String manufacturer, String name, Integer year) {
        StringBuilder b = new StringBuilder();
        if (manufacturer != null) b.append(manufacturer);
        if (name != null) b.append(b.length() > 0 ? " " : "").append(name);
        if (year != null && year > 0) b.append(b.length() > 0 ? " " : "").append(year);
        model = status == SUCCESS ? b.toString().trim() : "";
        save("model", status, model);
    }

    /** a listener the host refused (no permission, or an old Android Auto) */
    void refused(String item, String why) { save(item, REFUSED, why == null ? "" : why); }

    private void save(String item, int status, String value) {
        prefs(app).edit().putInt(item + "_status", status).putString(item + "_value", value)
                .putLong(item + "_at", System.currentTimeMillis()).apply();
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("car", Context.MODE_PRIVATE);
    }

    /** for the settings: what the car sent for one item, in Arabic */
    static String report(Context c, String item) {
        SharedPreferences sp = prefs(c);
        if (!sp.contains(item + "_status")) return "لم يصل بعد";
        int st = sp.getInt(item + "_status", UNKNOWN);
        String v = sp.getString(item + "_value", "");
        switch (st) {
            case SUCCESS: return v.isEmpty() ? "وصل فارغًا" : Texts.digits(v.replace("km/h", "كم/س").replace("km", "كم"), false);
            case UNIMPLEMENTED: return "لا ترسله هذه السيارة";
            case UNAVAILABLE: return "غير متاح الآن";
            case REFUSED: return "رُفض" + (v.isEmpty() ? "" : " (" + v + ")");
            default: return "غير معروف";
        }
    }

    /** a car that answered at all, for the settings' first line */
    static long lastHeard(Context c) {
        SharedPreferences sp = prefs(c);
        long at = 0;
        for (String k : new String[]{"fuel", "range", "low", "speed", "odometer", "model"}) at = Math.max(at, sp.getLong(k + "_at", 0));
        return at;
    }

    /** the preview's «وقود منخفض» demo */
    static CarData demo(Context app) {
        CarData d = new CarData(app);
        d.fuelPercent = 8;
        d.rangeKm = 42;
        d.carSaysLow = true;
        return d;
    }
}
