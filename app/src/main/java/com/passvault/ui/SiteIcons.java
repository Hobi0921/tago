package com.passvault.ui; // TODO: change to your package

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.passvault.R; // TODO: your R

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Real website icons for vault entries, fetched straight from each site.
 *
 * Privacy model (this is a password manager, so it matters):
 *  - Requests go only to the entry's own domain over HTTPS. No third-party icon service, so no
 *    one else learns which sites you have accounts on. The site itself only sees an anonymous
 *    icon request: no cookies, no referrer, generic user agent.
 *  - Results are cached on the device (30 days, or 7 days for "no icon"), so each site is
 *    contacted rarely, not on every scroll.
 *  - Users can turn it off in Settings; turning it off also deletes the cache.
 *
 * Quality: prefers the 180px apple-touch-icon; a favicon smaller than 32px is rejected, because a
 * blurry logo looks worse than a crisp initial.
 */
public final class SiteIcons {

    public interface OnIcon {
        /**
         * Main thread. brandColor is the logo's dominant colour, for a soft circle behind it.
         * arrivedLater is true when the logo came from disk/network after the row was shown
         * (fade it in); false when it was already in memory (show instantly, no flicker on scroll).
         */
        void onIcon(Bitmap icon, int brandColor, boolean arrivedLater);
    }

    private static final String PREFS = "site_icons";
    private static final String KEY_ENABLED = "enabled";
    private static final long POSITIVE_TTL_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long NEGATIVE_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MIN_PX = 32;
    private static final Pattern HOST =
            Pattern.compile("^(?=.{4,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final String[] PATHS = {
            "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/favicon.ico"
    };

    private static final class Entry {
        final Bitmap bitmap; final int brand;
        Entry(Bitmap b, int c) { bitmap = b; brand = c; }
    }

