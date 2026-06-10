package com.example.daymark;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads habit photos into ImageViews off the main thread, downsampled to roughly the target view
 * size. The camera now stores full-resolution originals, so decoding them synchronously on the UI
 * thread (the old {@code ImageView.setImageURI}) janks the frame and risks OutOfMemoryError on the
 * full-screen preview. Every decode here goes through {@link AppExecutors#io()} and applies the
 * result back on the main thread, guarded against view recycling via a tag.
 */
public final class ImageLoader {
    private ImageLoader() {
    }

    /**
     * Decode {@code imageUri} downsampled to about {@code targetPx} and set it on {@code imageView}.
     * The view is tagged with the uri it's loading (key {@link R.id#photoView}); when the background
     * decode finishes, the bitmap is applied only if the view is still waiting on that same uri, so
     * a recycled RecyclerView row never shows a stale image. {@code targetPx <= 0} means full size.
     */
    public static void load(ImageView imageView, String imageUri, int targetPx) {
        final Context context = imageView.getContext().getApplicationContext();
        imageView.setTag(R.id.photoView, imageUri);
        imageView.setImageDrawable(null);
        final Uri uri = Uri.parse(imageUri);
        AppExecutors.io().execute(() -> {
            Bitmap bitmap = decodeSampled(context, uri, targetPx);
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
