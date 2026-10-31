package sa.talla;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/**
 * The car's screen on the phone, at Mazda's 800 x 480: drawn by the same Renderer on the hub's thread into a bitmap, shown here.
 * A tap moves the carousel on. In split mode it shows the screen as Android Auto's split view does: the app in the left two thirds
 * (its visible area), Android Auto's own cards over the rest.
 */
final class PreviewView extends View implements Hub.Target {
    static final int W = 800, H = 480;
    private final Hub hub;
    private final Renderer renderer;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    /** Android Auto's split view, as the Mazda showed it: the app's panel is about two thirds of the screen */
    private final Rect splitArea = new Rect(0, 0, W * 66 / 100, H);
    private final Paint side = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile boolean split, night;
    /** drawn on the hub's thread into back, then swapped with front, which onDraw shows */
    private Bitmap front, back;

    PreviewView(Context c) {
        super(c);
        hub = Hub.get(c);
        renderer = new Renderer(c);
        setOnClickListener(v -> hub.nextCarousel());
    }

    void setSplit(boolean on) {
        split = on;
        hub.invalidate();
    }

    /** as the car shows it in its night mode (the dimming follows «تخفيف الإضاءة») */
    void setNight(boolean on) {
        night = on;
        hub.invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w * H / W);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        hub.add(this, W, H);
    }

    @Override
    protected void onDetachedFromWindow() {
        hub.remove(this);
        hub.trimLater();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean frame(long now) {
        if (back == null) back = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(back);
        float dim = hub.settings.dim(night);
        boolean moving = renderer.draw(cv, W, H, split ? splitArea : null, dim, hub, now);
        if (split) {
            side.setColor(0xFF101418);
            cv.drawRect(splitArea.right, 0, W, H, side);
            side.setColor(0xFF2A2F36);
            float x = splitArea.right + 10, gap = 10, cardH = (H - 3 * gap) / 2f;
            cv.drawRoundRect(x, gap, W - gap, gap + cardH, 18, 18, side);
            cv.drawRoundRect(x, 2 * gap + cardH, W - gap, H - gap, 18, 18, side);
        }
        synchronized (this) {
            Bitmap b = front;
            front = back;
            back = b;
        }
        postInvalidate();
        return moving;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        synchronized (this) {
            if (front == null) return;
            dst.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(front, null, dst, paint);
        }
    }

    @Override
    public Context context() { return getContext(); }
}
