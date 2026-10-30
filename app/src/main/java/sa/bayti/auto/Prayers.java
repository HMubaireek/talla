package sa.bayti.auto;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Today's prayer times and the next one, computed on the phone (PrayerCalc, no network) by the method in the settings (Jafari unless
 * changed) for the city in the settings (Al-Mallaha unless changed).
 */
final class Prayers {
    static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] FIVE = {PrayerCalc.FAJR, PrayerCalc.DHUHR, PrayerCalc.ASR, PrayerCalc.MAGHRIB, PrayerCalc.ISHA};

    static final class Next {
        final String name;
        final long at;
        Next(String name, long at) { this.name = name; this.at = at; }
    }

    private Prayers() { }

    /** the six moments of that day in the city (Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha) */
    static long[] day(Settings s, long when) {
        Calendar local = Calendar.getInstance(TimeZone.getTimeZone(s.tz()), Locale.US);
        local.setTimeInMillis(when);
        boolean ramadan = hijriMonth(when, s.tz()) == 9;
        return PrayerCalc.times(local.get(Calendar.YEAR), local.get(Calendar.MONTH) + 1, local.get(Calendar.DAY_OF_MONTH),
                s.lat(), s.lon(), s.tz(), ramadan, s.prayerMethod());
    }

    /** the next of the five, looking at tomorrow when today's Isha has passed; Asr and Isha skipped when the settings hide them */
    static Next next(Settings s, long now) {
        for (int d = 0; d < 3; d++) {
            long[] t = day(s, now + d * 86_400_000L);
            for (int i : FIVE) {
                if ((i == PrayerCalc.ASR && s.hideAsr()) || (i == PrayerCalc.ISHA && s.hideIsha())) continue;
                if (t[i] > now + 30_000) return new Next(NAMES[i], t[i]);
            }
        }
        return null;
    }

    /** the phrase's part of the day, by today's prayer times */
    static int part(Settings s, long now) {
        long[] t = day(s, now);
        return Texts.part(now, t[PrayerCalc.FAJR], t[PrayerCalc.DHUHR], t[PrayerCalc.ASR], t[PrayerCalc.ISHA]);
    }

    private static int hijriMonth(long millis, String tz) {
        try {
            android.icu.util.Calendar cal = android.icu.util.Calendar.getInstance(android.icu.util.TimeZone.getTimeZone(tz),
                    new android.icu.util.ULocale("en@calendar=islamic-umalqura"));
            cal.setTimeInMillis(millis);
            return cal.get(android.icu.util.Calendar.MONTH) + 1;
        } catch (Throwable t) {
            return 0;
        }
    }
}
