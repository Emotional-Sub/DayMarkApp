package com.example.daymark;

import android.app.Activity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.ImageView;

public class ImagePreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        ImageView fullImage = findViewById(R.id.fullImage);
        Button closeButton = findViewById(R.id.closeButton);
        String uri = getIntent().getStringExtra("image_uri");
        if (uri != null) {
            // Decode off the main thread, downsampled to the screen size. The camera stores
            // full-resolution originals, so the old setImageURI decoded the whole bitmap on the
            // UI thread here — the most likely OOM spot, since this screen exists to show that image.
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int targetPx = Math.max(metrics.widthPixels, metrics.heightPixels);
            ImageLoader.load(fullImage, uri, targetPx);
        }
        closeButton.setOnClickListener(v -> finish());
    }
}
