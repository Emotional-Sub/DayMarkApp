package com.example.daymark;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
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

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Locale;
import java.util.Set;

public class EditHabitActivity extends Activity {
    private static final int REQUEST_CAMERA = 20;
    private static final int REQUEST_GALLERY = 21;
    private static final int REQUEST_CAMERA_PERMISSION = 22;
    private static final String[] CATEGORIES = {"学习", "运动", "生活", "工作", "健康", "其他"};
    // Spinner order maps to Habit.FREQ_DAILY / FREQ_WEEKLY_DAYS / FREQ_WEEKLY_COUNT by index.
    private static final String[] FREQUENCY_LABELS = {"每天", "每周指定星期", "每周 N 次"};

    // Saved across process death while the camera is open (see onSaveInstanceState).
    private static final String STATE_PENDING_CAMERA = "pending_camera_file";
    private static final String STATE_IMAGE_URI = "image_uri";

    // One-shot flag so the battery-optimization nudge is only shown once per install.
    private static final String PREF_BATTERY_PROMPT_SHOWN = "battery_prompt_shown";

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
    // Full-size photo the camera is asked to write into, via EXTRA_OUTPUT. Held across the
    // capture intent and consumed in onActivityResult; also re-saved/restored on rotation.
    private File pendingCameraFile;
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

        // The camera runs in its own process and can have our Activity recycled while it's open;
        // recover the pending output path (and any already-chosen image) saved before that happened.
        if (savedInstanceState != null) {
            String pendingPath = savedInstanceState.getString(STATE_PENDING_CAMERA);
            if (pendingPath != null) {
                pendingCameraFile = new File(pendingPath);
            }
            String savedImage = savedInstanceState.getString(STATE_IMAGE_URI);
            if (savedImage != null) {
                imageUri = savedImage;
                showImage();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingCameraFile != null) {
            outState.putString(STATE_PENDING_CAMERA, pendingCameraFile.getAbsolutePath());
        }
        if (!TextUtils.isEmpty(imageUri)) {
            outState.putString(STATE_IMAGE_URI, imageUri);
        }
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
        AppExecutors.io().execute(() -> {
            Habit habit = dbHelper.getHabit(id);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                if (habit == null) {
                    Toast.makeText(this, "事件不存在", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                bindHabit(habit);
            });
        });
    }

    /** Populate the form fields from a loaded habit (called on the main thread). */
    private void bindHabit(Habit habit) {
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
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "未找到可用相机", Toast.LENGTH_SHORT).show();
            return;
        }
        // Give the camera a real file to write the full-size image into, exposed through our
        // FileProvider. Without EXTRA_OUTPUT the camera only returns a tiny thumbnail in the extras.
        File target = newPhotoFile();
        if (target == null) {
            Toast.makeText(this, "图片目录创建失败", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingCameraFile = target;
        Uri outputUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", target);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    /** Create an empty file under habit_photos for the camera to fill, or null if the dir fails. */
    private File newPhotoFile() {
        File imageDir = new File(getFilesDir(), "habit_photos");
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            return null;
        }
        return new File(imageDir, "photo_" + System.currentTimeMillis() + ".jpg");
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
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_CAMERA) {
            // With EXTRA_OUTPUT the camera writes the full-size image into pendingCameraFile and
            // returns a null data Intent, so don't gate this branch on data being non-null.
            if (pendingCameraFile != null && pendingCameraFile.exists() && pendingCameraFile.length() > 0) {
                imageUri = Uri.fromFile(pendingCameraFile).toString();
                showImage();
            }
            pendingCameraFile = null;
        } else if (requestCode == REQUEST_GALLERY && data != null && data.getData() != null) {
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

    private void showImage() {
        if (!TextUtils.isEmpty(imageUri)) {
            // 210dp preview slot; decode downsampled off the main thread (see ImageLoader) rather
            // than setImageURI, which would decode the full-size original on the UI thread.
            int targetPx = (int) (210 * getResources().getDisplayMetrics().density);
            ImageLoader.load(previewImage, imageUri, targetPx);
        } else {
            previewImage.setTag(R.id.photoView, null);
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

        if (habitId == -1 && userId == DayMarkDbHelper.NO_USER) {
            Toast.makeText(this, "登录状态已失效，请重新登录", Toast.LENGTH_SHORT).show();
            return;
        }

        // Effectively-final copies for the background lambda (the originals above are reassigned).
        final String fTitle = title, fContent = content, fTimeText = timeText, fCategory = category;
        final String fReminderTime = reminderTime, fFrequencyDays = frequencyDays;
        final int fFrequencyType = frequencyType, fFrequencyCount = frequencyCount, fTargetDays = targetDays;

        // DB write (+ re-read for scheduling) off the UI thread; result handled back on main.
        AppExecutors.io().execute(() -> {
            long savedId;
            boolean success;
            if (habitId == -1) {
                savedId = dbHelper.addHabit(userId, fTitle, fContent, fTimeText, imageUri, fCategory,
                        fReminderTime, fFrequencyType, fFrequencyDays, fFrequencyCount, fTargetDays);
                success = savedId != -1;
            } else {
                success = dbHelper.updateHabit(habitId, fTitle, fContent, fTimeText, imageUri, fCategory,
                        fReminderTime, fFrequencyType, fFrequencyDays, fFrequencyCount, fTargetDays);
                savedId = habitId;
            }
            // Re-read the saved row (still off the main thread) so the alarm reflects what was stored.
            Habit saved = success ? dbHelper.getHabit(savedId) : null;
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                if (success) {
                    ReminderReceiver.schedule(this, saved);
                    Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                    // A reminder was set; on aggressive battery managers the alarm can be delayed or
                    // killed. Offer a one-time nudge to whitelist the app, then close regardless.
                    if (!TextUtils.isEmpty(fReminderTime) && shouldOfferBatteryExemption()) {
                        promptBatteryExemption();
                    } else {
                        finish();
                    }
                } else {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Whether to nudge the user toward exempting the app from battery optimization. True only when
     * the OS supports Doze (API 23+), the app isn't already exempt, and we haven't asked before —
     * the prompt is a one-shot so it doesn't nag on every reminder edit.
     */
    private boolean shouldOfferBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        if (getPreferences(MODE_PRIVATE).getBoolean(PREF_BATTERY_PROMPT_SHOWN, false)) {
            return false;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    /**
     * Explain why reminders may be unreliable under battery optimization and, if the user agrees,
     * open the system battery-optimization list. Closes the screen once the user has decided.
     * Uses ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS (the list screen), which needs no special
     * permission, rather than the direct request action.
     */
    private void promptBatteryExemption() {
        getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_BATTERY_PROMPT_SHOWN, true).apply();
        new AlertDialog.Builder(this)
                .setTitle("让提醒更准时")
                .setMessage("部分手机会在后台限制应用，可能导致打卡提醒延迟或收不到。"
                        + "可在电池优化设置中将 DayMark 设为不优化。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    try {
                        startActivity(new Intent(
                                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, "未找到电池优化设置", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                })
                .setNegativeButton("以后再说", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
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
