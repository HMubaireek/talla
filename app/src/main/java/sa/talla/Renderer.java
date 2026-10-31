package sa.talla;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.Random;

/**
 * Draws Talla's screen on a canvas: the photo with Bayti TV's look (its slow settle, its darkness, the veil, the grain and the shades at
 * the corners), the dates and the clock on the right, the weather and the carousel on the left, the next prayer in the bottom right
 * corner and the chosen team's next match in the bottom left one, straight on the photo. Laid out on a screen 480 units high (Mazda's 800 x 480 is 1:1) and as wide as the car's.
 * Only the hub's thread calls it.
 */
final class Renderer {
    static final int SAND = 0xFFF6EFE0, DIM = 0xFFC3B8A2, GOLD = 0xFFE3B65E, GOLD_HI = 0xFFFFE3A6, AMBER = 0xFFFFB45A;
    /** the phone's preview only: shows the low-fuel warning with made-up numbers */
    volatile boolean demoFuel;
    private static final long BG_FADE = 2000, CAR_FADE = 650, SETTLE = 60_000, PUSH = 9000;

    private final Typeface light, regular, medium, bold;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint grain = new Paint();
    private final RectF r = new RectF();
    private final Rect src = new Rect();
    private final Path clip = new Path();

    Renderer(Context c) {
        light = font(c, "Tajawal-Light.ttf");
        regular = font(c, "Tajawal-Regular.ttf");
        medium = font(c, "Tajawal-Medium.ttf");
        bold = font(c, "Tajawal-Bold.ttf");
        grain.setShader(new BitmapShader(noise(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        grain.setAlpha(Math.round(255 * .05f * .55f * 2.2f));
    }

    private static Typeface font(Context c, String name) {
        try { return Typeface.createFromAsset(c.getAssets(), "fonts/" + name); } catch (RuntimeException e) { return Typeface.DEFAULT; }
    }

    /** Bayti TV's grain: a fine noise, tiled */
    private static Bitmap noise() {
        Bitmap b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Random rnd = new Random(7);
        int[] px = new int[160 * 160];
        for (int i = 0; i < px.length; i++) { int v = rnd.nextInt(256); px[i] = Color.argb(255, v, v, v); }
        b.setPixels(px, 0, 160, 0, 0, 160, 160);
        return b;
    }

    /**
     * One frame on a canvas w x h; area is the part of it the car shows uncovered (Android Auto's split view leaves the app only a
     * panel of it), null for all of it. Only its sides are used: its top and bottom move with the car's buttons. dim (0 to 1) is the
     * night's dimming: the photo gets all of it, the words about a third, so they stay easy to read.
     * True while something moves (a fade, a settle, a push-in), so the hub draws the next frame soon.
     */
    boolean draw(Canvas cv, int w, int h, Rect area, float dim, Hub hub, long now) {
        Settings s = hub.settings;
        CarData car = demoFuel ? CarData.demo(hub.app) : hub.car;
        // «تهدئة الشاشة أثناء السير»: no slow motion while the car moves
        boolean latin = s.latinDigits(), motion = s.motion() && !(s.calmMoving() && car.moving(now));
        float k = h / 480f, W = w / k, H = 480;
        float aL = 0, aR = W;
        if (area != null && area.width() >= w * .4f && area.height() >= h * .6f) {
            aL = Math.max(0, area.left / k);
            aR = Math.min(W, area.right / k);
        }
        cv.save();
        cv.scale(k, k);
        boolean moving = background(cv, W, H, aL, aR, hub, now, motion);
        if (dim > 0) cv.drawColor(Color.argb(Math.round(255 * dim), 0, 0, 0));

        float L = aL + 24, T = 18, R = aR - 24, B = H - 18, CW = R - L;
        // a narrow panel (the split view) gets smaller type
        float z = Math.max(.8f, Math.min(1, CW / 640));
        String tz = s.tz();

        // ---- right: the dates, the clock and the phrase, at most 56% of the width
        float rightW = CW * .56f;
        float y = T;
        if (s.shows("hijri")) {
            y += 24 * z;
            shadow(10);
            line(cv, Texts.hijri(now, tz, s.hijriAdjust(), latin), R, y, medium, 22 * z, SAND, rightW, Paint.Align.RIGHT);
        }
        if (s.shows("greg")) {
            y += 22 * z;
            shadow(10);
            line(cv, Texts.gregorian(now, tz, latin), R, y, light, 16 * z, DIM, rightW, Paint.Align.RIGHT);
        }
        text.clearShadowLayer();
        if (s.shows("hijri") || s.shows("greg")) {
            y += 12;
            fill.setShader(new LinearGradient(R, 0, R - 110, 0, GOLD, GOLD & 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(R - 110, y, R, y + 1.2f, fill);
            fill.setShader(null);
        }
        float clockW = 0;
        if (s.shows("clock")) {
            String[] c = Texts.clock(now, tz, s.clock24(), latin);
            // 112 units high, smaller when the time and its «م» don't fit the column
            text.setTypeface(bold);
            text.setTextSize(112);
            float full = text.measureText(c[0]);
            text.setTypeface(medium);
            text.setTextSize(30);
            float ampm = c[1].isEmpty() ? 0 : text.measureText(c[1]) + 10;
            float size = 112 * Math.min(1, rightW / (full + ampm));
            y += size * .93f;
            text.setTypeface(bold);
            text.setTextSize(size);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(SAND);
            text.setShadowLayer(18, 0, 3, 0x66000000);
            cv.drawText(c[0], R, y, text);
            float cw = text.measureText(c[0]);
            clockW = cw;
            if (!c[1].isEmpty()) {
                text.setTypeface(medium);
                text.setTextSize(30 * size / 112);
                text.setColor(GOLD);
                cv.drawText(c[1], R - cw - 10, y, text);
                clockW += text.measureText(c[1]) + 10;
            }
            text.clearShadowLayer();
        }
        if (s.shows("phrase")) {
            y += s.shows("clock") ? 46 * z : 40 * z;
            int part = Prayers.part(s, now);
            shadow(10);
            line(cv, s.greetShown(part), R, y, medium, 30 * z, GOLD, rightW, Paint.Align.RIGHT);
            text.clearShadowLayer();
        }

        // ---- the bottom corners: the next prayer on the right, the team's match on the left, on the photo itself
        float cornerW = CW / 2 - 14;
        Prayers.Next next = s.shows("prayer") ? Prayers.next(s, now) : null;
        if (next != null && s.prayerSoonOnly() && next.at - now > 3600_000L) next = null;
        if (next != null) prayer(cv, R, B, cornerW, next, s, now, latin);
        boolean match = s.shows("match") && hub.match != null;
        if (match) match(cv, L, B, cornerW, hub, now, latin);
        float bottomOfLeft = match ? B - 84 : B;

        // ---- left: the weather and the carousel, beside the right column
        float leftW = CW - Math.max(clockW, CW * .4f) - 28;
        float ly = T;
        Weather wx = hub.weather;
        if (s.shows("weather") && wx != null && wx.fresh(now)) {
            shadow(12);
            String t = Texts.digits(Math.round(wx.temp) + "°", latin);
            text.setTypeface(bold);
            text.setTextSize(46 * z);
            text.setColor(SAND);
            text.setTextAlign(Paint.Align.LEFT);
            cv.drawText(t, L, ly + 44 * z, text);
            float tw = text.measureText(t);
            float topW = CW - rightW - 16 - tw - 12;
            String sky = Texts.skyWord(wx.code, wx.dust);
            line(cv, sky.isEmpty() ? wx.place : sky, L + tw + 12, ly + 24 * z, medium, 18 * z, SAND, topW, Paint.Align.LEFT);
            if (!sky.isEmpty()) line(cv, wx.place, L + tw + 12, ly + 44 * z, light, 15 * z, DIM, topW, Paint.Align.LEFT);
            if (!Double.isNaN(wx.high) && !Double.isNaN(wx.low)) {
                line(cv, "العظمى " + Texts.digits(Math.round(wx.high) + "°", latin) + "  الصغرى " + Texts.digits(Math.round(wx.low) + "°", latin),
                        L, ly + 68 * z, light, 14 * z, DIM, CW - rightW - 16, Paint.Align.LEFT);
            }
            text.clearShadowLayer();
            ly += 82 * z;
        }
        String warn = s.shows("warnings") && wx != null && wx.fresh(now) ? wx.warning(latin) : "";
        if (!warn.isEmpty()) {
            ly += 4;
            warningSign(cv, L, ly + 13 * z, 15 * z);
            shadow(10);
            line(cv, warn, L + 22 * z, ly + 18 * z, medium, 17 * z, AMBER, CW - rightW - 16 - 22 * z, Paint.Align.LEFT);
            text.clearShadowLayer();
            ly += 26 * z;
        }
        // the fuel, from the car: an amber warning (and the nearest station) when low; otherwise, if asked, a quiet line
        if (s.shows("fuel") && car.hasFuel()) {
            boolean low = car.low(s.fuelLimitKm());
            String amount = fuelWords(car, latin);
            float lw = CW - rightW - 16;
            if (low) {
                ly += 4;
                warningSign(cv, L, ly + 13 * z, 15 * z);
                shadow(10);
                line(cv, "الوقود منخفض" + (amount.isEmpty() ? "" : " · " + amount), L + 22 * z, ly + 18 * z, medium, 17 * z, AMBER, lw - 22 * z, Paint.Align.LEFT);
                ly += 26 * z;
                Stations st = demoFuel ? Stations.demo() : hub.station;
                if (st != null && s.fuelStation()) {
                    line(cv, "أقرب محطة: " + st.name + " · نحو " + km(st.km, latin), L + 22 * z, ly + 14 * z, regular, 15 * z, SAND, lw - 22 * z, Paint.Align.LEFT);
                    ly += 22 * z;
                }
                text.clearShadowLayer();
            } else if (s.fuelAlways() && !amount.isEmpty()) {
                shadow(10);
                line(cv, "الوقود " + amount, L, ly + 16 * z, regular, 15 * z, DIM, lw, Paint.Align.LEFT);
                text.clearShadowLayer();
                ly += 22 * z;
            }
        }
        if (s.shows("carousel")) moving |= carousel(cv, L, ly + 8, Math.min(CW * .44f - 8, leftW), bottomOfLeft, hub.carousel, now, motion);
        if (dim > 0) cv.drawColor(Color.argb(Math.round(255 * dim * .35f), 0, 0, 0));
        cv.restore();
        return moving;
    }

    /** «٤٥٪ · يكفي ٨٠ كم», whatever of it the car gives */
    private static String fuelWords(CarData car, boolean latin) {
        String pct = Float.isNaN(car.fuelPercent) ? "" : Texts.digits(Math.round(car.fuelPercent) + (latin ? "%" : "٪"), latin);
        String range = Float.isNaN(car.rangeKm) ? "" : "يكفي " + km(car.rangeKm, latin);
        return pct.isEmpty() ? range : range.isEmpty() ? pct : pct + " · " + range;
    }

    /** a distance: one decimal under 10 km («٢٫٣ كم»), whole above */
    static String km(float km, boolean latin) {
        String n = km < 10 ? String.valueOf(Math.round(km * 10) / 10f) : String.valueOf(Math.round(km));
        if (n.endsWith(".0")) n = n.substring(0, n.length() - 2);
        return Texts.digits(latin ? n : n.replace('.', '٫'), latin) + " كم";
    }

    /** an amber triangle with «!», size wide, its middle at (x + size / 2, cy) */
    private void warningSign(Canvas cv, float x, float cy, float size) {
        clip.reset();
        clip.moveTo(x + size / 2, cy - size * .48f);
        clip.lineTo(x + size, cy + size * .42f);
        clip.lineTo(x, cy + size * .42f);
        clip.close();
        fill.setColor(AMBER);
        fill.setShadowLayer(6, 0, 1, 0x99000000);
        cv.drawPath(clip, fill);
        fill.clearShadowLayer();
        fill.setColor(0xFF1A1205);
        cv.drawRect(x + size * .45f, cy - size * .16f, x + size * .55f, cy + size * .16f, fill);
        cv.drawRect(x + size * .45f, cy + size * .23f, x + size * .55f, cy + size * .33f, fill);
        fill.setColor(Color.BLACK);
    }

    private void shadow(float radius) { text.setShadowLayer(radius, 0, 2, 0x99000000); }

    // ---------------------------------------------------------------- the media card (MediaService)

    /** the media card's three lines */
    static final class Lines {
        final String title, subtitle, more;
        Lines(String title, String subtitle, String more) { this.title = title; this.subtitle = subtitle; this.more = more; }
    }

    /**
     * The media card's picture, size x size: the photo, still, with the clock large in the middle and the phrase under it (a small
     * picture, so nothing else); and its lines: the time and the phrase, the next prayer (a weather warning first, and on a match day
     * the team's match unless the prayer is within the hour), the dates and the weather.
     */
    Lines card(Canvas cv, int size, Hub hub, long now) {
        Settings s = hub.settings;
        boolean latin = s.latinDigits();
        String tz = s.tz();
        float k = size / 480f, W = 480, H = 480;
        cv.save();
        cv.scale(k, k);
        background(cv, W, H, 0, W, hub, now, false);
        String[] c = Texts.clock(now, tz, s.clock24(), latin);
        String phrase = s.greetShown(Prayers.part(s, now));
        text.setTypeface(bold);
        text.setTextSize(150);
        float tw = text.measureText(c[0]);
        float size1 = 150 * Math.min(1, 420 / tw);
        text.setTextSize(size1);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(SAND);
        text.setShadowLayer(18, 0, 3, 0x88000000);
        cv.drawText(c[0], W / 2, 250, text);
        shadow(10);
        line(cv, phrase, W / 2, 330, medium, 40, GOLD, 420, Paint.Align.CENTER);
        text.clearShadowLayer();
        cv.restore();

        String title = c[0] + (c[1].isEmpty() ? "" : " " + c[1]) + " · " + phrase;
        Weather wx = hub.weather;
        boolean fresh = wx != null && wx.fresh(now);
        String warn = fresh && s.shows("warnings") ? wx.warning(latin) : "";
        String subtitle = warn;
        if (subtitle.isEmpty() && s.shows("fuel") && hub.car.hasFuel() && hub.car.low(s.fuelLimitKm())) {
            String amount = fuelWords(hub.car, latin);
            subtitle = "الوقود منخفض" + (amount.isEmpty() ? "" : " · " + amount);
        }
        Prayers.Next n = s.shows("prayer") ? Prayers.next(s, now) : null;
        String prayer = n == null ? "" : n.name + " " + Texts.clock12(n.at, tz, s.clock24(), latin) + " · " + Texts.remaining(n.at - now, latin);
        // the team's match on a day it plays (from 12 hours before), unless a prayer is less than an hour away
        Match m = s.shows("match") ? hub.match : null;
        boolean matchDay = m != null && m.at - now < 12 * 3600_000L && now < m.at + 2 * 3600_000L;
        if (subtitle.isEmpty() && matchDay && (n == null || n.at - now > 3600_000L))
            subtitle = m.home + " × " + m.away + " · " + Texts.dayWord(m.at, now, tz) + " " + Texts.clock12(m.at, tz, s.clock24(), latin);
        if (subtitle.isEmpty()) subtitle = prayer;
        String more = Texts.hijri(now, tz, s.hijriAdjust(), latin);
        if (fresh && s.shows("weather")) {
            String sky = Texts.skyWord(wx.code, wx.dust);
            more += " · " + Texts.digits(Math.round(wx.temp) + "°", latin) + (sky.isEmpty() ? "" : " " + sky);
        }
        return new Lines(title, subtitle, more);
    }

    // ---------------------------------------------------------------- the photo, as Bayti TV's page draws it

    /** the photo covers the whole surface, centred on the part the car shows (aL..aR) */
    private boolean background(Canvas cv, float W, float H, float aL, float aR, Hub hub, long now, boolean motion) {
        Hub.Deck d = hub.background;
        boolean on = hub.settings.shows("background") && d.cur != null;
        fill.setShader(new LinearGradient(0, 0, 0, H, 0xFF14291F, 0xFF030A07, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, fill);
        fill.setShader(null);
        boolean moving = false;
        if (on) {
            long age = now - d.shownAt;
            float fade = Math.min(1, age / (float) BG_FADE);
            float cx = (aL + aR) / 2;
            if (d.prev != null && fade < 1) photo(cv, d.prev, d.prevBright, W, H, cx, 1, 1);
            float settle = motion ? Math.min(1, age / (float) SETTLE) : 1;
            photo(cv, d.cur, d.curBright, W, H, cx, fade, settle);
            if (fade >= 1) d.prev = null;
            moving = fade < 1 || settle < 1;
        }
        // .veil: the vertical shade (lighter at the bottom than Bayti TV's: the corners' words sit on the photo) and a soft one at 22% 34%
        fill.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0x9E030A07, 0x4D030A07, 0x1F030A07, 0x40030A07, 0x8C020604},
                new float[]{0, .22f, .46f, .74f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, fill);
        cv.save();
        float aW = aR - aL;
        cv.translate(aL + .22f * aW, .34f * H);
        cv.scale(1.2f * aW, .85f * H);
        fill.setShader(new RadialGradient(0, 0, 1, new int[]{0x6B030E09, 0x00030E09}, new float[]{0, .58f}, Shader.TileMode.CLAMP));
        cv.drawRect(-1, -1, 1, 1, fill);
        cv.restore();
        // .grain
        cv.drawRect(0, 0, W, H, grain);
        // .top-veil at the weather's corner and .bottom-veil under the corners' words
        cv.save();
        cv.translate(aL, 0);
        cv.scale(.56f * aW, .34f * H);
        fill.setShader(new RadialGradient(0, 0, 1.2f, new int[]{0xD1020906, 0x6B020906, 0x00020906}, new float[]{0, .45f, .76f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, 1, 1, fill);
        cv.restore();
        fill.setShader(new LinearGradient(0, H * .72f, 0, H, new int[]{0x00020704, 0x40020704, 0x8C020604}, new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, H * .72f, W, H, fill);
        fill.setShader(null);
        return moving;
    }

    /**
     * cover, at 26% from the top, the photo's middle at x = cx where it can be; settle: 0 = the start of the drift (scale 1.055, a
     * little up and left), 1 = still at 1.03
     */
    private void photo(Canvas cv, Bitmap bm, float bright, float W, float H, float cx, float alpha, float settle) {
        float e = 1 - (float) Math.pow(1 - settle, 3);   // ease-out
        float scale = 1.055f - .025f * e, tx = -.0055f * (1 - e) * W, ty = -.004f * (1 - e) * H;
        cover(bm, W, H, .26f);
        float bs = W / src.width(), dx = Math.round(bm.getWidth() / 2f - cx / bs) - src.left;
        dx = Math.max(-src.left, Math.min(bm.getWidth() - src.right, dx));
        src.offset(Math.round(dx), 0);
        cv.save();
        cv.translate(W / 2 + tx, H / 2 + ty);
        cv.scale(scale, scale);
        cv.translate(-W / 2, -H / 2);
        fill.setAlpha(Math.round(255 * alpha));
        cv.drawBitmap(bm, src, r, fill);
        fill.setAlpha(255);
        fill.setColor(Color.argb(Math.round(255 * alpha * Math.max(0, Math.min(.6f, 1 - bright))), 0, 0, 0));
        cv.drawRect(0, 0, W, H, fill);
        fill.setColor(Color.BLACK);
        cv.restore();
    }

    /** src: the part of the photo that covers W x H, its top-to-bottom position at `fy`; r: 0,0,W,H */
    private void cover(Bitmap bm, float W, float H, float fy) {
        float bw = bm.getWidth(), bh = bm.getHeight(), s = Math.max(W / bw, H / bh);
        float cw = W / s, ch = H / s;
        float x = (bw - cw) / 2, y = (bh - ch) * fy;
        src.set(Math.round(x), Math.round(y), Math.round(x + cw), Math.round(y + ch));
        r.set(0, 0, W, H);
    }

    // ---------------------------------------------------------------- the carousel: Bayti TV's cross-fade and slow push-in, and its dots

    private boolean carousel(Canvas cv, float x, float top, float maxW, float bottom, Hub.Deck d, long now, boolean motion) {
        float h = Math.min(maxW * 9 / 16, bottom - top - 16);
        if (h < 60 || d.cur == null) return false;
        float w = h * 16 / 9;
        long age = now - d.shownAt;
        float fade = Math.min(1, age / (float) CAR_FADE);
        float push = motion ? Math.min(1, age / (float) PUSH) : 1;
        float pe = 1 - (1 - push) * (1 - push);   // decelerate
        cv.save();
        clip.reset();
        r.set(x, top, x + w, top + h);
        clip.addRoundRect(r, 14, 14, Path.Direction.CW);
        // its shadow, then the photos inside the rounded frame
        fill.setColor(0x66000000);
        fill.setShadowLayer(14, 0, 4, 0x99000000);
        cv.drawRoundRect(r, 14, 14, fill);
        fill.clearShadowLayer();
        cv.clipPath(clip);
        if (d.prev != null && fade < 1) inBox(cv, d.prev, d.prevBright, x, top, w, h, 1, 1);
        inBox(cv, d.cur, d.curBright, x, top, w, h, fade, 1.06f - .06f * pe);
        cv.restore();
        if (fade >= 1) d.prev = null;
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1);
        fill.setColor(0x40DCB86F);
        r.set(x, top, x + w, top + h);
        cv.drawRoundRect(r, 14, 14, fill);
        fill.setStyle(Paint.Style.FILL);
        // the dots: one per photo (12 at most, around the current one), the current one a gold dash
        int n = d.items.size();
        if (n > 1) {
            int shown = Math.min(n, 12), first = Math.max(0, Math.min(d.idx - shown / 2, n - shown));
            float dx = x + w / 2 - (shown * 6 + (shown - 1) * 5 + 12) / 2f, dy = top + h + 8;
            for (int i = first; i < first + shown; i++) {
                float dw = i == d.idx ? 18 : 6;
                fill.setColor(i == d.idx ? GOLD : 0x66FFFFFF);
                r.set(dx, dy, dx + dw, dy + 6);
                cv.drawRoundRect(r, 3, 3, fill);
                dx += dw + 5;
            }
        }
        return fade < 1 || push < 1;
    }

    private void inBox(Canvas cv, Bitmap bm, float bright, float x, float y, float w, float h, float alpha, float scale) {
        cover(bm, w, h, .5f);
        cv.save();
        cv.translate(x + w / 2, y + h / 2);
        cv.scale(scale, scale);
        cv.translate(-w / 2, -h / 2);
        fill.setColor(Color.BLACK);
        fill.setAlpha(Math.round(255 * alpha));
        cv.drawBitmap(bm, src, r, fill);
        fill.setColor(Color.argb(Math.round(255 * alpha * Math.max(0, Math.min(.5f, 1 - bright))), 0, 0, 0));
        cv.drawRect(0, 0, w, h, fill);
        cv.restore();
        fill.setColor(Color.BLACK);
    }

    // ---------------------------------------------------------------- the bottom corners

    /** the next prayer in the bottom right corner: a small title, the prayer and its time, how long until it */
    private void prayer(Canvas cv, float x, float b, float maxW, Prayers.Next n, Settings s, long now, boolean latin) {
        shadow(10);
        line(cv, "الصلاة القادمة", x, b - 52, regular, 14, DIM, maxW, Paint.Align.RIGHT);
        text.setTypeface(bold);
        text.setTextSize(26);
        float nameW = Math.min(maxW, text.measureText(n.name));
        line(cv, n.name, x, b - 24, bold, 26, GOLD, maxW, Paint.Align.RIGHT);
        line(cv, Texts.clock12(n.at, s.tz(), s.clock24(), latin), x - nameW - 12, b - 24, bold, 26, SAND, maxW - nameW - 12, Paint.Align.RIGHT);
        line(cv, Texts.remaining(n.at - now, latin), x, b - 2, regular, 15, GOLD_HI, maxW, Paint.Align.RIGHT);
        text.clearShadowLayer();
    }

    /** the team's next match in the bottom left corner: a small title, the badges and the sides (home on the right), when and where */
    private void match(Canvas cv, float x, float b, float maxW, Hub hub, long now, boolean latin) {
        Match m = hub.match;
        float bs = 28, gap = 8;
        shadow(10);
        line(cv, "المباراة القادمة", x, b - 52, regular, 14, DIM, maxW, Paint.Align.LEFT);
        String sides = m.home + " × " + m.away;
        text.setTypeface(bold);
        text.setTextSize(22);
        float room = maxW - 2 * (bs + gap);
        float tw = Math.min(room, text.measureText(sides));
        float tx = x + (hub.awayBadge != null ? bs + gap : 0), cy = b - 24 - 8;
        line(cv, sides, tx, b - 24, bold, 22, SAND, room, Paint.Align.LEFT);
        String tz = hub.settings.tz();
        String when = Texts.dayWord(m.at, now, tz) + " " + Texts.clock12(m.at, tz, hub.settings.clock24(), latin);
        line(cv, m.league + " · " + when, x, b - 2, regular, 15, GOLD_HI, maxW, Paint.Align.LEFT);
        text.clearShadowLayer();
        if (hub.awayBadge != null) badge(cv, hub.awayBadge, x, cy - bs / 2, bs);
        if (hub.homeBadge != null) badge(cv, hub.homeBadge, tx + tw + gap, cy - bs / 2, bs);
    }

    private void badge(Canvas cv, Bitmap bm, float x, float y, float size) {
        src.set(0, 0, bm.getWidth(), bm.getHeight());
        r.set(x, y, x + size, y + size);
        fill.setColor(Color.BLACK);
        cv.drawBitmap(bm, src, r, fill);
    }

    /** one line of text, cut with «…» when it is wider than maxW */
    private void line(Canvas cv, String s, float x, float y, Typeface face, float size, int color, float maxW, Paint.Align align) {
        if (s == null || s.isEmpty() || maxW <= 0) return;
        text.setTypeface(face);
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
        CharSequence cut = TextUtils.ellipsize(s, text, maxW, TextUtils.TruncateAt.END);
        cv.drawText(cut, 0, cut.length(), x, y, text);
    }
}
