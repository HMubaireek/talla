package sa.talla;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Plain HTTPS GETs, off the main thread. */
final class Net {
    static final String UA = "Talla/2.0 (https://github.com/HMubaireek/talla)";

    private Net() { }

    static String text(String url) throws Exception {
        HttpURLConnection con = open(url);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    /** downloads to f through a .tmp beside it, so a cut download never looks like a photo */
    static boolean file(String url, File f) {
        File dir = f.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
        File tmp = new File(dir, f.getName() + ".tmp");
        try {
            HttpURLConnection con = open(url);
            try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                con.disconnect();
            }
            if (tmp.renameTo(f)) return true;
        } catch (Exception ignored) {
            // offline: the next turn tries again
        }
        tmp.delete();
        return false;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);
        con.setRequestProperty("User-Agent", UA);   // Wikimedia asks every client to say who it is
        return con;
    }
}
