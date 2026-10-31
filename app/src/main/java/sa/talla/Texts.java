package sa.talla;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The words on the car's screen, in Bayti TV's Arabic: digits, the two dates, the clock, the greeting's part of the day, the sky,
 * and «بعد ساعتين و١٥ دقيقة».
 */
final class Texts {
    static final int MORNING = 0, DAY = 1, EVENING = 2, NIGHT = 3;

    private Texts() { }

    /** Arabic-Indic digits, or the Latin ones left alone */
    static String digits(String s, boolean latin) {
        if (latin) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append(ch >= '0' && ch <= '9' ? (char) ('٠' + (ch - '0')) : ch);
        }
        return b.toString();
    }

    /** «الأربعاء ٨ جمادى الأولى ١٤٤٨ هـ» from the Umm al-Qura calendar, moved by the owner's days */
    static String hijri(long now, String tz, int adjustDays, boolean latin) {
        try {
            android.icu.util.ULocale loc = new android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura;numbers=" + (latin ? "latn" : "arab"));
            android.icu.util.Calendar cal = android.icu.util.Calendar.getInstance(android.icu.util.TimeZone.getTimeZone(tz), loc);
            cal.setTimeInMillis(now + adjustDays * 86_400_000L);
            android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat("EEEE d MMMM y", loc);
            f.setCalendar(cal);
            String s = f.format(cal.getTime()).replaceAll("\\s*هـ\\s*$", "");
            return s + " هـ";
        } catch (Throwable t) {
            return "";
        }
    }

    /** «٣٠ أكتوبر ٢٠٢٦» */
    static String gregorian(long now, String tz, boolean latin) {
        try {
            android.icu.util.ULocale loc = new android.icu.util.ULocale("ar_SA@calendar=gregorian;numbers=" + (latin ? "latn" : "arab"));
            android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat("d MMMM y", loc);
            f.setTimeZone(android.icu.util.TimeZone.getTimeZone(tz));
            return f.format(new java.util.Date(now)) + " م";
        } catch (Throwable t) {
            return "";
        }
    }

    /** the clock's digits {"٩:٤١", "م"} (12 hours) or {"21:41", ""} */
    static String[] clock(long now, String tz, boolean h24, boolean latin) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US);
        c.setTimeInMillis(now);
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        if (h24) return new String[]{digits(String.format(Locale.US, "%02d:%02d", h, m), latin), ""};
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return new String[]{digits(String.format(Locale.US, "%d:%02d", h12, m), latin), h < 12 ? "ص" : "م"};
    }

    /** "18:40" → «٦:٤٠ م» */
    static String clock12(long at, String tz, boolean h24, boolean latin) {
        String[] c = clock(at, tz, h24, latin);
        return c[1].isEmpty() ? c[0] : c[0] + " " + c[1];
    }

    /** the phrase's part of the day: Fajr to Dhuhr, Dhuhr to Asr, Asr to Isha, then the night until the next Fajr */
    static int part(long now, long fajr, long dhuhr, long asr, long isha) {
        long[] edges = {fajr, dhuhr, asr, isha};
        int[] parts = {NIGHT, MORNING, DAY, EVENING};
        for (int i = 0; i < edges.length; i++) if (now < edges[i]) return parts[i];
        return NIGHT;
    }

    /** a WMO weather code (Open-Meteo's) in a word or two; "" for one with no word */
    static String sky(int code) {
        if (code == 0) return "صحو";
        if (code <= 2) return "غيوم متفرقة";
        if (code == 3) return "غائم";
        if (code == 45 || code == 48) return "ضباب";
        if (code >= 51 && code <= 57) return "رذاذ";
        if (code == 66 || code == 67) return "أمطار متجمدة";
        if (code == 65 || code == 82) return "أمطار غزيرة";
        if ((code >= 61 && code <= 63) || code == 80 || code == 81) return "ممطر";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "ثلج";
        if (code == 95) return "عاصفة رعدية";
        if (code == 96 || code == 99) return "عاصفة رعدية مع برد";
        return "";
    }

    /** the sky's word, unless the air is dusty: PM10 or dust (µg/m³) of 150 and up, 400 and up for heavy dust */
    static String skyWord(int code, double dust) {
        return dust >= 400 ? "غبار كثيف" : dust >= 150 ? "غبار" : sky(code);
    }

    /** «بعد ساعتين وعشر دقائق» style: the time left to the minute */
    static String remaining(long millis, boolean latin) {
        int total = (int) Math.max(0, Math.round(millis / 60_000.0));
        if (total == 0) return "الآن";
        String h = count(total / 60, "ساعة", "ساعتين", "ساعات", latin), m = count(total % 60, "دقيقة", "دقيقتين", "دقائق", latin);
        return "بعد " + (h.isEmpty() ? m : m.isEmpty() ? h : h + " و" + m);
    }

    /** Arabic counting: 1 the noun alone, 2 its dual, 3 to 10 the plural, 11 and up the singular again; 0 nothing */
    private static String count(int n, String one, String two, String few, boolean latin) {
        switch (n) {
            case 0: return "";
            case 1: return one;
            case 2: return two;
            default: return digits(Integer.toString(n), latin) + " " + (n <= 10 ? few : one);
        }
    }

    /** «بعد يومين», «غدًا», «اليوم» for a match; the time of day goes beside it */
    static String dayWord(long at, long now, String tz) {
        Calendar a = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US), b = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US);
        a.setTimeInMillis(at);
        b.setTimeInMillis(now);
        int days = 0;
        while (days < 60 && (a.get(Calendar.YEAR) != b.get(Calendar.YEAR) || a.get(Calendar.DAY_OF_YEAR) != b.get(Calendar.DAY_OF_YEAR))) {
            b.add(Calendar.DAY_OF_MONTH, 1);
            days++;
        }
        if (days == 0) return "اليوم";
        if (days == 1) return "غدًا";
        return WEEKDAYS[a.get(Calendar.DAY_OF_WEEK) - 1];
    }

    static final String[] WEEKDAYS = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
}
