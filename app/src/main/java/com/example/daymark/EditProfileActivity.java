package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;

public class EditProfileActivity extends Activity {
    private static final int REQUEST_TAKE_PHOTO = 1;
    private static final int REQUEST_PICK_PHOTO = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 3;

    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;
    private String username;

    private ImageView avatarImage;
    private TextInputEditText displayNameEdit;
    private TextInputEditText oldPasswordEdit;
    private TextInputEditText newPasswordEdit;
    private TextInputEditText confirmPasswordEdit;

    private String currentAvatarUri;
    private Uri photoUri;

    // 默认头像颜色（8个预设颜色）
    private static final int[] DEFAULT_AVATAR_COLORS = {
            0xFF4CAF50, // 绿色
            0xFF2196F3, // 蓝色
            0xFFFF9800, // 橙色
            0xFFE91E63, // 粉色
            0xFF9C27B0, // 紫色
            0xFF00BCD4, // 青色
            0xFFFFEB3B, // 黄色
            0xFFFF5722  // 深橙色
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        username = getIntent().getStringExtra("username");

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

        // 加载当前数据
        loadCurrentData();

        chooseDefaultAvatarButton.setOnClickListener(v -> showDefaultAvatarPicker());
        takePhotoButton.setOnClickListener(v -> takePhoto());
        pickPhotoButton.setOnClickListener(v -> pickPhoto());
        saveButton.setOnClickListener(v -> saveProfile());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void loadCurrentData() {
        displayNameEdit.setText(dbHelper.getDisplayName(userId));
        currentAvatarUri = dbHelper.getAvatarUri(userId);
        if (!TextUtils.isEmpty(currentAvatarUri)) {
            loadAvatar(currentAvatarUri);
        }
    }

    private void loadAvatar(String uri) {
        if (uri.startsWith("default_")) {
            // 默认头像（纯色）
            int index = Integer.parseInt(uri.substring(8));
            if (index >= 0 && index < DEFAULT_AVATAR_COLORS.length) {
                avatarImage.setImageDrawable(null);
                avatarImage.setBackgroundColor(DEFAULT_AVATAR_COLORS[index]);
            }
        } else {
            // 用户上传的头像
            ImageLoader.load(avatarImage, uri, 200);
        }
    }

    private void showDefaultAvatarPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_avatar_picker, null);
        GridLayout gridLayout = dialogView.findViewById(R.id.avatarGrid);

        // 动态添加8个默认头像
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

        // 为每个头像设置 dialog 引用
        for (int i = 0; i < gridLayout.getChildCount(); i++) {
            gridLayout.getChildAt(i).setTag(dialog);
        }

        dialog.show();
    }

    private void takePhoto() {
        // 检查相机权限（Android 6.0+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                File photoFile = ImageUtils.createImageFile(this);
                photoUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            } catch (IOException e) {
                Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "没有可用的相机应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_TAKE_PHOTO) {
                if (photoUri != null) {
                    currentAvatarUri = photoUri.toString();
                    loadAvatar(currentAvatarUri);
                }
            } else if (requestCode == REQUEST_PICK_PHOTO && data != null) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    currentAvatarUri = selectedImage.toString();
                    loadAvatar(currentAvatarUri);
                }
            }
        }
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

        // 验证显示名称
        if (TextUtils.isEmpty(displayName)) {
            Toast.makeText(this, "请输入显示名称", Toast.LENGTH_SHORT).show();
            return;
        }

        // 更新显示名称
        dbHelper.setDisplayName(userId, displayName);

        // 更新头像
        if (!TextUtils.isEmpty(currentAvatarUri)) {
            dbHelper.setAvatarUri(userId, currentAvatarUri);
        }

        // 修改密码（如果填写了）
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

            // 验证原密码
            if (dbHelper.login(username, oldPassword) == DayMarkDbHelper.NO_USER) {
                Toast.makeText(this, "原密码不正确", Toast.LENGTH_SHORT).show();
                return;
            }

            // 修改密码
            if (dbHelper.changePassword(userId, oldPassword, newPassword)) {
                Toast.makeText(this, "密码修改成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "密码修改失败", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
