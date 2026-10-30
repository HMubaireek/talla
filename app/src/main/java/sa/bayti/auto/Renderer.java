package sa.bayti.auto;

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
 * Draws Bayti's screen on a canvas: the photo with Bayti TV's look (its slow settle, its darkness, the veil, the grain and the shades at
 * the corners), the dates and the clock on the right, the weather and the carousel on the left, and the cards of the next prayer and of
 * Al-Hilal's next match along the bottom. Laid out on a screen 480 units high (Mazda's 800 x 480 is 1:1) and as wide as the car's.
 * Only the hub's thread calls it.
 */
final class Renderer {
    static final int SAND = 0xFFF6EFE0, DIM = 0xFFC3B8A2, GOLD = 0xFFDCB86F, GOLD_HI = 0xFFFFE9B8;
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
     * One frame on a canvas w x h; area is the part of it the car keeps clear of its own buttons (null: all of it).
     * True while something moves (a fade, a settle, a push-in), so the hub draws the next frame soon.
     */
    boolean draw(Canvas cv, int w, int h, Rect area, Hub hub, long now) {
        Settings s = hub.settings;
        boolean latin = s.latinDigits(), motion = s.motion();
        float k = h / 480f, W = w / k, H = 480;
        cv.save();
        cv.scale(k, k);
        boolean moving = background(cv, W, H, hub, now, motion);

        // the content keeps to the area the car leaves clear, unless that is a sliver
        float L = 22, T = 18, R = W - 22, B = H - 16;
        if (area != null && area.width() > w * .6f && area.height() > h * .6f) {
            L = Math.max(L, area.left / k + 14); T = Math.max(T, area.top / k + 10);
            R = Math.min(R, area.right / k - 14); B = Math.min(B, area.bottom / k - 10);
        }
        String tz = s.tz();

        // ---- right: the dates, the clock and the phrase
        float y = T;
        if (s.shows("hijri")) {
            y += 24;
            line(cv, Texts.hijri(now, tz, s.hijriAdjust(), latin), R, y, medium, 22, SAND, R - L - 40, Paint.Align.RIGHT);
        }
        if (s.shows("greg")) {
            y += 22;
            line(cv, Texts.gregorian(now, tz, latin), R, y, light, 16, DIM, R - L - 40, Paint.Align.RIGHT);
        }
        if (s.shows("hijri") || s.shows("greg")) {
            y += 12;
            fill.setShader(new LinearGradient(R, 0, R - 110, 0, GOLD, GOLD & 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(R - 110, y, R, y + 1.2f, fill);
            fill.setShader(null);
        }
        float rightW = (R - L) * .52f;
        if (s.shows("clock")) {
            String[] c = Texts.clock(now, tz, s.clock24(), latin);
            y += 104;
            text.setTypeface(bold);
            text.setTextSize(112);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(SAND);
            text.setShadowLayer(18, 0, 3, 0x66000000);
            cv.drawText(c[0], R, y, text);
            if (!c[1].isEmpty()) {
                float cw = text.measureText(c[0]);
                text.setTypeface(medium);
                text.setTextSize(30);
                text.setColor(GOLD);
                cv.drawText(c[1], R - cw - 10, y, text);
            }
            text.clearShadowLayer();
        }
        if (s.shows("phrase")) {
            y += s.shows("clock") ? 46 : 40;
            int part = Prayers.part(s, now);
            text.setShadowLayer(10, 0, 2, 0x88000000);
            line(cv, s.greetShown(part), R, y, medium, 30, GOLD, rightW, Paint.Align.RIGHT);
            text.clearShadowLayer();
        }

        // ---- bottom: the cards
        float cardH = 80, cardTop = B - cardH;
        boolean prayer = s.shows("prayer"), hilal = s.shows("hilal") && hub.match != null;
        int cards = (prayer ? 1 : 0) + (hilal ? 1 : 0);
        if (cards > 0) {
            float gap = 12, cw = Math.min(330, ((R - L) - gap) / 2), x = R;
            if (prayer) { prayerCard(cv, x - cw, cardTop, x, B, s, now, latin); x -= cw + gap; }
            if (hilal) hilalCard(cv, x - cw, cardTop, x, B, hub, now, latin);
        }
        float bottomOfLeft = cards > 0 ? cardTop - 12 : B;

        // ---- left: the weather and the carousel
        float ly = T;
        Weather wx = hub.weather;
        if (s.shows("weather") && wx != null && wx.fresh(now)) {
            text.setShadowLayer(12, 0, 2, 0x88000000);
            String t = Texts.digits(Math.round(wx.temp) + "°", latin);
            text.setTypeface(bold);
            text.setTextSize(46);
            text.setColor(SAND);
            text.setTextAlign(Paint.Align.LEFT);
            cv.drawText(t, L, ly + 44, text);
            float tw = text.measureText(t);
            String sky = Texts.skyWord(wx.code, wx.dust);
            line(cv, sky.isEmpty() ? wx.place : sky, L + tw + 12, ly + 24, medium, 18, SAND, rightW, Paint.Align.LEFT);
            if (!sky.isEmpty()) line(cv, wx.place, L + tw + 12, ly + 44, light, 15, DIM, rightW, Paint.Align.LEFT);
            if (!Double.isNaN(wx.high) && !Double.isNaN(wx.low)) {
                line(cv, "العظمى " + Texts.digits(Math.round(wx.high) + "°", latin) + "  الصغرى " + Texts.digits(Math.round(wx.low) + "°", latin),
                        L, ly + 68, light, 14, DIM, rightW, Paint.Align.LEFT);
            }
            text.clearShadowLayer();
            ly += 82;
        }
        if (s.shows("carousel")) moving |= carousel(cv, L, ly + 8, (R - L) * .44f, bottomOfLeft, hub.carousel, now, motion);
        cv.restore();
        return moving;
    }

    // ---------------------------------------------------------------- the photo, as Bayti TV's page draws it

    private boolean background(Canvas cv, float W, float H, Hub hub, long now, boolean motion) {
        Hub.Deck d = hub.background;
        boolean on = hub.settings.shows("background") && d.cur != null;
        fill.setShader(new LinearGradient(0, 0, 0, H, 0xFF14291F, 0xFF030A07, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, fill);
        fill.setShader(null);
        boolean moving = false;
        if (on) {
            long age = now - d.shownAt;
            float fade = Math.min(1, age / (float) BG_FADE);
            if (d.prev != null && fade < 1) photo(cv, d.prev, d.prevBright, W, H, 1, 1);
            float settle = motion ? Math.min(1, age / (float) SETTLE) : 1;
            photo(cv, d.cur, d.curBright, W, H, fade, settle);
            if (fade >= 1) d.prev = null;
            moving = fade < 1 || settle < 1;
        }
        // .veil: the vertical shade and a soft one at 22% 34%
        fill.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0x9E030A07, 0x4D030A07, 0x1F030A07, 0x6B030A07, 0xE0020604},
                new float[]{0, .22f, .46f, .74f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, fill);
        cv.save();
        cv.translate(.22f * W, .34f * H);
        cv.scale(1.2f * W, .85f * H);
        fill.setShader(new RadialGradient(0, 0, 1, new int[]{0x6B030E09, 0x00030E09}, new float[]{0, .58f}, Shader.TileMode.CLAMP));
        cv.drawRect(-1, -1, 1, 1, fill);
        cv.restore();
        // .grain
        cv.drawRect(0, 0, W, H, grain);
        // .top-veil at the weather's corner and .bottom-veil under the cards
        cv.save();
        cv.scale(.56f * W, .34f * H);
        fill.setShader(new RadialGradient(0, 0, 1.2f, new int[]{0xD1020906, 0x6B020906, 0x00020906}, new float[]{0, .45f, .76f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, 1, 1, fill);
        cv.restore();
        fill.setShader(new LinearGradient(0, H * .7f, 0, H, new int[]{0x00020704, 0x8C020704, 0xDB020604}, new float[]{0, .42f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, H * .7f, W, H, fill);
        fill.setShader(null);
        return moving;
    }

    /** cover, centred at 26% from the top; settle: 0 = the start of the drift (scale 1.055, a little up and left), 1 = still at 1.03 */
    private void photo(Canvas cv, Bitmap bm, float bright, float W, float H, float alpha, float settle) {
        float e = 1 - (float) Math.pow(1 - settle, 3);   // ease-out
        float scale = 1.055f - .025f * e, tx = -.0055f * (1 - e) * W, ty = -.004f * (1 - e) * H;
        cover(bm, W, H, .26f);
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

    // ---------------------------------------------------------------- the carousel: Bayti's cross-fade and slow push-in, and its dots

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

    // ---------------------------------------------------------------- the cards

    private void card(Canvas cv, float l, float t, float rr, float b) {
        r.set(l, t, rr, b);
        fill.setColor(0x8C030A07);
        cv.drawRoundRect(r, 14, 14, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1);
        fill.setColor(0x33DCB86F);
        cv.drawRoundRect(r, 14, 14, fill);
        fill.setStyle(Paint.Style.FILL);
    }

    private void prayerCard(Canvas cv, float l, float t, float rr, float b, Settings s, long now, boolean latin) {
        Prayers.Next n = Prayers.next(s, now);
        if (n == null) return;
        card(cv, l, t, rr, b);
        float x = rr - 16, w = rr - l - 32;
        line(cv, "الصلاة القادمة", x, t + 22, regular, 14, DIM, w, Paint.Align.RIGHT);
        text.setTypeface(bold);
        text.setTextSize(25);
        float nameW = text.measureText(n.name);
        line(cv, n.name, x, t + 50, bold, 25, GOLD, w, Paint.Align.RIGHT);
        line(cv, Texts.clock12(n.at, s.tz(), s.clock24(), latin), x - nameW - 12, t + 50, bold, 25, SAND, w - nameW - 12, Paint.Align.RIGHT);
        line(cv, Texts.remaining(n.at - now, latin), x, t + 71, regular, 14, GOLD_HI, w, Paint.Align.RIGHT);
    }

    private void hilalCard(Canvas cv, float l, float t, float rr, float b, Hub hub, long now, boolean latin) {
        Hilal m = hub.match;
        card(cv, l, t, rr, b);
        float bs = 34, cy = t + 30;
        // the home side on the right, as Arabic reads
        if (hub.homeBadge != null) badge(cv, hub.homeBadge, rr - 14 - bs, cy - bs / 2, bs);
        if (hub.awayBadge != null) badge(cv, hub.awayBadge, l + 14, cy - bs / 2, bs);
        float mid = (l + rr) / 2, w = rr - l - 2 * (bs + 22);
        line(cv, m.home + " × " + m.away, mid, cy + 8, bold, 21, SAND, w, Paint.Align.CENTER);
        String tz = hub.settings.tz();
        String when = Texts.dayWord(m.at, now, tz) + " " + Texts.clock12(m.at, tz, hub.settings.clock24(), latin);
        line(cv, m.league + " · " + when, mid, t + 68, regular, 14, GOLD_HI, rr - l - 28, Paint.Align.CENTER);
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
