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
import androidx.car.app.hardware.CarHardwareManager;
import androidx.car.app.hardware.common.CarValue;
import androidx.car.app.hardware.common.OnCarDataAvailableListener;
import androidx.car.app.hardware.info.CarInfo;
import androidx.car.app.hardware.info.EnergyLevel;
import androidx.car.app.hardware.info.Mileage;
import androidx.car.app.hardware.info.Model;
import androidx.car.app.hardware.info.Speed;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.Executor;

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
                listenToCar();
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                hub.remove(DashScreen.this);
                stopListening();
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

    // ---------------------------------------------------------------- «بيانات السيارة»: the car's own data (car API 3 and up)

    private CarInfo info;
    /** every answer goes to the hub's thread, which owns CarData */
    private final Executor onHub = this::onHub;

    private final OnCarDataAvailableListener<EnergyLevel> energy = this::onEnergy;
    private final OnCarDataAvailableListener<Speed> speed = this::onSpeed;
    private final OnCarDataAvailableListener<Mileage> mileage = this::onMileage;

    private void onHub(Runnable r) { hub.handler.post(r); }

    private void onEnergy(EnergyLevel e) {
        CarValue<Float> f = e.getFuelPercent(), r = e.getRangeRemainingMeters();
        CarValue<Boolean> low = e.getEnergyIsLow();
        boolean was = hub.car.low(hub.settings.fuelLimitKm());
        hub.car.energy(f.getStatus(), f.getValue(), r.getStatus(), r.getValue(), low.getStatus(), low.getValue());
        if (was != hub.car.low(hub.settings.fuelLimitKm()) || hub.settings.fuelAlways()) hub.invalidate();
    }

    private void onSpeed(Speed sp) {
        CarValue<Float> v = sp.getDisplaySpeedMetersPerSecond();
        if (v.getStatus() != CarValue.STATUS_SUCCESS) v = sp.getRawSpeedMetersPerSecond();
        long now = System.currentTimeMillis();
        boolean was = hub.car.moving(now);
        hub.car.speed(v.getStatus(), v.getValue(), now);
        if (was != hub.car.moving(now)) hub.invalidate();   // calm on, or off again
    }

    @SuppressWarnings("deprecation")
    private void onMileage(Mileage m) {
        CarValue<Float> v = m.getOdometerMeters();
        hub.car.mileage(v.getStatus(), v.getValue());
    }

    private void listenToCar() {
        if (getCarContext().getCarAppApiLevel() < 3) {
            hub.handler.post(() -> hub.car.refused("model", "Android Auto قديم"));
            return;
        }
        try {
            info = getCarContext().getCarService(CarHardwareManager.class).getCarInfo();
        } catch (RuntimeException e) {
            hub.handler.post(() -> hub.car.refused("model", e.getClass().getSimpleName()));
            return;
        }
        try {
            info.fetchModel(onHub, (Model m) -> hub.car.model(m.getName().getStatus(), m.getManufacturer().getValue(),
                    m.getName().getValue(), m.getYear().getValue()));
        } catch (RuntimeException e) { refused("model", e); }
        try { info.addEnergyLevelListener(onHub, energy); } catch (RuntimeException e) { refused("fuel", e); }
        try { info.addSpeedListener(onHub, speed); } catch (RuntimeException e) { refused("speed", e); }
        try { info.addMileageListener(onHub, mileage); } catch (RuntimeException e) { refused("odometer", e); }
    }

    private void refused(String item, RuntimeException e) {
        final String why = e instanceof SecurityException ? "لا إذن" : e.getClass().getSimpleName();
        hub.handler.post(() -> hub.car.refused(item, why));
    }

    private void stopListening() {
        if (info != null) {
            try { info.removeEnergyLevelListener(energy); } catch (RuntimeException ignored) { }
            try { info.removeSpeedListener(speed); } catch (RuntimeException ignored) { }
            try { info.removeMileageListener(mileage); } catch (RuntimeException ignored) { }
            info = null;
        }
        hub.handler.post(hub.car::clear);
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
