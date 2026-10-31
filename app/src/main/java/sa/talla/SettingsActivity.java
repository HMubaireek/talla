package sa.talla;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    /** Talla's palette: night blue, gold and sand (docs/design/talla-icons.png) */
    static final int BG = 0xFF0E1A2B, CARD = 0xFF16263D, LINE = 0xFF24384F, GOLD = 0xFFE3B65E, GOLD_HI = 0xFFFFE3A6,
            SAND = 0xFFF3E9D6, MUTED = 0xFFB4BAC4, OFF = 0xFF6E7C91;

    private Settings s;
    /** root: the whole page; list: the card that the next rows go into (a section's card, or root before the first) */
    private LinearLayout root, list;
    private Typeface regular, bold;
    private TextView folderLabel, teamLabel;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        s = new Settings(this);
        MediaService.enable(this, s.mediaCard());   // the service follows the setting (after a restore, or a fresh install)
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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(32));
        list = root;
        scroll.addView(root);
        setContentView(scroll);

        // the header: the icon, «طلّة» and a line under it
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_talla);
        head.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(14), 0, dp(14), 0);
        names.addView(text(getString(R.string.app_name), 30, GOLD, bold));
        names.addView(text("Talla · طلّة على يومك من السيارة", 13, MUTED, regular));
        head.addView(names);
        list.addView(head);
        TextView sub = text("ما تراه هنا هو ما تعرضه شاشة السيارة. المس الصورة لتنتقل إلى صورة العرض التالية.", 14, MUTED, regular);
        sub.setPadding(0, dp(14), 0, dp(12));
        list.addView(sub);
        PreviewView preview = new PreviewView(this);
        GradientDrawable round = new GradientDrawable();
        round.setCornerRadius(dp(12));
        preview.setClipToOutline(true);
        preview.setBackground(round);
        list.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        section("المعاينة");
        note("للمعاينة هنا فقط، لا تغيّر شيئًا في السيارة.");
        toggle("معاينة الشاشة المقسومة (الخريطة مع بطاقات أندرويد أوتو)", false, preview::setSplit);
        toggle("معاينة الليل (كما تخففها السيارة حين تضيء أنوارها)", false, preview::setNight);

        section("ما يظهر في السيارة");
        for (String[] item : Settings.ITEMS) toggle(item[1], s.shows(item[0]), on -> s.setShows(item[0], on));

        section("فريقك");
        note("مباراته القادمة تظهر في الزاوية اليسرى من الشاشة، وفي بطاقة الوسائط يوم المباراة. اختر الدوري ثم الفريق.");
        teamLabel = text("", 18, SAND, bold);
        teamLabel.setPadding(0, dp(8), 0, dp(2));
        list.addView(teamLabel);
        showTeam();
        button("اختر الدوري والفريق", v -> pickLeague());
        outline("الهلال (المعتاد)", v -> {
            s.setTeam(Settings.DEFAULT_TEAM, Settings.DEFAULT_TEAM_NAME, Settings.DEFAULT_LEAGUE);
            showTeam();
        });

        section("العبارة");
        note("عبارة لكل جزء من اليوم. اتركها فارغة لتظهر العبارة المعتادة.");
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
        toggle("حركة الصور الهادئة", s.motion(), s::setMotion);

        section("صوري");
        folderLabel = note(folderText());
        button("اختر مجلد الصور في الهاتف", v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER));

        section("تخفيف الإضاءة");
        note("يُعتم الصورة كلها، والكتابة بقدر أقل لتبقى واضحة.");
        choice("متى", Settings.NIGHT_MODES, s.nightMode(), s::setNightMode);
        choice("بأي قدر", Settings.NIGHT_DIM, String.valueOf(s.nightDim()), v -> s.setNightDim(Integer.parseInt(v)));

        section("بطاقة الوسائط");
        note("في الشاشة المقسومة يترك Android Auto المساحة الكبيرة لتطبيق خرائط واحد (مثل خرائط Google)، وبجانبها بطاقة تطبيق الوسائط. "
                + "حين تُشغَّل هذه يظهر «طلّة» أيضًا تطبيقَ وسائط: بطاقته صورة الخلفية مع الساعة، وسطورها الوقت والصلاة القادمة "
                + "(أو تنبيه الطقس، أو مباراة فريقك يوم المباراة) والتاريخ والطقس، و«التالي» فيها يبدّل الخلفية. لا تشغّل صوتًا، لكنها تأخذ مكان بطاقة YouTube Music "
                + "ما دامت مختارة. بعد تغييرها افصل الهاتف عن السيارة وأعد وصله.");
        toggle("طلّة بطاقةَ وسائط بجانب الخريطة", s.mediaCard(), on -> {
            s.setMediaCard(on);
            MediaService.enable(this, on);
        });

        if (saved == null && s.weatherHere() && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_LOCATION);

        section("التحديث من الهاتف");
        note("بلا حاسوب: يبني GitHub كل إصدار، فيُنزَّل هنا ويثبّته UnknownAuto. "
                + "١) نزّل التحديث. ٢) افتح Shizuku وشغّله بـ«التشغيل عبر تصحيح الأخطاء اللاسلكي» (والواي فاي مفتوح). "
                + "٣) افتح UnknownAuto: ‏Load APK ← Download ← الملف الجديد ← Install، ثم Update.");
        final long have = Update.installed(this);
        final String haveName = "الإصدار المثبت " + Texts.digits(Update.installedName(this), false);
        final TextView state = note(haveName + " · يُبحث عن الأحدث…");
        final Update.Latest[] newest = {new Update.Latest(-1, "")};
        new Thread(() -> {
            Update.Latest n = Update.latest();
            runOnUiThread(() -> {
                newest[0] = n;
                state.setText(haveName + " · " + (n.code < 0 ? "تعذّرت معرفة الأحدث"
                        : n.code > have ? "الأحدث " + Texts.digits(n.name.isEmpty() ? String.valueOf(n.code) : n.name, false) + ": يوجد تحديث"
                        : "هذا هو الأحدث"));
            });
        }, "talla-update").start();
        button("نزّل التحديث", v -> {
            long id = Update.download(this, newest[0]);
            state.setText("يُنزَّل إلى مجلد Download…");
            watch(state, id);
        });
        outline("افتح Shizuku", v -> { if (!Update.open(this, Update.SHIZUKU, "shizuku")) state.setText("Shizuku غير مثبت"); });
        outline("افتح UnknownAuto", v -> { if (!Update.open(this, null, "unknownauto")) state.setText("UnknownAuto غير مثبت"); });

        section("في السيارة");
        note("يظهر التطبيق في قائمة تطبيقات Android Auto باسم «طلّة». زرّه الوحيد يبدّل صورة الخلفية، "
                + "ويخفيه Android Auto بعد ثوانٍ.");
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

    /** shows a download's end in `state`, looking every second while this screen is open */
    private void watch(TextView state, long id) {
        state.postDelayed(() -> {
            if (isDestroyed()) return;
            int st = Update.status(this, id);
            if (st == DownloadManager.STATUS_SUCCESSFUL) state.setText("نُزّل في Download. الآن: Shizuku ثم UnknownAuto.");
            else if (st == DownloadManager.STATUS_FAILED || st == 0) state.setText("تعذّر التنزيل. تأكد من الاتصال وحاول ثانية.");
            else watch(state, id);
        }, 1000);
    }

    /** a gold button, the width of the card */
    private void button(String label, View.OnClickListener click) { button(label, click, true); }

    /** a quieter one: gold words in a gold outline */
    private void outline(String label, View.OnClickListener click) { button(label, click, false); }

    private void button(String label, View.OnClickListener click, boolean filled) {
        Button b = new Button(this);
        b.setText(label);
        b.setTypeface(bold);
        b.setTextColor(filled ? BG : GOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        GradientDrawable g = rounded(filled ? GOLD : 0x00000000, 24);
        if (!filled) g.setStroke(dp(1.5f), GOLD);
        GradientDrawable pressed = rounded(filled ? GOLD_HI : 0x22E3B65E, 24);
        if (!filled) pressed.setStroke(dp(1.5f), GOLD_HI);
        StateListDrawable look = new StateListDrawable();
        look.addState(new int[]{android.R.attr.state_pressed}, pressed);
        look.addState(new int[0], g);
        b.setBackground(look);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-1, dp(48));
        at.topMargin = dp(10);
        at.bottomMargin = dp(6);
        list.addView(b, at);
    }

    // ---------------------------------------------------------------- «فريقك»: a league, then a team in it

    private void showTeam() {
        String league = Teams.leagueArabic(this, s.teamLeague());
        teamLabel.setText(s.teamName() + (league == null ? "" : "  ·  " + league));
    }

    private void pickLeague() {
        final List<String[]> leagues = Teams.leagues(this);
        if (leagues.isEmpty()) return;
        String[] rows = new String[leagues.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = leagues.get(i)[1];
        dialog("اختر الدوري", rows, i -> pickTeam(leagues.get(i)));
    }

    private void pickTeam(final String[] league) {
        final List<Teams.Team> clubs = Teams.inLeague(this, league[0]);
        String[] rows = new String[clubs.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = (clubs.get(i).id.equals(s.team()) ? "✓ " : "") + clubs.get(i).label();
        dialog(league[1], rows, i -> {
            Teams.Team t = clubs.get(i);
            s.setTeam(t.id, t.name(), league[0]);
            showTeam();
        });
    }

    private interface OnPick { void picked(int i); }

    /** a list to pick one row from, in the app's colours */
    private void dialog(String title, String[] rows, OnPick done) {
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public View getView(int pos, View v, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, v, parent);
                t.setTypeface(regular);
                t.setTextColor(SAND);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                t.setTextDirection(View.TEXT_DIRECTION_RTL);
                t.setPadding(dp(20), dp(12), dp(20), dp(12));
                return t;
            }
        };
        TextView head = text(title, 20, GOLD, bold);
        head.setPadding(dp(20), dp(18), dp(20), dp(8));
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                .setCustomTitle(head)
                .setAdapter(a, (x, i) -> done.picked(i))
                .create();
        d.getWindow().setBackgroundDrawable(rounded(CARD, 16));
        d.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        d.show();
    }

    private String folderText() {
        String n = s.ownFolderName();
        return n.isEmpty() ? "لم يُختر مجلد بعد. اختر مجلدًا، ثم فعّل «صوري» في مصادر الخلفية أو العرض المتحرك."
                : "المجلد: " + n + ". فعّل «صوري» في المصادر أعلاه لتظهر صوره.";
    }

    // ---------------------------------------------------------------- building blocks

    private interface OnBool { void set(boolean v); }
    private interface OnText { void set(String v); }

    /** a gold heading after a short gold rule, then a card that holds the rows until the next section */
    private void section(String name) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(4), dp(26), dp(4), dp(10));
        View rule = new View(this);
        rule.setBackground(rounded(GOLD, 2));
        h.addView(rule, new LinearLayout.LayoutParams(dp(4), dp(20)));
        TextView t = text(name, 18, GOLD, bold);
        t.setPadding(dp(10), 0, dp(10), 0);
        h.addView(t);
        root.addView(h);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable card = rounded(CARD, 16);
        card.setStroke(dp(1), LINE);
        list.setBackground(card);
        list.setPadding(dp(16), dp(6), dp(16), dp(8));
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    /** on: a gold track with a dark knob; off: an empty, outlined track with a grey knob. Plain to tell apart at a glance. */
    private void switchLook(Switch sw) {
        GradientDrawable trackOn = rounded(GOLD, 14), trackOff = rounded(0x00000000, 14);
        trackOn.setSize(dp(56), dp(28));
        trackOff.setSize(dp(56), dp(28));
        trackOff.setStroke(dp(2), OFF);
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, trackOn);
        track.addState(new int[0], trackOff);

        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_checked}, knob(BG));
        thumb.addState(new int[0], knob(OFF));
        sw.setTrackDrawable(track);
        sw.setThumbDrawable(thumb);
        sw.setSwitchMinWidth(dp(56));
        sw.setSwitchPadding(dp(16));
    }

    private Drawable knob(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setSize(dp(20), dp(20));
        return new InsetDrawable(g, dp(4));
    }

    private TextView note(String s) {
        TextView t = text(s, 14, MUTED, regular);
        t.setPadding(0, dp(6), 0, dp(2));
        list.addView(t);
        return t;
    }

    private void toggle(String label, boolean on, OnBool done) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(SAND);
        sw.setTypeface(regular);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        switchLook(sw);
        sw.setChecked(on);
        sw.setMinHeight(dp(52));
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
        e.setTextColor(SAND);
        e.setHintTextColor(0x88B4BAC4);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        e.setBackgroundTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{GOLD, OFF}));
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
            public View getView(int pos, View v, ViewGroup parent) {
                View shown = style(super.getView(pos, v, parent));
                shown.setBackgroundColor(0x00000000);   // the box's own outline shows around the chosen item
                return shown;
            }
            @Override
            public View getDropDownView(int pos, View v, ViewGroup parent) { return style(super.getDropDownView(pos, v, parent)); }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        sp.setSelection(at, false);
        GradientDrawable g = rounded(BG, 10);
        g.setStroke(dp(1), LINE);
        sp.setBackground(g);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { done.set(options[pos][0]); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        // the custom background hides the spinner's own arrow, so one is drawn over its far end (it lets touches through)
        FrameLayout box = new FrameLayout(this);
        box.addView(sp, new FrameLayout.LayoutParams(-1, -1));
        TextView arrow = text("▾", 18, GOLD, regular);
        arrow.setPadding(dp(14), 0, dp(14), 0);
        box.addView(arrow, new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        sp.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, -2);
        place.topMargin = dp(4);
        place.bottomMargin = dp(6);
        list.addView(box, place);
    }

    private View style(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            t.setTypeface(regular);
            t.setTextColor(SAND);
            t.setBackgroundColor(BG);
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
            cb.setTextColor(SAND);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            cb.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{GOLD, OFF}));
            cb.setMinHeight(dp(44));
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
