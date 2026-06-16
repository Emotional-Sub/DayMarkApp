package com.example.daymark;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads habit photos into ImageViews off the main thread, downsampled to roughly the target view
 * size. The camera now stores full-resolution originals, so decoding them synchronously on the UI
 * thread (the old {@code ImageView.setImageURI}) janks the frame and risks OutOfMemoryError on the
 * full-screen preview. Every decode here goes through {@link AppExecutors#io()} and applies the
 * result back on the main thread, guarded against view recycling via a tag.
 *
 * <p>Includes an LruCache to avoid re-decoding the same image on RecyclerView scroll, with memory
 * management callbacks to respond to system memory pressure.
 */
public final class ImageLoader {
    /** Memory cache sized to ~1/8 of available heap (a reasonable default for image-heavy apps). */
    private static final LruCache<String, Bitmap> memoryCache;

    static {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024; // Size in KB
            }
        };
        Logger.d("ImageLoader cache initialized: " + (cacheSize / 1024) + " MB");
    }

    private ImageLoader() {
    }

    /**
     * Handle system memory pressure by trimming the image cache accordingly.
     * Call this from your Application or Activity's onTrimMemory() callback.
     *
     * @param level the memory trim level from ComponentCallbacks2
     */
    public static void onTrimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // App is in the middle of the LRU list; trim to half size
            Logger.w("Memory pressure MODERATE, trimming image cache to 50%");
            trimCacheToPercent(50);
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Device is running low on memory; trim to 25%
            Logger.w("Memory pressure RUNNING_LOW, trimming image cache to 25%");
            trimCacheToPercent(25);
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            // App is first to be killed; clear everything
            Logger.w("Memory pressure COMPLETE, clearing image cache");
            clearCache();
        }
    }

    /**
     * Trim the cache to the specified percentage of its max size.
     */
    private static void trimCacheToPercent(int percent) {
        int currentSize = memoryCache.size();
        int targetSize = memoryCache.maxSize() * percent / 100;
        if (currentSize > targetSize) {
            memoryCache.trimToSize(targetSize);
            Logger.d("Cache trimmed from " + currentSize + " KB to " + memoryCache.size() + " KB");
        }
    }

    /**
     * Manually clear the entire image cache. Useful when the user logs out or
     * when responding to extreme memory pressure.
     */
    public static void clearCache() {
        memoryCache.evictAll();
        Logger.d("Image cache cleared");
    }

    /**
     * Get current cache statistics for debugging.
     */
    public static String getCacheStats() {
        return String.format("Cache: %d/%d KB (%.1f%% full)",
                memoryCache.size(),
                memoryCache.maxSize(),
                memoryCache.size() * 100.0f / memoryCache.maxSize());
    }

    /**
     * Decode {@code imageUri} downsampled to about {@code targetPx} and set it on {@code imageView}.
     * The view is tagged with the uri it's loading (key {@link R.id#photoView}); when the background
     * decode finishes, the bitmap is applied only if the view is still waiting on that same uri, so
     * a recycled RecyclerView row never shows a stale image. {@code targetPx <= 0} means full size.
     * Results are cached in memory to avoid redundant decoding on scroll.
     */
    public static void load(ImageView imageView, String imageUri, int targetPx) {
        final Context context = imageView.getContext().getApplicationContext();
        imageView.setTag(R.id.photoView, imageUri);

        // Check memory cache first (key includes target size to avoid mixing thumbnail/full-size).
        String cacheKey = imageUri + ":" + targetPx;
        Bitmap cached = memoryCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setImageDrawable(null);
        final Uri uri = Uri.parse(imageUri);
        AppExecutors.io().execute(() -> {
            Bitmap bitmap = decodeSampled(context, uri, targetPx);
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap);
            }
            AppExecutors.main().execute(() -> {
                if (imageUri.equals(imageView.getTag(R.id.photoView))) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    /**
     * Decode {@code uri} downsampled so its smaller dimension stays >= {@code targetPx} (no
     * downsampling when {@code targetPx <= 0}). Returns null on a missing file, revoked uri
     * permission, or any decode failure, so callers just show nothing.
     */
    static Bitmap decodeSampled(Context context, Uri uri, int targetPx) {
        try {
            int sample = 1;
            if (targetPx > 0) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int half = Math.min(bounds.outWidth, bounds.outHeight) / 2;
                while (half / sample >= targetPx) {
                    sample *= 2;
                }
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (IOException | RuntimeException e) {
            // Missing file, revoked uri permission, or a decode failure: show nothing.
            return null;
        }
    }
}
