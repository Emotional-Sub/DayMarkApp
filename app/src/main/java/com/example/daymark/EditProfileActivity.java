package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class EditProfileActivity extends Activity {
    private static final int REQUEST_TAKE_PHOTO = 1;
    private static final int REQUEST_PICK_PHOTO = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 3;

    private static final int[] DEFAULT_AVATAR_COLORS = {
            0xFF4CAF50,
            0xFF2196F3,
            0xFFFF9800,
            0xFFE91E63,
            0xFF9C27B0,
            0xFF00BCD4,
            0xFFFFEB3B,
            0xFFFF5722
    };

    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;

    private ImageView avatarImage;
    private TextInputEditText displayNameEdit;
    private TextInputEditText oldPasswordEdit;
    private TextInputEditText newPasswordEdit;
    private TextInputEditText confirmPasswordEdit;

    private String currentAvatarUri;
    private String originalAvatarUri;
    private Uri photoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        if (userId == DayMarkDbHelper.NO_USER) {
            finish();
            return;
        }

        avatarImage = findViewById(R.id.editAvatarImage);
        displayNameEdit = findViewById(R.id.displayNameEdit);
        oldPasswordEdit = findViewById(R.id.oldPasswordEdit);
        newPasswordEdit = findViewById(R.id.newPasswordEdit);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEdit);
        MaterialButton chooseDefaultAvatarButton = findViewById(R.id.chooseDefaultAvatarButton);
        MaterialButton takePhotoButton = findViewById(R.id.takePhotoButton);
        MaterialButton pickPhotoButton = findViewById(R.id.pickPhotoButton);
        MaterialButton saveButton = findViewById(R.id.saveButton);
        MaterialButton cancelButton = findViewById(R.id.cancelButton);

        loadCurrentData();

        chooseDefaultAvatarButton.setOnClickListener(v -> showDefaultAvatarPicker());
        takePhotoButton.setOnClickListener(v -> takePhoto());
        pickPhotoButton.setOnClickListener(v -> pickPhoto());
        saveButton.setOnClickListener(v -> saveProfile());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void loadCurrentData() {
        AppExecutors.io().execute(() -> {
            String displayName = dbHelper.getDisplayName(userId);
            String avatarUri = dbHelper.getAvatarUri(userId);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                displayNameEdit.setText(displayName);
                currentAvatarUri = avatarUri;
                originalAvatarUri = avatarUri;
                if (!TextUtils.isEmpty(avatarUri)) {
                    loadAvatar(avatarUri);
                }
            });
        });
    }

    private void loadAvatar(String uri) {
        if (TextUtils.isEmpty(uri)) {
            avatarImage.setImageDrawable(null);
            avatarImage.setBackgroundColor(DEFAULT_AVATAR_COLORS[0]);
            return;
        }
        if (uri.startsWith("default_")) {
            try {
                int index = Integer.parseInt(uri.substring(8));
                if (index >= 0 && index < DEFAULT_AVATAR_COLORS.length) {
                    avatarImage.setImageDrawable(null);
                    avatarImage.setBackgroundColor(DEFAULT_AVATAR_COLORS[index]);
                }
                return;
            } catch (NumberFormatException ignored) {
                // Fall back to the default avatar below.
            }
        }
        avatarImage.setBackground(null);
        ImageLoader.load(avatarImage, uri, 200);
    }

    private void showDefaultAvatarPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_avatar_picker, null);
        GridLayout gridLayout = dialogView.findViewById(R.id.avatarGrid);

        for (int i = 0; i < DEFAULT_AVATAR_COLORS.length; i++) {
            ImageView avatarView = new ImageView(this);
            int size = (int) (60 * getResources().getDisplayMetrics().density);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(8, 8, 8, 8);
            avatarView.setLayoutParams(params);
            avatarView.setBackgroundColor(DEFAULT_AVATAR_COLORS[i]);
            avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            final int index = i;
            avatarView.setOnClickListener(v -> {
                currentAvatarUri = "default_" + index;
                loadAvatar(currentAvatarUri);
                ((AlertDialog) v.getTag()).dismiss();
            });
            gridLayout.addView(avatarView);
        }

        AlertDialog dialog = builder.setView(dialogView)
                .setNegativeButton("取消", null)
                .create();
        for (int i = 0; i < gridLayout.getChildCount(); i++) {
            gridLayout.getChildAt(i).setTag(dialog);
        }
        dialog.show();
    }

    private void takePhoto() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "没有可用的相机应用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File photoFile = ImageUtils.createImageFile(this);
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePictureIntent.setClipData(ClipData.newRawUri("avatar_photo", photoUri));
            startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
        } catch (IOException e) {
            Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_TAKE_PHOTO) {
            if (photoUri != null) {
                currentAvatarUri = photoUri.toString();
                loadAvatar(currentAvatarUri);
            }
        } else if (requestCode == REQUEST_PICK_PHOTO && data != null && data.getData() != null) {
            importPickedAvatar(data.getData());
        }
    }

    private void importPickedAvatar(Uri selectedImage) {
        try {
            getContentResolver().takePersistableUriPermission(
                    selectedImage, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            currentAvatarUri = selectedImage.toString();
            loadAvatar(currentAvatarUri);
        } catch (SecurityException e) {
            copyAvatarToPrivateStorage(selectedImage);
        }
    }

    private void copyAvatarToPrivateStorage(Uri sourceUri) {
        AppExecutors.io().execute(() -> {
            try {
                File dest = ImageUtils.createImageFile(this);
                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     OutputStream out = new java.io.FileOutputStream(dest)) {
                    if (in == null) {
                        throw new IOException("无法读取头像文件");
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                Uri stored = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", dest);
                AppExecutors.main().execute(() -> {
                    if (!isFinishing()) {
                        currentAvatarUri = stored.toString();
                        loadAvatar(currentAvatarUri);
                    }
                });
            } catch (IOException e) {
                AppExecutors.main().execute(() ->
                        Toast.makeText(this, "头像保存失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveProfile() {
        String displayName = displayNameEdit.getText().toString().trim();
        String oldPassword = oldPasswordEdit.getText().toString();
        String newPassword = newPasswordEdit.getText().toString();
        String confirmPassword = confirmPasswordEdit.getText().toString();

        if (TextUtils.isEmpty(displayName)) {
            Toast.makeText(this, "请输入显示名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!TextUtils.isEmpty(oldPassword) || !TextUtils.isEmpty(newPassword)) {
            if (TextUtils.isEmpty(oldPassword)) {
                Toast.makeText(this, "请输入原密码", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(newPassword)) {
                Toast.makeText(this, "请输入新密码", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        final String finalAvatarUri = currentAvatarUri;
        final String previousAvatarUri = originalAvatarUri;
        AppExecutors.io().execute(() -> {
            int result = dbHelper.updateProfile(userId, displayName, finalAvatarUri, oldPassword, newPassword);
            if (result == DayMarkDbHelper.PROFILE_UPDATE_OK
                    && !TextUtils.isEmpty(previousAvatarUri)
                    && !TextUtils.equals(previousAvatarUri, finalAvatarUri)) {
                ImageUtils.deleteOwnedImage(this, previousAvatarUri);
            }
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                if (result == DayMarkDbHelper.PROFILE_UPDATE_OK) {
                    originalAvatarUri = finalAvatarUri;
                    Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else if (result == DayMarkDbHelper.PROFILE_UPDATE_OLD_PASSWORD_REQUIRED) {
                    Toast.makeText(this, "请输入原密码", Toast.LENGTH_SHORT).show();
                } else if (result == DayMarkDbHelper.PROFILE_UPDATE_OLD_PASSWORD_INCORRECT) {
                    Toast.makeText(this, "原密码不正确", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
