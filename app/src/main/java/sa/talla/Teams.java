package sa.talla;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The leagues and clubs to pick a team from, and the clubs' Arabic names: assets/teams.json, made by tools/teams.py from Bayti TV's
 * lists (its LEAGUES, its teams.json and its teams-ar.json). Read once; empty when it can't be read, never a crash.
 */
final class Teams {
    static final class Team {
        final String id, en, ar;
        final String[] leagues, alt;
        final boolean translit;
        Team(String id, String en, String ar, String[] leagues, String[] alt, boolean translit) {
            this.id = id; this.en = en; this.ar = ar; this.leagues = leagues; this.alt = alt; this.translit = translit;
        }
        /** the name to show: Arabic, with the English beside a mere transliteration */
        String label() { return ar.isEmpty() ? en : translit ? ar + "  ·  " + en : ar; }
        String name() { return ar.isEmpty() ? en : ar; }
    }

    private static List<String[]> leagues = Collections.emptyList();
    private static List<Team> all;
    private static final Map<String, Team> BY_ID = new HashMap<>();
    /** English name (lower case, hyphens as spaces) to Arabic: the clubs, their other names, then teams-ar.json's */
    private static final Map<String, String> AR = new HashMap<>();

    private Teams() { }

    private static synchronized void load(Context c) {
        if (all != null) return;
        List<Team> v = new ArrayList<>();
        List<String[]> ls = new ArrayList<>();
        try (InputStream in = c.getApplicationContext().getAssets().open("teams.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            JSONObject o = new JSONObject(b.toString("UTF-8"));
            JSONArray la = o.getJSONArray("leagues");
            for (int i = 0; i < la.length(); i++) ls.add(new String[]{la.getJSONArray(i).getString(0), la.getJSONArray(i).getString(1)});
            JSONObject names = o.optJSONObject("names");
            if (names != null) for (java.util.Iterator<String> k = names.keys(); k.hasNext(); ) {
                String en = k.next();
                AR.put(key(en), names.optString(en));
            }
            JSONArray ta = o.getJSONArray("teams");
            for (int i = 0; i < ta.length(); i++) {
                JSONObject t = ta.getJSONObject(i);
                Team team = new Team(t.getString("id"), t.optString("en"), t.optString("ar"), strings(t.optJSONArray("l")),
                        strings(t.optJSONArray("alt")), t.optInt("tr", 0) == 1);
                v.add(team);
                BY_ID.put(team.id, team);
                if (!team.ar.isEmpty()) {
                    AR.put(key(team.en), team.ar);
                    for (String a : team.alt) AR.put(key(a), team.ar);
                }
            }
        } catch (Exception e) {
            v.clear();
            ls.clear();
            BY_ID.clear();
        }
        // a few clubs Al-Hilal meets in Asia, kept from 1.x (not in Bayti TV's lists)
        for (String[] x : ASIA) if (!AR.containsKey(key(x[0]))) AR.put(key(x[0]), x[1]);
        leagues = ls;
        all = v;
    }

    private static String[] strings(JSONArray a) {
        if (a == null) return new String[0];
        String[] out = new String[a.length()];
        for (int i = 0; i < out.length; i++) out[i] = a.optString(i);
        return out;
    }

    private static String key(String en) {
        return en.trim().toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("\\s+", " ");
    }

    /** {English as TheSportsDB spells it, Arabic}; the last one is the national teams */
    static List<String[]> leagues(Context c) { load(c); return leagues; }

    static Team byId(Context c, String id) {
        load(c);
        synchronized (Teams.class) { return BY_ID.get(id); }
    }

    /** the clubs of one league, in Arabic alphabetical order */
    static List<Team> inLeague(Context c, String league) {
        load(c);
        List<Team> out = new ArrayList<>();
        for (Team t : all) for (String l : t.leagues) if (l.equalsIgnoreCase(league)) { out.add(t); break; }
        final Collator ar = Collator.getInstance(new Locale("ar"));
        Collections.sort(out, (a, b) -> ar.compare(a.name(), b.name()));
        return out;
    }

    /** a club's Arabic name from its English one (TheSportsDB's); unknown: the English as it is */
    static String arabic(Context c, String en) {
        load(c);
        String a = AR.get(key(en));
        return a == null || a.isEmpty() ? en.trim() : a;
    }

    /** a league's Arabic name, from the leagues table; null when it isn't one of them */
    static String leagueArabic(Context c, String en) {
        for (String[] l : leagues(c)) if (l[0].equalsIgnoreCase(en.trim())) return l[1];
        return null;
    }

    private static final String[][] ASIA = {
        {"Al-Ain", "العين"}, {"Al-Sadd", "السد"}, {"Al-Duhail", "الدحيل"}, {"Al-Gharafa", "الغرافة"}, {"Al-Rayyan", "الريان"},
        {"Al-Wasl", "الوصل"}, {"Shabab Al-Ahli", "شباب الأهلي"}, {"Sharjah", "الشارقة"}, {"Al-Wahda", "الوحدة الإماراتي"},
        {"Persepolis", "بيرسبوليس"}, {"Esteghlal", "استقلال طهران"}, {"Pakhtakor", "باختاكور"}, {"Nasaf", "ناساف"},
        {"Ulsan HD", "أولسان"}, {"Yokohama F. Marinos", "يوكوهاما"}, {"Kawasaki Frontale", "كاواساكي"}, {"Vissel Kobe", "فيسل كوبي"},
        {"Urawa Red Diamonds", "أوراوا"}, {"Shanghai Port", "شنغهاي بورت"},
    };
}
