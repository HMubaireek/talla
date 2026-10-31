package sa.talla;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Prayer times from the sun's position (the usual low-precision solar formulas: declination and equation of time, then the hour
 * angle at which the sun reaches each prayer's altitude). Nothing of Android here, so a plain JVM can check it against Aladhan.
 */
final class PrayerCalc {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    /** A calculation method: the sun's depression for Fajr, Maghrib and Isha (or Isha as minutes after Maghrib). */
    static final class Method {
        final String key;
        final double fajr, maghrib, isha;
        /** Isha this many minutes after Maghrib (and in Ramadan) instead of at an angle; 0 = by its angle */
        final int ishaAfter, ishaAfterRamadan;

        Method(String key, double fajr, double maghrib, double isha, int ishaAfter, int ishaAfterRamadan) {
            this.key = key; this.fajr = fajr; this.maghrib = maghrib; this.isha = isha;
            this.ishaAfter = ishaAfter; this.ishaAfterRamadan = ishaAfterRamadan;
        }

        static final double SUNSET = 0.833;   // the sun's upper edge on the horizon, refraction included
        /** the angles each method publishes (Aladhan's table) */
        static final Method[] ALL = {
            new Method("jafari", 16, 4, 14, 0, 0),
            new Method("tehran", 17.7, 4.5, 14, 0, 0),
            new Method("umm_alqura", 18.5, SUNSET, 0, 90, 120),
            new Method("gulf", 19.5, SUNSET, 0, 90, 0),
            new Method("kuwait", 18, SUNSET, 17.5, 0, 0),
            new Method("qatar", 18, SUNSET, 0, 90, 0),
            new Method("dubai", 18.2, SUNSET, 18.2, 0, 0),
            new Method("mwl", 18, SUNSET, 17, 0, 0),
            new Method("egypt", 19.5, SUNSET, 17.5, 0, 0),
            new Method("karachi", 18, SUNSET, 18, 0, 0),
            new Method("isna", 15, SUNSET, 15, 0, 0),
        };

        static Method of(String key) {
            for (Method m : ALL) if (m.key.equals(key)) return m;
            return ALL[0];
        }
    }

    private PrayerCalc() { }

    /**
     * The six moments (epoch millis, to the minute) of the local day y-mo-d at lat/lon in zone tz. Isha is always after Maghrib:
     * past midnight it is the next day's moment.
     */
    static long[] times(int y, int mo, int d, double lat, double lon, String tz, boolean ramadan, Method m) {
        Calendar noon = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US);
        noon.clear();
        noon.set(y, mo - 1, d, 12, 0, 0);
        long localNoon = noon.getTimeInMillis();
        // days since J2000.0 at the local noon; every event is solved near its own time, starting from there
        double base = localNoon / 86_400_000.0 - 10_957.5;

        double transit = transit(base, lon);
        long[] at = new long[6];
        at[DHUHR] = moment(transit);
        at[FAJR] = moment(transit - hours(base, transit - 6.0 / 24, lat, lon, -m.fajr));
        at[SUNRISE] = moment(transit - hours(base, transit - 6.0 / 24, lat, lon, -Method.SUNSET));
        at[ASR] = moment(transit + hours(base, transit + 3.5 / 24, lat, lon, Double.NaN));
        at[MAGHRIB] = moment(transit + hours(base, transit + 6.0 / 24, lat, lon, -m.maghrib));
        if (m.ishaAfter > 0) {
            at[ISHA] = at[MAGHRIB] + (ramadan && m.ishaAfterRamadan > 0 ? m.ishaAfterRamadan : m.ishaAfter) * 60_000L;
        } else {
            long isha = moment(transit + hours(base, transit + 7.5 / 24, lat, lon, -m.isha));
            while (isha <= at[MAGHRIB]) isha += 86_400_000L;
            at[ISHA] = isha;
        }
        return at;
    }

    /** solar noon, in days since J2000.0 (UT) */
    private static double transit(double base, double lon) {
        double t = base;
        for (int i = 0; i < 3; i++) {
            double[] s = sun(t);
            // the sun crosses the meridian at 12:00 local mean time, less the equation of time
            double ut = 12 - lon / 15 - s[1];
            t = Math.floor(base + 0.5) - 0.5 + ut / 24;   // that date's 0h UT plus the hours
            if (t - base > 0.5) t -= 1;
            if (base - t > 0.5) t += 1;
        }
        return t;
    }

    /**
     * Days between solar noon and the moment the sun is at `altitude` degrees (NaN: Asr's, shadow = its length at noon + 1),
     * solved with the sun's place at `near`. 0 when it never gets there (very high latitudes).
     */
    private static double hours(double base, double near, double lat, double lon, double altitude) {
        double t = near, h = 0;
        for (int i = 0; i < 3; i++) {
            double decl = sun(t)[0];
            double alt = Double.isNaN(altitude) ? Math.toDegrees(Math.atan(1 / (1 + Math.tan(Math.toRadians(Math.abs(lat - decl)))))) : altitude;
            double cos = (Math.sin(Math.toRadians(alt)) - Math.sin(Math.toRadians(lat)) * Math.sin(Math.toRadians(decl)))
                    / (Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(decl)));
            if (cos < -1 || cos > 1) return 0;
            h = Math.toDegrees(Math.acos(cos)) / 360;   // degrees of hour angle to days
            t = transit(base, lon) + (near < transit(base, lon) ? -h : h);
        }
        return h;
    }

    /** {declination in degrees, equation of time in hours} at t days since J2000.0 */
    private static double[] sun(double t) {
        double g = Math.toRadians(norm(357.529 + 0.98560028 * t));
        double q = norm(280.459 + 0.98564736 * t);
        double l = Math.toRadians(norm(q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g)));
        double e = Math.toRadians(23.439 - 0.00000036 * t);
        double ra = norm(Math.toDegrees(Math.atan2(Math.cos(e) * Math.sin(l), Math.cos(l)))) / 15;
        double eqt = q / 15 - ra;
        eqt -= 24 * Math.round(eqt / 24);
        return new double[]{Math.toDegrees(Math.asin(Math.sin(e) * Math.sin(l))), eqt};
    }

    /** days since J2000.0 to epoch millis, rounded to the minute */
    private static long moment(double t) {
        long ms = Math.round((t + 10_957.5) * 86_400_000.0);
        return Math.round(ms / 60_000.0) * 60_000L;
    }

    private static double norm(double deg) {
        double v = deg % 360;
        return v < 0 ? v + 360 : v;
    }
}
