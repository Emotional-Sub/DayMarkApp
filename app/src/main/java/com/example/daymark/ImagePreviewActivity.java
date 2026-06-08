package com.example.daymark;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
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
            fullImage.setImageURI(Uri.parse(uri));
        }
        closeButton.setOnClickListener(v -> finish());
    }
}
