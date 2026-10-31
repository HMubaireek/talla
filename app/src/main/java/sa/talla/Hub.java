package sa.talla;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the car's screen shows and when it draws, for the whole app: one thread ("talla-draw") owns the state and draws every target
 * (the car's surface, the phone's preview), so nothing is locked; the network and the photo decoding run on two worker threads and
 * hand their results back to it.
 *
 * Drawing is lazy: while a photo fades or settles it draws about 30 frames a second, otherwise once a minute (the clock) and when
 * something new comes in. With no target (the car's screen is not shown, the phone's settings are closed) nothing is fetched.
 */
final class Hub implements SharedPreferences.OnSharedPreferenceChangeListener {
    /** a screen to draw on; frame() draws one frame now and says whether it is still moving */
    interface Target { boolean frame(long now); Context context(); }

    /** the photos of the background or of the carousel */
    static final class Deck {
        final boolean carousel;
        List<Photos.Item> items = Collections.emptyList();
        String listKey = "";
        long listAt;
        boolean listing, loading;
        int idx = -1;
        Bitmap cur, prev;
        float curBright = 1, prevBright = 1;
        /** when cur came in (its fade and its slow movement count from here) */
        long shownAt;
        long nextAt;
        Deck(boolean carousel) { this.carousel = carousel; }
        void reset() { items = Collections.emptyList(); listKey = ""; idx = -1; cur = null; prev = null; nextAt = 0; }
    }

    private static Hub instance;

    static synchronized Hub get(Context c) {
        if (instance == null) instance = new Hub(c.getApplicationContext());
        return instance;
    }

    final Context app;
    final Settings settings;
    final Handler handler;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final List<Target> targets = new ArrayList<>();

    Weather weather;
    Match match;
    /** what the car says (DashScreen fills it), and the nearest station while the fuel is low */
    final CarData car;
    Stations station;
    private boolean stationBusy;
    Bitmap homeBadge, awayBadge;
    private String badgesOf = "";
    private boolean weatherBusy, matchBusy;
    final Deck background = new Deck(false), carousel = new Deck(true);
    /** the size the photos are decoded at: the largest target's */
    int width = 800, height = 480;

    private Hub(Context app) {
        this.app = app;
        settings = new Settings(app);
        car = new CarData(app);
        HandlerThread t = new HandlerThread("talla-draw");
        t.start();
        handler = new Handler(t.getLooper());
        Settings.prefs(app).registerOnSharedPreferenceChangeListener(this);
        handler.post(() -> {
            weather = Weather.kept(app);
            match = Match.kept(app, settings.team(), System.currentTimeMillis());
        });
    }

    // ---------------------------------------------------------------- targets and frames

    void add(Target t, int w, int h) {
        handler.post(() -> {
            if (!targets.contains(t)) targets.add(t);
            if (w * h > width * height) { width = w; height = h; }
            invalidate();
        });
    }

    void remove(Target t) { handler.post(() -> targets.remove(t)); }

    /** draw again soon (from any thread) */
    void invalidate() {
        handler.removeCallbacks(loop);
        handler.post(loop);
    }

    private final Runnable loop = this::loop;

    private void loop() {
        handler.removeCallbacks(loop);
        if (targets.isEmpty()) return;
        long now = System.currentTimeMillis();
        housekeeping(now);
        boolean moving = false;
        for (Target t : new ArrayList<>(targets)) moving |= t.frame(now);
        long next = 60_000 - now % 60_000 + 50;   // the next minute, for the clock
        for (Deck d : new Deck[]{background, carousel}) if (d.nextAt > now) next = Math.min(next, d.nextAt - now);
        handler.postDelayed(loop, moving ? 33 : Math.max(250, next));
    }

    /** the carousel's next photo now (a swipe, the knob, a tap on the preview) */
    void nextCarousel() { handler.post(() -> { carousel.nextAt = 0; invalidate(); }); }

    /** the car's one button: the next background now */
    void nextBackground() { handler.post(() -> { background.nextAt = 0; invalidate(); }); }

    // ---------------------------------------------------------------- what is due

