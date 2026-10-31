package sa.bayti.auto;

import android.content.Intent;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/** What Android Auto starts: one session, one screen (DashScreen). */
public final class CarService extends CarAppService {
    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // a personal app, installed by its owner: any Android Auto host may show it
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new DashScreen(getCarContext());
            }

            /** the car's day and night (its headlights): the screen dims at once */
            @Override
            public void onCarConfigurationChanged(@NonNull Configuration c) {
                Hub.get(getCarContext()).invalidate();
            }
        };
    }
}
