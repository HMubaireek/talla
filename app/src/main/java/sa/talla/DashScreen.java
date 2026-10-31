package sa.talla;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * The car's screen: a navigation template, which leaves the whole screen to the app's own drawing (the "map"). The template must have
 * an action strip with one action at least; ours is a single pale icon (the next background), which Android Auto hides after a few
 * seconds and brings back on a touch or the knob. It floats over the drawing and takes no room from it. The drawing itself is
 * Renderer's, on the surface the car gives, from the hub's thread, dimmed as «تخفيف الإضاءة» says (auto: with the car's night mode).
 */
final class DashScreen extends Screen implements SurfaceCallback, Hub.Target {
    private final Hub hub;
    private final Renderer renderer;
    private Surface surface;
    private int width, height;
    /** the part of the surface the car shows uncovered (in split view, the app's panel); null until the car says */
    private Rect visible;

    DashScreen(@NonNull CarContext carContext) {
        super(carContext);
        hub = Hub.get(carContext);
        renderer = new Renderer(carContext);
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onCreate(@NonNull LifecycleOwner owner) {
                carContext.getCarService(AppManager.class).setSurfaceCallback(DashScreen.this);
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                hub.remove(DashScreen.this);
                hub.trimLater();
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarIcon next = new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), R.drawable.ic_next_photo)).build();
        ActionStrip strip = new ActionStrip.Builder()
                .addAction(new Action.Builder().setIcon(next).setOnClickListener(hub::nextBackground).build())
                .build();
        return new NavigationTemplate.Builder().setActionStrip(strip).build();
    }

    // ---------------------------------------------------------------- the surface (the car's binder thread: handed to the hub's)

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer c) {
        final Surface s = c.getSurface();
        final int w = c.getWidth(), h = c.getHeight();
        hub.handler.post(() -> { surface = s; width = w; height = h; });
        hub.add(this, w, h);
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer c) {
        hub.remove(this);
        hub.handler.post(() -> surface = null);
    }

    /** the stable area leaves out the action strip's band at the top, which moved the whole layout; only the visible area is used */
    @Override
    public void onStableAreaChanged(@NonNull Rect area) { }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) {
        final Rect a = new Rect(area);
        hub.handler.post(() -> visible = a);
        hub.invalidate();
    }

    /** a swipe on the screen (parked, on a touch screen): the carousel's next photo */
    @Override
    public void onFling(float velocityX, float velocityY) {
        if (Math.abs(velocityX) > Math.abs(velocityY)) hub.nextCarousel();
    }

    // ---------------------------------------------------------------- Hub.Target

    @Override
    public boolean frame(long now) {
        Surface s = surface;
        if (s == null || !s.isValid() || width <= 0) return false;
        Canvas cv = null;
        try {
            cv = s.lockHardwareCanvas();
            float dim = hub.settings.dim(getCarContext().isDarkMode());
            return renderer.draw(cv, width, height, visible, dim, hub, now);
        } catch (RuntimeException e) {
            return false;   // the surface went while drawing: the next one comes with onSurfaceAvailable
        } finally {
            if (cv != null) {
                try { s.unlockCanvasAndPost(cv); } catch (RuntimeException ignored) { }
            }
        }
    }

    @Override
    public Context context() { return getCarContext(); }
}
