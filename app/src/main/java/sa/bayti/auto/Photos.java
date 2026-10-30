package sa.bayti.auto;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The photos of the background and of the carousel: the chosen sources' lists (PhotoSources) and the owner's folder on the phone,
 * each photo downloaded once into files/photos (the newest 80 kept) and decoded at about the size it is drawn, off the main thread.
 */
final class Photos {
    /** one photo: its address (https or the owner's content uri) and how bright it may show (Bayti TV's d) */
    static final class Item {
        final String url;
        final float bright;
        Item(String url, float bright) { this.url = url; this.bright = bright; }
    }

    private Photos() { }

    /** Blocking. The chosen sources' photos, shuffled (a day's order is the same all day), the owner's own first among them. */
    static List<Item> list(Context c, List<String> sources, String ownFolder) {
        List<Item> out = new ArrayList<>();
        JSONArray a = PhotoSources.index(c, sources);
        for (int i = 0; i < a.length(); i++) {
            JSONObject p = a.optJSONObject(i);
            if (p != null) out.add(new Item(p.optString("f"), (float) p.optDouble("d", 0.95)));
        }
        if (sources.contains(Settings.OWN)) out.addAll(own(c, ownFolder));
        Collections.shuffle(out, new java.util.Random(System.currentTimeMillis() / 86_400_000L));
        return out;
    }

    /** the images of the owner's folder (and one level of folders under it) */
    static List<Item> own(Context c, String tree) {
        List<Item> out = new ArrayList<>();
        if (tree == null || tree.isEmpty()) return out;
        try {
            Uri t = Uri.parse(tree);
            children(c, t, DocumentsContract.getTreeDocumentId(t), out, 1);
        } catch (Exception ignored) {
            // the folder is gone or its permission was taken back: no own photos
        }
        return out;
    }

    private static void children(Context c, Uri tree, String docId, List<Item> out, int depth) {
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cur = c.getContentResolver().query(kids, cols, null, null, null)) {
            while (cur != null && cur.moveToNext() && out.size() < 500) {
                String id = cur.getString(0), mime = cur.getString(1);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (depth > 0) children(c, tree, id, out, depth - 1);
                } else if (mime != null && (mime.equals("image/jpeg") || mime.equals("image/png") || mime.equals("image/webp"))) {
                    out.add(new Item(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), 0.95f));
                }
            }
        }
    }

    /** Blocking: the photo at about w x h (never smaller than it, so a cover crop stays sharp), or null. */
    static Bitmap bitmap(Context c, String url, int w, int h) {
        try {
            if (url.startsWith("content://")) {
                Uri u = Uri.parse(url);
                return decode(() -> c.getContentResolver().openInputStream(u), w, h);
            }
            if (!PhotoSources.allowed(url) && !url.startsWith("https://r2.thesportsdb.com/") && !url.startsWith("https://www.thesportsdb.com/")) return null;
            File dir = new File(c.getFilesDir(), "photos");
            File f = new File(dir, name(url));
            if (!f.isFile()) {
                if (!Net.file(url, f)) return null;
                prune(dir);
            } else {
                f.setLastModified(System.currentTimeMillis());
            }
            return decode(() -> new FileInputStream(f), w, h);
        } catch (Throwable e) {
            return null;
        }
    }

    /** a file name for an address: its hash and the end of it */
    private static String name(String url) {
        String tail = url.replaceAll("[^A-Za-z0-9._-]", "_");
        if (tail.length() > 40) tail = tail.substring(tail.length() - 40);
        return Integer.toHexString(url.hashCode()) + "_" + tail + (tail.endsWith(".jpg") ? "" : ".img");
    }

    private static void prune(File dir) {
        File[] all = dir.listFiles((d, n) -> !n.endsWith(".tmp"));
        if (all == null || all.length <= 80) return;
        Arrays.sort(all, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
        for (int i = 80; i < all.length; i++) all[i].delete();
    }

    private interface Source { InputStream open() throws java.io.IOException; }

    private static Bitmap decode(Source s, int w, int h) throws java.io.IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = s.open()) { if (in == null) return null; BitmapFactory.decodeStream(in, null, o); }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        try (InputStream in = s.open()) { return in == null ? null : BitmapFactory.decodeStream(in, null, o); }
    }
}