    private void housekeeping(long now) {
        if (settings.shows("weather") && !weatherBusy && Weather.due(app)) {
            weatherBusy = true;
            io.execute(() -> {
                Weather w = Weather.fetch(app, settings);
                handler.post(() -> { weatherBusy = false; if (w != null) weather = w; invalidate(); });
            });
        }
        boolean low = settings.shows("fuel") && car.low(settings.fuelLimitKm());
        if (!low || !settings.fuelStation()) station = null;
        else if (!stationBusy) {
            final android.location.Location here = Weather.lastKnown(app);
            if (here != null && (station == null || station.stale(here, now))) {
                stationBusy = true;
                io.execute(() -> {
                    Stations st = Stations.nearest(here);
                    handler.post(() -> { stationBusy = false; if (st != null) station = st; invalidate(); });
                });
            }
        }
        final String team = settings.team();
        if (match != null && Match.kept(app, team, now) == null) match = null;   // another team was picked, or it was played
        if (settings.shows("match") && !matchBusy && Match.due(app, team, now)) {
            matchBusy = true;
            io.execute(() -> {
                Match.fetch(app, team);
                Match m = Match.kept(app, team, System.currentTimeMillis());
                handler.post(() -> { matchBusy = false; match = m; invalidate(); });
            });
        }
        if (match != null && now > match.at + 2 * 3600_000L) match = null;
        if (match != null && !badgesOf.equals(match.homeBadge + match.awayBadge)) {
            final Match m = match;
            badgesOf = m.homeBadge + m.awayBadge;
            io.execute(() -> {
                Bitmap h = m.homeBadge.isEmpty() ? null : Photos.bitmap(app, m.homeBadge, 96, 96);
                Bitmap a = m.awayBadge.isEmpty() ? null : Photos.bitmap(app, m.awayBadge, 96, 96);
                handler.post(() -> { homeBadge = h; awayBadge = a; invalidate(); });
            });
        }
        deck(background, settings.shows("background"), settings.bgSources(), settings.bgSeconds(), width, height, now);
        deck(carousel, settings.shows("carousel"), settings.carouselSources(), settings.carouselSeconds(), width / 2, height / 2, now);
    }

    private void deck(final Deck d, boolean on, final List<String> sources, int seconds, final int w, final int h, long now) {
        if (!on) { if (d.cur != null) d.reset(); return; }
        final String key = sources + "|" + (sources.contains(Settings.OWN) ? settings.ownFolder() : "");
        if (!d.listing && (!key.equals(d.listKey) || (d.items.isEmpty() && now - d.listAt > 60_000) || now - d.listAt > 6 * 3600_000L)) {
            d.listing = true;
            final String own = settings.ownFolder();
            io.execute(() -> {
                List<Photos.Item> list = Photos.list(app, sources, own);
                handler.post(() -> {
                    d.listing = false;
                    boolean changed = !key.equals(d.listKey);
                    d.items = list;
                    d.listKey = key;
                    d.listAt = System.currentTimeMillis();
                    if (changed) { d.idx = -1; d.nextAt = 0; }
                    invalidate();
                });
            });
        }
        if (d.loading || d.items.isEmpty() || now < d.nextAt) return;
        // «تهدئة الشاشة أثناء السير»: the photo stays until the car stops (the minute's frame looks again)
        if (d.cur != null && settings.calmMoving() && car.moving(now)) return;
        d.loading = true;
        d.idx = (d.idx + 1) % d.items.size();
        final Photos.Item item = d.items.get(d.idx);
        final int wait = seconds * 1000;
        io.execute(() -> {
            Bitmap bm = Photos.bitmap(app, item.url, w, h);
            handler.post(() -> {
                d.loading = false;
                long t = System.currentTimeMillis();
                if (bm == null) { d.nextAt = t + 3000; invalidate(); return; }   // that one failed: the next in a moment
                d.prev = d.cur;
                d.prevBright = d.curBright;
                d.cur = bm;
                d.curBright = item.bright;
                d.shownAt = t;
                d.nextAt = t + wait;
                invalidate();
            });
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (key != null && (key.equals("weather_here") || key.equals("lat") || key.equals("lon") || key.equals("city")))
            app.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().putLong("tried", 0).apply();
        invalidate();
    }

    /** a minute after the screen goes, the photos are let go (the lists stay) */
    void trimLater() {
        handler.postDelayed(() -> {
            if (!targets.isEmpty()) return;
            background.cur = background.prev = carousel.cur = carousel.prev = null;
            background.nextAt = carousel.nextAt = 0;
        }, 60_000);
    }
}
