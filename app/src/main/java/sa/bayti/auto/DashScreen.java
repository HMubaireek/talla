package sa.bayti.auto;

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
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * The car's screen: a navigation template, which leaves the whole screen to the app's own drawing (the "map"), with two buttons the
 * knob can reach («التالي» for the carousel, «خلفية» for the background). Android Auto hides the buttons after a few seconds.
 * The drawing itself is Renderer's, on the surface the car gives, from the hub's thread.
 */
final class DashScreen extends Screen implements SurfaceCallback, Hub.Target {
    private final Hub hub;
    private final Renderer renderer;
    private Surface surface;
    private int width, height;
    private Rect stable;

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
        ActionStrip strip = new ActionStrip.Builder()
                .addAction(new Action.Builder().setTitle("التالي").setOnClickListener(hub::nextCarousel).build())
                .addAction(new Action.Builder().setTitle("خلفية").setOnClickListener(hub::nextBackground).build())
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

    @Override
    public void onStableAreaChanged(@NonNull Rect area) {
        final Rect a = new Rect(area);
        hub.handler.post(() -> stable = a);
        hub.invalidate();
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) { }

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
            return renderer.draw(cv, width, height, stable, hub, now);
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
