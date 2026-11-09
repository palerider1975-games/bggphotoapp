package ca.patrick.bgglogger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads cover pictures from BGG, keeps them on disk and in memory. */
final class ImageLoader {
    private static ImageLoader instance;

    private final File dir;
    private final LruCache<String, Bitmap> memory;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    static synchronized ImageLoader get(Context c) {
        if (instance == null) instance = new ImageLoader(c.getApplicationContext());
        return instance;
    }

    private ImageLoader(Context c) {
        dir = new File(c.getCacheDir(), "covers");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        memory = new LruCache<String, Bitmap>(kb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    /** Shows the picture at url in view, scaled to about maxPx wide. */
    void load(String url, ImageView view, int maxPx, int placeholderRes) {
        String key = url + "@" + maxPx;
        view.setTag(key);
        if (url == null || url.isEmpty()) {
            view.setImageResource(placeholderRes);
            return;
        }
        Bitmap cached = memory.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(placeholderRes);
        pool.execute(() -> {
            Bitmap bmp = fetch(url, maxPx);
            if (bmp != null) memory.put(key, bmp);
            main.post(() -> {
                if (bmp != null && key.equals(view.getTag())) view.setImageBitmap(bmp);
            });
        });
    }

    private Bitmap fetch(String url, int maxPx) {
        try {
            File f = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
            if (!f.exists() || f.length() == 0) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", Http.USER_AGENT);
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxPx) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), opts);
        } catch (Exception e) {
            return null;
        }
    }
}
