package sa.bayti.auto;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The phone's screen of the app: a live preview of the car's screen and every setting, saved as soon as it changes (the car's
 * screen follows at once). Plain framework views, in Tajawal, right to left.
 */
public final class SettingsActivity extends Activity {
    private static final int PICK_FOLDER = 1, ASK_LOCATION = 2;
    private static final int BG = 0xFF0B1F16, CARD = 0xFF13291F;

    private Settings s;
    private LinearLayout list;
    private Typeface regular, bold;
    private TextView folderLabel;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        s = new Settings(this);
        try {
            regular = Typeface.createFromAsset(getAssets(), "fonts/Tajawal-Regular.ttf");
            bold = Typeface.createFromAsset(getAssets(), "fonts/Tajawal-Bold.ttf");
        } catch (RuntimeException e) {
            regular = Typeface.DEFAULT;
            bold = Typeface.DEFAULT_BOLD;
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(list);
        setContentView(scroll);

        TextView title = text(getString(R.string.app_name), 26, Renderer.GOLD, bold);
        list.addView(title);
        TextView sub = text("ما تراه هنا هو ما تعرضه شاشة السيارة. المس الصورة لتنتقل إلى صورة العرض التالية.", 14, Renderer.DIM, regular);
        sub.setPadding(0, dp(4), 0, dp(12));
        list.addView(sub);
        PreviewView preview = new PreviewView(this);
        GradientDrawable round = new GradientDrawable();
        round.setCornerRadius(dp(12));
        preview.setClipToOutline(true);
        preview.setBackground(round);
        list.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        toggle("معاينة الشاشة المقسومة (الخريطة مع بطاقات أندرويد أوتو)", false, preview::setSplit);

        section("ما يظهر في السيارة");
        for (String[] item : Settings.ITEMS) toggle(item[1], s.shows(item[0]), on -> s.setShows(item[0], on));

        section("العبارة");
        note("عبارة لكل جزء من اليوم، كعبارات بيتي. اتركها فارغة لتظهر العبارة المعتادة.");
        for (int k = 0; k < Settings.GREET_PARTS.length; k++) {
            final int part = k;
            note(Settings.GREET_PARTS[k][1]);
            edit(s.greetPhrase(k), Settings.GREET_PARTS[k][2], InputType.TYPE_CLASS_TEXT, v -> s.setGreetPhrase(part, v));
        }

        section("الصلاة");
        choice("طريقة الحساب", Settings.PRAYER_METHODS, s.method(), s::setMethod);
        toggle("الصلاة القادمة فقط حين يبقى لها أقل من ساعة", s.prayerSoonOnly(), s::setPrayerSoonOnly);
        toggle("إخفاء العصر (تُصلّى مع الظهر)", s.hideAsr(), s::setHideAsr);
        toggle("إخفاء العشاء (تُصلّى مع المغرب)", s.hideIsha(), s::setHideIsha);

        section("المكان");
        note("للصلاة والتاريخ، وللطقس حين لا يُعرف مكان الهاتف.");
        final String[] place = {s.city(), String.valueOf(s.lat()), String.valueOf(s.lon())};
        note("المدينة");
        edit(place[0], Settings.HOME_CITY, InputType.TYPE_CLASS_TEXT, v -> { place[0] = v; savePlace(place); });
        note("خط العرض");
        edit(place[1], String.valueOf(Settings.HOME_LAT), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                v -> { place[1] = v; savePlace(place); });
        note("خط الطول");
        edit(place[2], String.valueOf(Settings.HOME_LON), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                v -> { place[2] = v; savePlace(place); });
        toggle("الطقس حيث تكون السيارة (موقع الهاتف)", s.weatherHere(), on -> {
            s.setWeatherHere(on);
            if (on && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_LOCATION);
        });

        section("التاريخ والوقت");
        toggle("أرقام عربية 123 (بدل الهندية ١٢٣)", s.latinDigits(), s::setLatinDigits);
        toggle("ساعة ٢٤", s.clock24(), s::setClock24);
        String[][] adj = {{"-2", "قبل يومين"}, {"-1", "قبل يوم"}, {"0", "بلا تعديل (أم القرى)"}, {"1", "بعد يوم"}, {"2", "بعد يومين"}};
        choice("تعديل التاريخ الهجري", adj, String.valueOf(s.hijriAdjust()), v -> s.setHijriAdjust(Integer.parseInt(v)));

        section("صورة الخلفية");
        sources(s.bgSources(), s::setBgSources);
        choice("تتبدل كل", seconds(Settings.BG_SECONDS), String.valueOf(s.bgSeconds()), v -> s.setBgSeconds(Integer.parseInt(v)));

        section("العرض المتحرك");
        sources(s.carouselSources(), s::setCarouselSources);
        choice("تتبدل كل", seconds(Settings.CAROUSEL_SECONDS), String.valueOf(s.carouselSeconds()), v -> s.setCarouselSeconds(Integer.parseInt(v)));
        toggle("حركة الصور الهادئة (كما في بيتي)", s.motion(), s::setMotion);

        section("صوري");
        folderLabel = note(folderText());
        Button pick = new Button(this);
        pick.setText("اختر مجلد الصور في الهاتف");
        pick.setTypeface(bold);
        pick.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER));
        list.addView(pick, new LinearLayout.LayoutParams(-1, -2));

        if (saved == null && s.weatherHere() && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_LOCATION);

        section("في السيارة");
        note("يظهر التطبيق في قائمة تطبيقات Android Auto باسم «بيتي في السيارة». الزرّان «التالي» و«خلفية» يُبدّلان الصور، "
                + "ويخفيهما Android Auto بعد ثوانٍ.");
        note("الخط: Tajawal (رخصة OFL)");
        note("البيانات: Open-Meteo · TheSportsDB · ويكيميديا · Bing");
    }

    private void savePlace(String[] p) {
        try {
            double lat = Double.parseDouble(p[1].trim()), lon = Double.parseDouble(p[2].trim());
            if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) s.setPlace(p[0].trim().isEmpty() ? Settings.HOME_CITY : p[0], p[1], p[2]);
        } catch (NumberFormatException ignored) {
            // still typing: kept when it is a number
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FOLDER || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // the picker gave no lasting permission: it works until the phone restarts
        }
        String id = DocumentsContract.getTreeDocumentId(tree);
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        s.setOwnFolder(tree.toString(), name.isEmpty() ? "الذاكرة" : name);
        folderLabel.setText(folderText());
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        // the weather of where the phone is, now that it may be known: asked again at once
        if (request == ASK_LOCATION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences("weather", MODE_PRIVATE).edit().putLong("tried", 0).apply();
            Hub.get(this).invalidate();
        }
    }

    private String folderText() {
        String n = s.ownFolderName();
        return n.isEmpty() ? "لم يُختر مجلد بعد. اختر مجلدًا، ثم فعّل «صوري» في مصادر الخلفية أو العرض المتحرك."
                : "المجلد: " + n + ". فعّل «صوري» في المصادر أعلاه لتظهر صوره.";
    }

    // ---------------------------------------------------------------- building blocks

    private interface OnBool { void set(boolean v); }
    private interface OnText { void set(String v); }

    private void section(String name) {
        TextView t = text(name, 18, Renderer.GOLD, bold);
        t.setPadding(0, dp(24), 0, dp(6));
        list.addView(t);
    }

    private TextView note(String s) {
        TextView t = text(s, 14, Renderer.DIM, regular);
        t.setPadding(0, dp(6), 0, dp(2));
        list.addView(t);
        return t;
    }

    private void toggle(String label, boolean on, OnBool done) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Renderer.SAND);
        sw.setTypeface(regular);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setChecked(on);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setOnCheckedChangeListener((b, v) -> done.set(v));
        list.addView(sw, new LinearLayout.LayoutParams(-1, -2));
    }

    private void edit(String value, String hint, int type, OnText done) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTypeface(regular);
        e.setTextColor(Renderer.SAND);
        e.setHintTextColor(0x88C3B8A2);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable ed) { done.set(ed.toString()); }
        });
        list.addView(e, new LinearLayout.LayoutParams(-1, -2));
    }

    private void choice(String label, final String[][] options, String current, OnText done) {
        note(label);
        List<String> names = new ArrayList<>();
        int at = 0;
        for (int i = 0; i < options.length; i++) {
            names.add(options[i][1]);
            if (options[i][0].equals(current)) at = i;
        }
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names) {
            @Override
            public View getView(int pos, View v, ViewGroup parent) { return style(super.getView(pos, v, parent)); }
            @Override
            public View getDropDownView(int pos, View v, ViewGroup parent) { return style(super.getDropDownView(pos, v, parent)); }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        sp.setSelection(at, false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(8));
        sp.setBackground(g);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { done.set(options[pos][0]); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        list.addView(sp, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private View style(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            t.setTypeface(regular);
            t.setTextColor(Renderer.SAND);
            t.setBackgroundColor(CARD);
            t.setPadding(dp(12), dp(10), dp(12), dp(10));
        }
        return v;
    }

    private interface OnList { void set(List<String> v); }

    private void sources(List<String> chosen, OnList done) {
        final List<String> now = new ArrayList<>(chosen);
        for (String[] src : Settings.SOURCES) {
            CheckBox cb = new CheckBox(this);
            cb.setText(src[1]);
            cb.setTypeface(regular);
            cb.setTextColor(Renderer.SAND);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            cb.setChecked(now.contains(src[0]));
            cb.setOnCheckedChangeListener((b, on) -> {
                now.remove(src[0]);
                if (on) now.add(src[0]);
                // in the table's order, whatever the order of the ticks
                List<String> ordered = new ArrayList<>();
                for (String[] o : Settings.SOURCES) if (now.contains(o[0])) ordered.add(o[0]);
                done.set(ordered);
            });
            list.addView(cb);
        }
    }

    private static String[][] seconds(int[] values) {
        String[][] out = new String[values.length][];
        for (int i = 0; i < values.length; i++) {
            int v = values[i];
            String label = v < 60 ? Texts.digits(String.valueOf(v), false) + " ثانية"
                    : v < 3600 ? (v == 60 ? "دقيقة" : v == 120 ? "دقيقتين" : Texts.digits(String.valueOf(v / 60), false) + (v / 60 <= 10 ? " دقائق" : " دقيقة"))
                    : (v == 3600 ? "ساعة" : Texts.digits(String.valueOf(v / 3600), false) + " ساعات");
            out[i] = new String[]{String.valueOf(v), label};
        }
        return out;
    }

    private TextView text(String s, float sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        return t;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
