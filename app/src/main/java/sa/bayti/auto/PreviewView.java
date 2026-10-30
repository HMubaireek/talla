package sa.bayti.auto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/**
 * The car's screen on the phone, at Mazda's 800 x 480: drawn by the same Renderer on the hub's thread into a bitmap, shown here.
 * A tap moves the carousel on.
 */
final class PreviewView extends View implements Hub.Target {
    static final int W = 800, H = 480;
    private final Hub hub;
    private final Renderer renderer;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    /** drawn on the hub's thread into back, then swapped with front, which onDraw shows */
    private Bitmap front, back;

    PreviewView(Context c) {
        super(c);
        hub = Hub.get(c);
        renderer = new Renderer(c);
        setOnClickListener(v -> hub.nextCarousel());
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
        boolean moving = renderer.draw(new Canvas(back), W, H, null, hub, now);
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
