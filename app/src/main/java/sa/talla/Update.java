package sa.talla;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updating from the phone alone, without the laptop: GitHub builds the APK (the apk-latest release, whose notes say its
 * versionCode), the phone's download manager saves it in Download, and UnknownAuto installs it with Shizuku started over wireless
 * debugging.
 */
final class Update {
    static final String APK = "https://github.com/HMubaireek/talla/releases/download/apk-latest/talla.apk";
    private static final String RELEASE = "https://api.github.com/repos/HMubaireek/talla/releases/tags/apk-latest";
    static final String SHIZUKU = "moe.shizuku.privileged.api";

    private Update() { }

    /** this install's versionCode */
    @SuppressWarnings("deprecation")
    static long installed(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    /** this install's version name ("2.0") */
    static String installedName(Context c) {
        try {
            String n = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return n == null ? "" : n;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** the newest build, from the release's notes («versionCode: N» and «versionName: 2.x»); code -1 when it can't be read */
    static final class Latest {
        final long code;
        final String name;
        Latest(long code, String name) { this.code = code; this.name = name; }
    }

    /** Blocking. */
    static Latest latest() {
        try {
            String body = new JSONObject(Net.text(RELEASE)).optString("body", "");
            Matcher m = Pattern.compile("versionCode:\\s*(\\d+)").matcher(body);
            Matcher n = Pattern.compile("versionName:\\s*([0-9][0-9.]*)").matcher(body);
            return new Latest(m.find() ? Long.parseLong(m.group(1)) : -1, n.find() ? n.group(1) : "");
        } catch (Exception e) {
            return new Latest(-1, "");
        }
    }

    /** saves the newest APK in Download as talla-<version>.apk (a new name each time: the phone won't overwrite); its id */
    static long download(Context c, Latest latest) {
        String v = !latest.name.isEmpty() ? latest.name : latest.code > 0 ? String.valueOf(latest.code) : String.valueOf(System.currentTimeMillis() / 1000);
        String name = "talla-" + v + ".apk";
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(APK))
                .setTitle(name)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        return ((DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
    }

    /** a download's state: DownloadManager.STATUS_*, or 0 when it is gone */
    static int status(Context c, long id) {
        try (Cursor q = ((DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE)).query(new DownloadManager.Query().setFilterById(id))) {
            return q != null && q.moveToFirst() ? q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) : 0;
        }
    }

    /** opens an app by its package, or else the first one whose name, without spaces, contains `named`; false when none is found */
    static boolean open(Context c, String pkg, String named) {
        PackageManager pm = c.getPackageManager();
        Intent i = pkg == null ? null : pm.getLaunchIntentForPackage(pkg);
        if (i == null && named != null) {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
                String label = String.valueOf(r.loadLabel(pm)).replace(" ", "").toLowerCase(Locale.ROOT);
                if (label.contains(named)) { i = pm.getLaunchIntentForPackage(r.activityInfo.packageName); break; }
            }
        }
        if (i == null) return false;
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }
}
