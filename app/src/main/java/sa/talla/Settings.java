package sa.talla;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the phone's settings screen sets and the car's screen reads, in one SharedPreferences file. The car reads it again on
 * every change (Hub listens), so a switch flipped on the phone shows on the car at once.
 */
final class Settings {
    static final String FILE = "talla";

    /** the items of the car's screen: key, Arabic label; each one on unless switched off */
    static final String[][] ITEMS = {
        {"clock", "الساعة"},
        {"hijri", "التاريخ الهجري"},
        {"greg", "التاريخ الميلادي"},
        {"phrase", "العبارة"},
        {"weather", "الطقس"},
        {"warnings", "تنبيهات الطقس (غبار، رياح، مطر، ضباب، حر)"},
        {"fuel", "تنبيه الوقود المنخفض (من السيارة)"},
        {"prayer", "الصلاة القادمة"},
        {"match", "مباراة فريقك القادمة"},
        {"carousel", "العرض المتحرك"},
        {"background", "صورة الخلفية"},
    };

    /** «التحية»: one phrase per part of the day {key, when, the default}, as Bayti TV's */
    static final String[][] GREET_PARTS = {
        {"morning", "صباحًا · من الفجر إلى الظهر", "صباح الخير"},
        {"day", "نهارًا · من الظهر إلى العصر", "طاب يومك"},
        {"evening", "مساءً · من العصر إلى العشاء", "مساء الخير"},
        {"night", "ليلًا · بعد العشاء", "تصبح على خير"},
    };
    static final int GREET_CHARS = 40;

    /** the same table as Bayti TV's Prefs.PRAYER_METHODS (PrayerCalc.Method holds the angles) */
    static final String[][] PRAYER_METHODS = {
        {"jafari", "الشيعة الاثنا عشرية (قم)"}, {"tehran", "معهد الجيوفيزياء (طهران)"}, {"umm_alqura", "أم القرى (السعودية)"},
        {"gulf", "منطقة الخليج"}, {"kuwait", "الكويت"}, {"qatar", "قطر"}, {"dubai", "دبي"},
        {"mwl", "رابطة العالم الإسلامي"}, {"egypt", "الهيئة المصرية العامة للمساحة"},
        {"karachi", "جامعة العلوم الإسلامية (كراتشي)"}, {"isna", "أمريكا الشمالية (ISNA)"},
    };

    /** «مصادر الصور»: Bayti TV's sources (PhotoSources) and the owner's own folder on the phone */
    static final String OWN = "own";
    static final String[][] SOURCES = {
        {"saudi", "صور السعودية"},
        {"nature", "طبيعة ومناظر (ويكيميديا)"},
        {"mountains", "جبال (ويكيميديا)"},
        {"sunsets", "غروب وشروق (ويكيميديا)"},
        {"space", "الفضاء والفلك (ويكيميديا)"},
        {"potd", "صورة اليوم (ويكيميديا)"},
        {"bing", "صورة اليوم (Bing)"},
        {OWN, "صوري (مجلد في الهاتف)"},
    };

    /** seconds: how long a photo stays, background and carousel */
    static final int[] BG_SECONDS = {60, 120, 300, 600, 900, 1800, 3600};
    static final int[] CAROUSEL_SECONDS = {10, 15, 20, 30, 60, 120, 300};

    /** the owner's village: Al-Mallaha, Al-Qatif (OpenStreetMap's centre of it) */
    static final String HOME_CITY = "الملاحة، القطيف";
    static final double HOME_LAT = 26.5132, HOME_LON = 49.9988;
    static final String HOME_TZ = "Asia/Riyadh";

    /** «فريقك»: TheSportsDB's Al-Hilal until another team is picked */
    static final String DEFAULT_TEAM = "136013", DEFAULT_TEAM_NAME = "الهلال", DEFAULT_LEAGUE = "Saudi-Arabian Pro League";

    private final SharedPreferences sp;

    Settings(Context c) { sp = prefs(c); }

    static SharedPreferences prefs(Context c) {
        SharedPreferences sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        migrate(sp);
        return sp;
    }

