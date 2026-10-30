package sa.bayti.auto;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Al-Hilal's next match, from TheSportsDB (its free key: every competition, not one league), with the clubs' names in Arabic
 * (CLUBS; a club not in it keeps TheSportsDB's name). Asked every 3 hours; the answer is kept on the phone.
 */
final class Hilal {
    static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final String KEY = "123";

    final String home, away, league, homeBadge, awayBadge;
    final long at;

    private Hilal(String home, String away, String league, String homeBadge, String awayBadge, long at) {
        this.home = home; this.away = away; this.league = league; this.homeBadge = homeBadge; this.awayBadge = awayBadge; this.at = at;
    }

    /** the kept match, or null when there is none or it has been played (two hours after kick-off) */
    static Hilal kept(Context c, long now) {
        SharedPreferences sp = prefs(c);
        long at = sp.getLong("at", 0);
        if (at == 0 || now > at + 2 * 3600_000L) return null;
        return new Hilal(sp.getString("home", ""), sp.getString("away", ""), sp.getString("league", ""),
                sp.getString("homeBadge", ""), sp.getString("awayBadge", ""), at);
    }

    static boolean due(Context c, long now) {
        SharedPreferences sp = prefs(c);
        long at = sp.getLong("at", 0);
        // after the match is over, ask at once for the one after it
        return now - sp.getLong("tried", 0) >= REFRESH_MS || (at > 0 && now > at + 2 * 3600_000L && now - sp.getLong("tried", 0) > 15 * 60_000L);
    }

    /** Blocking; true when a match was kept. */
    static boolean fetch(Context c) {
        prefs(c).edit().putLong("tried", System.currentTimeMillis()).apply();
        try {
            JSONArray events = new JSONObject(Net.text("https://www.thesportsdb.com/api/v1/json/" + KEY + "/eventsnext.php?id=" + Settings.HILAL_ID))
                    .optJSONArray("events");
            if (events == null) return false;
            long now = System.currentTimeMillis();
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                long at = kickoff(e);
                if (at <= now - 2 * 3600_000L) continue;
                prefs(c).edit()
                        .putString("home", arabic(e.optString("strHomeTeam")))
                        .putString("away", arabic(e.optString("strAwayTeam")))
                        .putString("league", league(e.optString("strLeague")))
                        .putString("homeBadge", badge(e.optString("strHomeTeamBadge")))
                        .putString("awayBadge", badge(e.optString("strAwayTeamBadge")))
                        .putLong("at", at).apply();
                return true;
            }
        } catch (Exception ignored) {
            // offline: the kept match stays
        }
        return false;
    }

    /** strTimestamp is UTC ("2026-10-31T18:00:00"); a match with only a date counts from its UTC midnight */
    private static long kickoff(JSONObject e) throws Exception {
        String ts = e.optString("strTimestamp");
        SimpleDateFormat f;
        if (ts.length() >= 19) {
            f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            ts = ts.substring(0, 19);
        } else {
            f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            ts = e.optString("dateEvent");
        }
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.parse(ts).getTime();
    }

    /** the competitions Al-Hilal plays, in Arabic; another one keeps TheSportsDB's name */
    private static String league(String en) {
        String l = en.toLowerCase(Locale.ROOT);
        if (l.contains("saudi") && l.contains("pro")) return "دوري روشن السعودي";
        if (l.contains("king") && l.contains("cup")) return "كأس الملك";
        if (l.contains("super cup")) return "كأس السوبر السعودي";
        if (l.contains("afc") && l.contains("elite")) return "دوري أبطال آسيا للنخبة";
        if (l.contains("afc") && l.contains("champions")) return "دوري أبطال آسيا";
        if (l.contains("club world cup")) return "كأس العالم للأندية";
        if (l.contains("friendl")) return "مباراة ودية";
        return en;
    }

    /** a badge at TheSportsDB's small size (the host only, nothing else) */
    private static String badge(String url) {
        return url.startsWith("https://r2.thesportsdb.com/") || url.startsWith("https://www.thesportsdb.com/") ? url + "/small" : "";
    }

    /** TheSportsDB's names of the Saudi league's clubs (and a few Al-Hilal meets often), in Arabic */
    private static final String[][] CLUBS = {
        {"Al-Hilal", "الهلال"}, {"Al-Nassr", "النصر"}, {"Al-Ittihad", "الاتحاد"}, {"Al-Ahli", "الأهلي"}, {"Al-Shabab", "الشباب"},
        {"Al-Ettifaq", "الاتفاق"}, {"Al-Taawoun", "التعاون"}, {"Al-Fateh", "الفتح"}, {"Al-Fayha", "الفيحاء"}, {"Al-Khaleej", "الخليج"},
        {"Al-Riyadh", "الرياض"}, {"Al-Qadsiah", "القادسية"}, {"Al-Kholood", "الخلود"}, {"Al-Hazem", "الحزم"}, {"Neom", "نيوم"},
        {"Al-Diriyah", "الدرعية"}, {"Al-Faisaly", "الفيصلي"}, {"Abha", "أبها"}, {"Al-Okhdood", "الأخدود"}, {"Al-Wehda", "الوحدة"},
        {"Damac", "ضمك"}, {"Al-Raed", "الرائد"}, {"Al-Orobah", "العروبة"}, {"Al-Najma", "النجمة"}, {"Al-Tai", "الطائي"},
        {"Al-Batin", "الباطن"}, {"Al-Adalah", "العدالة"}, {"Al-Jabalain", "الجبلين"}, {"Al-Ain", "العين"}, {"Al-Sadd", "السد"},
        {"Al-Duhail", "الدحيل"}, {"Al-Gharafa", "الغرافة"}, {"Al-Rayyan", "الريان"}, {"Al-Wasl", "الوصل"}, {"Shabab Al-Ahli", "شباب الأهلي"},
        {"Sharjah", "الشارقة"}, {"Al-Wahda", "الوحدة الإماراتي"}, {"Persepolis", "بيرسبوليس"}, {"Esteghlal", "استقلال طهران"},
        {"Pakhtakor", "باختاكور"}, {"Nasaf", "ناساف"}, {"Ulsan HD", "أولسان"}, {"Yokohama F. Marinos", "يوكوهاما"},
        {"Kawasaki Frontale", "كاواساكي"}, {"Vissel Kobe", "فيسل كوبي"}, {"Urawa Red Diamonds", "أوراوا"}, {"Shanghai Port", "شنغهاي بورت"},
    };

    static String arabic(String en) {
        String key = en.trim();
        for (String[] c : CLUBS) if (c[0].equalsIgnoreCase(key) || c[0].replace("-", " ").equalsIgnoreCase(key)) return c[1];
        return key;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("hilal", Context.MODE_PRIVATE);
    }
}
