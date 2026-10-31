package sa.talla;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The chosen team's next match (Al-Hilal unless another is picked in the settings), from TheSportsDB (its free key: every
 * competition, not one league), with the clubs' names in Arabic (Teams; a club not known keeps TheSportsDB's name). Asked every
 * 3 hours; the answer is kept on the phone, with the team it was asked for.
 */
final class Match {
    static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final String KEY = "123";

    final String home, away, league, homeBadge, awayBadge;
    final long at;

    private Match(String home, String away, String league, String homeBadge, String awayBadge, long at) {
        this.home = home; this.away = away; this.league = league; this.homeBadge = homeBadge; this.awayBadge = awayBadge; this.at = at;
    }

    /** the kept match of this team, or null when there is none or it has been played (two hours after kick-off) */
    static Match kept(Context c, String team, long now) {
        SharedPreferences sp = prefs(c);
        long at = sp.getLong("at", 0);
        if (at == 0 || now > at + 2 * 3600_000L || !team.equals(sp.getString("team", ""))) return null;
        return new Match(sp.getString("home", ""), sp.getString("away", ""), sp.getString("league", ""),
                sp.getString("homeBadge", ""), sp.getString("awayBadge", ""), at);
    }

    static boolean due(Context c, String team, long now) {
        SharedPreferences sp = prefs(c);
        if (!team.equals(sp.getString("tried_team", ""))) return true;   // another team was just picked
        long at = sp.getLong("at", 0);
        // after the match is over, ask at once for the one after it
        return now - sp.getLong("tried", 0) >= REFRESH_MS || (at > 0 && now > at + 2 * 3600_000L && now - sp.getLong("tried", 0) > 15 * 60_000L);
    }

    /** Blocking; true when a match was kept. */
    static boolean fetch(Context c, String team) {
        prefs(c).edit().putLong("tried", System.currentTimeMillis()).putString("tried_team", team).apply();
        try {
            JSONArray events = new JSONObject(Net.text("https://www.thesportsdb.com/api/v1/json/" + KEY + "/eventsnext.php?id=" + team))
                    .optJSONArray("events");
            if (events == null) { prefs(c).edit().putLong("at", 0).apply(); return false; }
            long now = System.currentTimeMillis();
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                long at = kickoff(e);
                if (at <= now - 2 * 3600_000L) continue;
                prefs(c).edit()
                        .putString("team", team)
                        .putString("home", Teams.arabic(c, e.optString("strHomeTeam")))
                        .putString("away", Teams.arabic(c, e.optString("strAwayTeam")))
                        .putString("league", league(c, e.optString("strLeague")))
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

    /** a competition's name in Arabic: the leagues of the picker, then the cups and the continents; another keeps TheSportsDB's */
    static String league(Context c, String en) {
        String known = Teams.leagueArabic(c, en);
        if (known != null) return known;
        String l = en.toLowerCase(Locale.ROOT);
        for (String[] k : CUPS) {
            boolean all = true;
            for (String w : k[0].split("\\+")) all &= l.contains(w);
            if (all) return k[1];
        }
        return en;
    }

    /** words that must all be in TheSportsDB's name ("a+b"), the Arabic; the first that fits, so the narrower ones come first */
    private static final String[][] CUPS = {
        {"saudi+pro", "دوري روشن السعودي"}, {"saudi+super cup", "كأس السوبر السعودي"}, {"saudi+king", "كأس الملك"},
        {"king+cup", "كأس الملك"}, {"afc+elite", "دوري أبطال آسيا للنخبة"}, {"afc+champions league two", "دوري أبطال آسيا ٢"},
        {"afc+champions", "دوري أبطال آسيا"}, {"asian cup+qualif", "تصفيات كأس آسيا"}, {"asian cup", "كأس آسيا"},
        {"club world cup", "كأس العالم للأندية"}, {"world cup+qualif", "تصفيات كأس العالم"}, {"world cup", "كأس العالم"},
        {"arab cup", "كأس العرب"}, {"gulf cup", "كأس الخليج"}, {"uefa+champions", "دوري أبطال أوروبا"},
        {"uefa+europa conference", "دوري المؤتمر الأوروبي"}, {"conference league", "دوري المؤتمر الأوروبي"},
        {"europa", "الدوري الأوروبي"}, {"uefa+super cup", "كأس السوبر الأوروبي"}, {"nations league", "دوري الأمم"},
        {"euro+qualif", "تصفيات كأس أوروبا"}, {"european championship", "كأس أوروبا"}, {"fa cup", "كأس الاتحاد الإنجليزي"},
        {"efl cup", "كأس الرابطة الإنجليزية"}, {"league cup", "كأس الرابطة"}, {"community shield", "درع المجتمع"},
        {"copa del rey", "كأس ملك إسبانيا"}, {"supercopa", "كأس السوبر الإسباني"}, {"coppa italia", "كأس إيطاليا"},
        {"supercoppa", "كأس السوبر الإيطالي"}, {"dfb", "كأس ألمانيا"}, {"coupe de france", "كأس فرنسا"},
        {"copa libertadores", "كأس ليبرتادوريس"}, {"caf+champions", "دوري أبطال أفريقيا"}, {"egypt+cup", "كأس مصر"},
        {"friendl", "مباراة ودية"},
    };

    /** a badge at TheSportsDB's small size (the host only, nothing else) */
    private static String badge(String url) {
        return url.startsWith("https://r2.thesportsdb.com/") || url.startsWith("https://www.thesportsdb.com/") ? url + "/small" : "";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("match", Context.MODE_PRIVATE);
    }
}