    private static final LruCache<String, Entry> MEMORY = new LruCache<String, Entry>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Entry e) { return e.bitmap.getByteCount(); }
    };
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SiteIcons() {}

    // ------------------------------------------------------------------ settings

    public static boolean enabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, on).apply();
        if (!on) clearCache(ctx);
    }

    public static void clearCache(Context ctx) {
        MEMORY.evictAll();
        MISSING.clear();
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            File[] files = dir(app).listFiles();
            if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
                f.delete();
        });
    }

    // ------------------------------------------------------------------ loading

    /**
     * Load the icon for an entry's domain into {@code target}. Safe in RecyclerView: if the view
     * gets rebound to another entry before the download finishes, the stale result is dropped.
     *
     * @param rawDomain the entry's domain field ("paypal.com", "https://www.paypal.com/ph", ...)
     */
    public static void load(ImageView target, String rawDomain, OnIcon onIcon) {
        String host = normalizeHost(rawDomain);
        target.setTag(R.id.tag_site_icon, host);
        if (host == null || !enabled(target.getContext())) return;

        Entry cached = MEMORY.get(host);
        if (cached != null) {
            onIcon.onIcon(cached.bitmap, cached.brand, false);
            return;
        }
        if (MISSING.contains(host)) return;

        Context app = target.getContext().getApplicationContext();
        IO.execute(() -> {
            Entry e = MEMORY.get(host);
            if (e == null && IN_FLIGHT.add(host)) {
                try {
                    e = obtain(app, host);
                } finally {
                    IN_FLIGHT.remove(host);
                }
            } else if (e == null) {
                // Another row is already fetching this host; wait briefly for it.
                for (int i = 0; i < 40 && e == null && IN_FLIGHT.contains(host); i++) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { return; }
                    e = MEMORY.get(host);
                }
            }
            final Entry result = e;
            if (result == null) return;
            MAIN.post(() -> {
                if (host.equals(target.getTag(R.id.tag_site_icon))) {
                    onIcon.onIcon(result.bitmap, result.brand, true);
                }
            });
        });
    }

    private static Entry obtain(Context app, String host) {
        File png = new File(dir(app), key(host) + ".png");
        File none = new File(dir(app), key(host) + ".none");
        long now = System.currentTimeMillis();

        if (png.exists() && now - png.lastModified() < POSITIVE_TTL_MS) {
            Bitmap b = BitmapFactory.decodeFile(png.getAbsolutePath());
            if (b != null) return remember(host, b);
        }
        if (none.exists() && now - none.lastModified() < NEGATIVE_TTL_MS) {
            MISSING.add(host);
            return null;
        }

        Bitmap b = download(host);
        try {
            if (b != null) {
                try (FileOutputStream out = new FileOutputStream(png)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                //noinspection ResultOfMethodCallIgnored
                none.delete();
                return remember(host, b);
            } else {
                //noinspection ResultOfMethodCallIgnored
                none.createNewFile();
                //noinspection ResultOfMethodCallIgnored
                none.setLastModified(now);
            }
        } catch (Exception ignored) {
            // Cache write failures only cost a re-download later.
        }
        MISSING.add(host);
        return null;
    }

    private static Entry remember(String host, Bitmap b) {
        Entry e = new Entry(b, brandColor(b));
        MEMORY.put(host, e);
        return e;
    }

    private static Bitmap download(String host) {
        for (String path : PATHS) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL("https://" + host + path).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setInstanceFollowRedirects(true);   // never downgrades HTTPS to HTTP
                c.setUseCaches(false);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)");
                c.setRequestProperty("Accept", "image/png,image/x-icon,image/*;q=0.8");
                if (c.getResponseCode() != 200) continue;
                if (!"https".equals(c.getURL().getProtocol())) continue;
                if (c.getContentLength() > MAX_BYTES) continue;
                String type = c.getContentType();
                if (type != null && !type.startsWith("image/")
                        && !type.startsWith("application/octet-stream")) continue;

                byte[] bytes = readCapped(c.getInputStream());
                if (bytes == null) continue;
                Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (b != null && Math.min(b.getWidth(), b.getHeight()) >= MIN_PX) return b;
            } catch (Exception ignored) {
                // Offline, TLS failure, timeout: try the next candidate, then fall back to initials.
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    private static byte[] readCapped(InputStream in) throws Exception {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = is.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) return null;
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ brand colour

    /**
     * The logo's dominant saturated colour (Keep → yellow, PayPal → blue), ignoring white,
     * black and transparent pixels. Avatars blends it heavily toward the surface for the circle.
     */
    private static int brandColor(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 24, 24, true);
        double r = 0, g = 0, b = 0, weight = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int p = s.getPixel(x, y);
                if (Color.alpha(p) < 160) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue;   // greys, white, black
                double w = hsv[1] * hsv[2];
                r += Color.red(p) * w; g += Color.green(p) * w; b += Color.blue(p) * w;
                weight += w;
            }
        }
        if (s != src) s.recycle();
        if (weight == 0) return Color.GRAY;
        return Color.rgb((int) (r / weight), (int) (g / weight), (int) (b / weight));
    }

    // ------------------------------------------------------------------ helpers

    /** "https://www.PayPal.com/ph?x" → "www.paypal.com"; null for anything not a public hostname. */
    public static String normalizeHost(String raw) {
        if (raw == null) return null;
        String h = raw.trim().toLowerCase(Locale.ROOT);
        int scheme = h.indexOf("://");
        if (scheme >= 0) h = h.substring(scheme + 3);
        int cut = indexOfAny(h, "/?#");
        if (cut >= 0) h = h.substring(0, cut);
        int at = h.lastIndexOf('@');
        if (at >= 0) h = h.substring(at + 1);
        int port = h.indexOf(':');
        if (port >= 0) h = h.substring(0, port);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        try {
            h = IDN.toASCII(h);
        } catch (IllegalArgumentException e) {
            return null;
        }
        // Hostnames only: no IP literals, no "localhost", no single-label LAN names.
        return HOST.matcher(h).matches() ? h : null;
    }

    private static int indexOfAny(String s, String chars) {
        int best = -1;
        for (char ch : chars.toCharArray()) {
            int i = s.indexOf(ch);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    private static String key(String host) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(host.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format(Locale.ROOT, "%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(host.hashCode());
        }
    }

    private static File dir(Context app) {
        File d = new File(app.getCacheDir(), "site_icons");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
