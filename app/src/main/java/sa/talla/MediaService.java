package sa.talla;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import java.util.Collections;
import java.util.List;

/**
 * «بطاقة الوسائط»: Talla as a media app too, off unless switched on in the settings (the service is disabled otherwise, so Android
 * Auto doesn't list it). Android Auto's split view keeps the big panel for one navigation app (Google Maps) and shows the media
 * app's card beside it; this card's picture is the photo with the clock, its lines the time and the phrase, the next prayer (or a
 * weather warning), the dates and the weather. It plays no sound. «التالي» on the card moves to the next background.
 *
 * Its picture is drawn by the hub like the other screens, but at most every 20 seconds and on the minute: a media card is not
 * animated.
 */
public final class MediaService extends MediaBrowserService implements Hub.Target {
    private static final int ART = 360;
    private static final long EVERY = 20_000;

    private Hub hub;
    private Renderer renderer;
    private MediaSession session;
    private long drawnAt, drawnMinute = -1;

    /** switches the service (and so Android Auto's media entry) on or off; Android Auto may need a reconnect to see the change */
    static void enable(Context c, boolean on) {
        c.getPackageManager().setComponentEnabledSetting(new ComponentName(c, MediaService.class),
                on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        hub = Hub.get(this);
        renderer = new Renderer(this);
        session = new MediaSession(this, "talla");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { setPlaying(true); }
            @Override public void onPlayFromMediaId(String id, Bundle extras) { setPlaying(true); }
            @Override public void onPause() { setPlaying(false); }
            @Override public void onStop() { setPlaying(false); }
            @Override public void onSkipToNext() { hub.nextBackground(); }
        });
        setPlaying(true);
        session.setActive(true);
        setSessionToken(session.getSessionToken());
        hub.add(this, ART, ART);
    }

    @Override
    public void onDestroy() {
        hub.remove(this);
        hub.trimLater();
        session.setActive(false);
        session.release();
        super.onDestroy();
    }

    private void setPlaying(boolean on) {
        session.setPlaybackState(new PlaybackState.Builder()
                .setState(on ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, on ? 1 : 0)
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID)
                .build());
    }

    // ---------------------------------------------------------------- browsing: one item, the card itself

    @Override
    public BrowserRoot onGetRoot(String client, int uid, Bundle hints) {
        return new BrowserRoot("root", null);   // a personal app: any client may see it
    }

    @Override
    public void onLoadChildren(String parent, Result<List<MediaBrowser.MediaItem>> result) {
        if (!"root".equals(parent)) { result.sendResult(Collections.<MediaBrowser.MediaItem>emptyList()); return; }
        MediaDescription d = new MediaDescription.Builder().setMediaId("talla").setTitle(getString(R.string.app_name)).build();
        result.sendResult(Collections.singletonList(new MediaBrowser.MediaItem(d, MediaBrowser.MediaItem.FLAG_PLAYABLE)));
    }

    // ---------------------------------------------------------------- Hub.Target (the hub's thread)

    @Override
    public boolean frame(long now) {
        long minute = now / 60_000;
        if (now - drawnAt < EVERY && minute == drawnMinute) return false;
        // a new background is still fading in: draw once it has
        long fadeLeft = hub.background.shownAt + 2300 - now;
        if (fadeLeft > 0) {
            hub.handler.postDelayed(hub::invalidate, fadeLeft);
            return false;
        }
        drawnAt = now;
        drawnMinute = minute;
        Bitmap art = Bitmap.createBitmap(ART, ART, Bitmap.Config.ARGB_8888);
        Renderer.Lines lines = renderer.card(new Canvas(art), ART, hub, now);
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "talla")
                .putString(MediaMetadata.METADATA_KEY_TITLE, lines.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, lines.subtitle)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, lines.more)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, lines.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, lines.subtitle)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art)
                .build());
        return false;
    }

    @Override
    public Context context() { return this; }
}
