package com.example.daymark;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

public class EditHabitActivity extends Activity {
    private static final int REQUEST_CAMERA = 20;
    private static final int REQUEST_GALLERY = 21;
    private static final int REQUEST_CAMERA_PERMISSION = 22;
    private static final String[] CATEGORIES = {"学习", "运动", "生活", "工作", "健康", "其他"};
    // Spinner order maps to Habit.FREQ_DAILY / FREQ_WEEKLY_DAYS / FREQ_WEEKLY_COUNT by index.
    private static final String[] FREQUENCY_LABELS = {"每天", "每周指定星期", "每周 N 次"};

    private DayMarkDbHelper dbHelper;
    private EditText titleEdit;
    private EditText contentEdit;
    private EditText timeEdit;
    private Spinner categorySpinner;
    private Spinner frequencySpinner;
    private LinearLayout weekdayRow;
    private ToggleButton[] dayToggles;
    private EditText frequencyCountEdit;
    private EditText targetDaysEdit;
    private EditText reminderEdit;
    private ImageView previewImage;
    private String imageUri = "";
    private long habitId = -1;
    private long userId = DayMarkDbHelper.NO_USER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_habit);

        dbHelper = new DayMarkDbHelper(this);
        titleEdit = findViewById(R.id.titleEdit);
        contentEdit = findViewById(R.id.contentEdit);
        timeEdit = findViewById(R.id.timeEdit);
        categorySpinner = findViewById(R.id.categorySpinner);
        frequencySpinner = findViewById(R.id.frequencySpinner);
        weekdayRow = findViewById(R.id.weekdayRow);
        frequencyCountEdit = findViewById(R.id.frequencyCountEdit);
        targetDaysEdit = findViewById(R.id.targetDaysEdit);
        reminderEdit = findViewById(R.id.reminderEdit);
        previewImage = findViewById(R.id.previewImage);
        TextView pageTitle = findViewById(R.id.pageTitle);
        Button cameraButton = findViewById(R.id.cameraButton);
        Button galleryButton = findViewById(R.id.galleryButton);
        Button saveButton = findViewById(R.id.saveButton);
        Button cancelButton = findViewById(R.id.cancelButton);

        // Order matches ISO weekday numbers 1..7 (Mon..Sun).
        dayToggles = new ToggleButton[]{
                findViewById(R.id.dayMon), findViewById(R.id.dayTue), findViewById(R.id.dayWed),
                findViewById(R.id.dayThu), findViewById(R.id.dayFri), findViewById(R.id.daySat),
                findViewById(R.id.daySun)
        };

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CATEGORIES);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        ArrayAdapter<String> frequencyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, FREQUENCY_LABELS);
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frequencySpinner.setAdapter(frequencyAdapter);
        frequencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateFrequencyVisibility(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        habitId = getIntent().getLongExtra("habit_id", -1);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        if (habitId != -1) {
            pageTitle.setText("编辑打卡事件");
            loadHabit(habitId);
        } else {
            updateFrequencyVisibility(Habit.FREQ_DAILY);
        }

        cameraButton.setOnClickListener(v -> openCamera());
        galleryButton.setOnClickListener(v -> openGallery());
        saveButton.setOnClickListener(v -> saveHabit());
        cancelButton.setOnClickListener(v -> finish());

        // Time fields are picked, not typed, so the stored value is always valid HH:mm.
        attachTimePicker(timeEdit, false);
        attachTimePicker(reminderEdit, true);
    }

    /** Show only the controls relevant to the selected frequency type. */
    private void updateFrequencyVisibility(int frequencyType) {
        weekdayRow.setVisibility(frequencyType == Habit.FREQ_WEEKLY_DAYS ? View.VISIBLE : View.GONE);
        frequencyCountEdit.setVisibility(
                frequencyType == Habit.FREQ_WEEKLY_COUNT ? View.VISIBLE : View.GONE);
    }

    /**
     * Make an EditText open a TimePickerDialog on tap instead of accepting keyboard input.
     * When {@code clearable} is true a long-press clears the value (used for the optional reminder).
     */
    private void attachTimePicker(EditText field, boolean clearable) {
        field.setFocusable(false);
        field.setClickable(true);
        field.setOnClickListener(v -> showTimePicker(field));
        if (clearable) {
            field.setOnLongClickListener(v -> {
                field.setText("");
                Toast.makeText(this, "已清除提醒", Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    private void showTimePicker(EditText field) {
        int hour = 8;
        int minute = 0;
        int[] parsed = parseHourMinute(field.getText().toString());
        if (parsed != null) {
            hour = parsed[0];
            minute = parsed[1];
        }
        new TimePickerDialog(this, (view, selectedHour, selectedMinute) ->
                field.setText(String.format(Locale.CHINA, "%02d:%02d", selectedHour, selectedMinute)),
                hour, minute, true).show();
    }

    private int[] parseHourMinute(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        String[] parts = text.trim().split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return new int[]{hour, minute};
        } catch (NumberFormatException e) {
            return null;
        }
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
        setCategorySelection(habit.category);
        reminderEdit.setText(habit.reminderTime);
        imageUri = habit.imageUri == null ? "" : habit.imageUri;
        showImage();

        frequencySpinner.setSelection(clampFrequency(habit.frequencyType));
        updateFrequencyVisibility(habit.frequencyType);
        Set<Integer> days = habit.parseDays();
        for (int i = 0; i < dayToggles.length; i++) {
            dayToggles[i].setChecked(days.contains(i + 1));
        }
        if (habit.frequencyCount > 0) {
            frequencyCountEdit.setText(String.valueOf(habit.frequencyCount));
        }
        if (habit.targetDays > 0) {
            targetDaysEdit.setText(String.valueOf(habit.targetDays));
        }
    }

    private int clampFrequency(int frequencyType) {
        return (frequencyType < 0 || frequencyType >= FREQUENCY_LABELS.length)
                ? Habit.FREQ_DAILY : frequencyType;
    }

    private void setCategorySelection(String category) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(category)) {
                categorySpinner.setSelection(i);
                return;
            }
        }
        categorySpinner.setSelection(0);
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

        int frequencyType = clampFrequency(frequencySpinner.getSelectedItemPosition());
        String frequencyDays = "";
        int frequencyCount = 0;
        if (frequencyType == Habit.FREQ_WEEKLY_DAYS) {
            frequencyDays = collectSelectedDays();
            if (TextUtils.isEmpty(frequencyDays)) {
                Toast.makeText(this, "请至少选择一个星期几", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (frequencyType == Habit.FREQ_WEEKLY_COUNT) {
            frequencyCount = parsePositiveInt(frequencyCountEdit.getText().toString());
            if (frequencyCount < 1 || frequencyCount > 7) {
                frequencyCountEdit.setError("请输入 1-7 之间的次数");
                return;
            }
        }
        int targetDays = parsePositiveInt(targetDaysEdit.getText().toString());

        String category = dbHelper.normalizeCategory((String) categorySpinner.getSelectedItem());
        String reminderTime = dbHelper.normalizeReminder(reminderEdit.getText().toString().trim());

        boolean success;
        long savedId;
        if (habitId == -1) {
            if (userId == DayMarkDbHelper.NO_USER) {
                Toast.makeText(this, "登录状态已失效，请重新登录", Toast.LENGTH_SHORT).show();
                return;
            }
            savedId = dbHelper.addHabit(userId, title, content, timeText, imageUri, category,
                    reminderTime, frequencyType, frequencyDays, frequencyCount, targetDays);
            success = savedId != -1;
        } else {
            success = dbHelper.updateHabit(habitId, title, content, timeText, imageUri, category,
                    reminderTime, frequencyType, frequencyDays, frequencyCount, targetDays);
            savedId = habitId;
        }

        if (success) {
            ReminderReceiver.schedule(this, dbHelper.getHabit(savedId));
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    /** Comma-separated ISO weekday numbers (1=Mon..7=Sun) for the checked toggles. */
    private String collectSelectedDays() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < dayToggles.length; i++) {
            if (dayToggles[i].isChecked()) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(i + 1);
            }
        }
        return builder.toString();
    }

    /** Parse a non-negative integer, returning 0 for empty or malformed input. */
    private int parsePositiveInt(String text) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
