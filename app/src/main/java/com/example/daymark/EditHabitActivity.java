package com.example.daymark;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class EditHabitActivity extends Activity {
    private static final int REQUEST_CAMERA = 20;
    private static final int REQUEST_GALLERY = 21;
    private static final int REQUEST_CAMERA_PERMISSION = 22;

    private DayMarkDbHelper dbHelper;
    private EditText titleEdit;
    private EditText contentEdit;
    private EditText timeEdit;
    private ImageView previewImage;
    private String imageUri = "";
    private long habitId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_habit);

        dbHelper = new DayMarkDbHelper(this);
        titleEdit = findViewById(R.id.titleEdit);
        contentEdit = findViewById(R.id.contentEdit);
        timeEdit = findViewById(R.id.timeEdit);
        previewImage = findViewById(R.id.previewImage);
        TextView pageTitle = findViewById(R.id.pageTitle);
        Button cameraButton = findViewById(R.id.cameraButton);
        Button galleryButton = findViewById(R.id.galleryButton);
        Button saveButton = findViewById(R.id.saveButton);
        Button cancelButton = findViewById(R.id.cancelButton);

        habitId = getIntent().getLongExtra("habit_id", -1);
        if (habitId != -1) {
            pageTitle.setText("编辑打卡事件");
            loadHabit(habitId);
        }

        cameraButton.setOnClickListener(v -> openCamera());
        galleryButton.setOnClickListener(v -> openGallery());
        saveButton.setOnClickListener(v -> saveHabit());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void loadHabit(long id) {
        Habit habit = dbHelper.getHabit(id);
        if (habit == null) {
            Toast.makeText(this, "事件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        titleEdit.setText(habit.title);
        contentEdit.setText(habit.content);
        timeEdit.setText(habit.timeText);
        imageUri = habit.imageUri == null ? "" : habit.imageUri;
        showImage();
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CAMERA);
        } else {
            Toast.makeText(this, "未找到可用相机", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_CAMERA) {
            Bitmap bitmap = (Bitmap) data.getExtras().get("data");
            if (bitmap != null) {
                imageUri = saveBitmap(bitmap);
                showImage();
            }
        } else if (requestCode == REQUEST_GALLERY && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some gallery providers do not offer persistable permission; preview still works for this session.
            }
            imageUri = uri.toString();
            showImage();
        }
    }

    private String saveBitmap(Bitmap bitmap) {
        File imageDir = new File(getFilesDir(), "habit_photos");
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            Toast.makeText(this, "图片目录创建失败", Toast.LENGTH_SHORT).show();
            return "";
        }
        File imageFile = new File(imageDir, "photo_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            return Uri.fromFile(imageFile).toString();
        } catch (IOException e) {
            Toast.makeText(this, "图片保存失败", Toast.LENGTH_SHORT).show();
            return "";
        }
    }

    private void showImage() {
        if (!TextUtils.isEmpty(imageUri)) {
            previewImage.setImageURI(Uri.parse(imageUri));
        } else {
            previewImage.setImageResource(android.R.color.transparent);
        }
    }

    private void saveHabit() {
        String title = titleEdit.getText().toString().trim();
        String content = contentEdit.getText().toString().trim();
        String timeText = timeEdit.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            titleEdit.setError("请输入标题");
            return;
        }
        if (TextUtils.isEmpty(content)) {
            contentEdit.setError("请输入内容");
            return;
        }
        if (TextUtils.isEmpty(timeText)) {
            timeEdit.setError("请输入时间");
            return;
        }

        boolean success;
        if (habitId == -1) {
            success = dbHelper.addHabit(title, content, timeText, imageUri) != -1;
        } else {
            success = dbHelper.updateHabit(habitId, title, content, timeText, imageUri);
        }

        if (success) {
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }
}