    /** 1.x's keys, renamed in 2.0: «show_hilal» became «show_match» (the team is now chosen) */
    private static void migrate(SharedPreferences sp) {
        if (!sp.contains("show_hilal")) return;
        SharedPreferences.Editor e = sp.edit();
        if (!sp.contains("show_match")) e.putBoolean("show_match", sp.getBoolean("show_hilal", true));
        e.remove("show_hilal").apply();
    }

    boolean shows(String item) { return sp.getBoolean("show_" + item, true); }
    void setShows(String item, boolean on) { sp.edit().putBoolean("show_" + item, on).apply(); }

    String greetPhrase(int part) { return sp.getString("greet_" + GREET_PARTS[part][0], "").trim(); }
    void setGreetPhrase(int part, String v) {
        String s = v == null ? "" : v.replaceAll("[\\r\\n]+", " ").trim();
        if (s.length() > GREET_CHARS) s = s.substring(0, GREET_CHARS).trim();
        sp.edit().putString("greet_" + GREET_PARTS[part][0], s).apply();
    }
    String greetShown(int part) { String s = greetPhrase(part); return s.isEmpty() ? GREET_PARTS[part][2] : s; }

    String method() { return sp.getString("method", "jafari"); }
    void setMethod(String v) { sp.edit().putString("method", v).apply(); }
    PrayerCalc.Method prayerMethod() { return PrayerCalc.Method.of(method()); }

    /** «إخفاء العصر والعشاء»: not counted down to (prayed with Dhuhr and Maghrib); their times still divide the day */
    boolean hideAsr() { return sp.getBoolean("hide_asr", false); }
    boolean hideIsha() { return sp.getBoolean("hide_isha", false); }
    void setHideAsr(boolean v) { sp.edit().putBoolean("hide_asr", v).apply(); }
    void setHideIsha(boolean v) { sp.edit().putBoolean("hide_isha", v).apply(); }

    /** the next prayer shows only in the hour before it (on unless switched off); off: always */
    boolean prayerSoonOnly() { return sp.getBoolean("prayer_soon", true); }
    void setPrayerSoonOnly(boolean v) { sp.edit().putBoolean("prayer_soon", v).apply(); }

    String city() { return sp.getString("city", HOME_CITY); }
    double lat() { return number(sp.getString("lat", null), HOME_LAT); }
    double lon() { return number(sp.getString("lon", null), HOME_LON); }
    String tz() { return sp.getString("tz", HOME_TZ); }
    void setPlace(String city, String lat, String lon) {
        sp.edit().putString("city", city.trim()).putString("lat", lat.trim()).putString("lon", lon.trim()).apply();
    }

    /** the weather of where the phone is (its last known location), the city's otherwise */
    boolean weatherHere() { return sp.getBoolean("weather_here", true); }
    void setWeatherHere(boolean v) { sp.edit().putBoolean("weather_here", v).apply(); }

    /** «هندية ١٢٣» (as Bayti TV) or «عربية 123» */
    boolean latinDigits() { return "latin".equals(sp.getString("digits", "hindi")); }
    void setLatinDigits(boolean v) { sp.edit().putString("digits", v ? "latin" : "hindi").apply(); }

    boolean clock24() { return sp.getBoolean("clock24", false); }
    void setClock24(boolean v) { sp.edit().putBoolean("clock24", v).apply(); }

    /** days added to the Umm al-Qura date, for a month that began a day earlier or later by sighting */
    int hijriAdjust() { return sp.getInt("hijri_adj", 0); }
    void setHijriAdjust(int v) { sp.edit().putInt("hijri_adj", Math.max(-2, Math.min(2, v))).apply(); }

    /** the photos' slow settle and push-in; off: they only cross-fade */
    boolean motion() { return sp.getBoolean("motion", true); }
    void setMotion(boolean v) { sp.edit().putBoolean("motion", v).apply(); }

    /** «تخفيف الإضاءة»: auto (while the car is in its night mode), always on, or off; and how much darker (percent) */
    static final String[][] NIGHT_MODES = {{"auto", "تلقائي: حين تنتقل السيارة إلى وضعها الليلي"}, {"on", "دائمًا"}, {"off", "أبدًا"}};
    static final String[][] NIGHT_DIM = {{"25", "خفيف"}, {"40", "متوسط"}, {"55", "قوي"}};
    String nightMode() { return sp.getString("night_mode", "auto"); }
    void setNightMode(String v) { sp.edit().putString("night_mode", v).apply(); }
    int nightDim() { return sp.getInt("night_dim", 40); }
    void setNightDim(int v) { sp.edit().putInt("night_dim", Math.max(0, Math.min(70, v))).apply(); }

    /** the dimming now (0 to 1), carNight being the car's night mode */
    float dim(boolean carNight) {
        String m = nightMode();
        return "off".equals(m) || ("auto".equals(m) && !carNight) ? 0 : nightDim() / 100f;
    }

    /** «فريقك»: TheSportsDB's id of the team whose next match shows, its Arabic name, and the league it was picked from */
    String team() { return sp.getString("team_id", DEFAULT_TEAM); }
    String teamName() { return sp.getString("team_name", DEFAULT_TEAM_NAME); }
    String teamLeague() { return sp.getString("team_league", DEFAULT_LEAGUE); }
    void setTeam(String id, String name, String league) {
        sp.edit().putString("team_id", id).putString("team_name", name).putString("team_league", league).apply();
    }

    /** «بيانات السيارة» (CarData): warn when the range is under this many km; the nearest station with it; the fuel always */
    static final String[][] FUEL_LIMITS = {{"50", "٥٠ كم"}, {"80", "٨٠ كم"}, {"120", "١٢٠ كم"}, {"200", "٢٠٠ كم"}};
    int fuelLimitKm() { return sp.getInt("fuel_limit", 80); }
    void setFuelLimitKm(int v) { sp.edit().putInt("fuel_limit", v).apply(); }
    boolean fuelStation() { return sp.getBoolean("fuel_station", true); }
    void setFuelStation(boolean v) { sp.edit().putBoolean("fuel_station", v).apply(); }
    boolean fuelAlways() { return sp.getBoolean("fuel_always", false); }
    void setFuelAlways(boolean v) { sp.edit().putBoolean("fuel_always", v).apply(); }
    /** while the car moves: no slow photo motion, and the photos wait until it stops */
    boolean calmMoving() { return sp.getBoolean("calm_moving", true); }
    void setCalmMoving(boolean v) { sp.edit().putBoolean("calm_moving", v).apply(); }

    /** «بطاقة الوسائط» (MediaService), off unless switched on */
    boolean mediaCard() { return sp.getBoolean("media_card", false); }
    void setMediaCard(boolean v) { sp.edit().putBoolean("media_card", v).apply(); }

    List<String> bgSources() { return list(sp.getString("bg_sources", "saudi,mountains,sunsets")); }
    void setBgSources(List<String> v) { sp.edit().putString("bg_sources", join(v)).apply(); }
    List<String> carouselSources() { return list(sp.getString("carousel_sources", "own,potd,bing")); }
    void setCarouselSources(List<String> v) { sp.edit().putString("carousel_sources", join(v)).apply(); }

    int bgSeconds() { return sp.getInt("bg_seconds", 600); }
    void setBgSeconds(int v) { sp.edit().putInt("bg_seconds", v).apply(); }
    int carouselSeconds() { return sp.getInt("carousel_seconds", 20); }
    void setCarouselSeconds(int v) { sp.edit().putInt("carousel_seconds", v).apply(); }

    /** the folder of the owner's photos (a tree uri the phone's file picker gave, with a kept read permission), "" for none */
    String ownFolder() { return sp.getString("own_folder", ""); }
    void setOwnFolder(String uri, String name) { sp.edit().putString("own_folder", uri).putString("own_folder_name", name).apply(); }
    String ownFolderName() { return sp.getString("own_folder_name", ""); }

    static String label(String[][] table, String key) {
        for (String[] r : table) if (r[0].equals(key)) return r[1];
        return key;
    }

    private static List<String> list(String saved) {
        List<String> out = new ArrayList<>();
        for (String k : saved.split(",")) {
            String key = k.trim();
            boolean known = false;
            for (String[] s : SOURCES) known |= s[0].equals(key);
            if (known && !out.contains(key)) out.add(key);
        }
        return out;
    }

    private static String join(List<String> v) {
        StringBuilder b = new StringBuilder();
        for (String s : v) { if (b.length() > 0) b.append(','); b.append(s); }
        return b.toString();
    }

    private static double number(String s, double fallback) {
        try { return s == null ? fallback : Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }
}
