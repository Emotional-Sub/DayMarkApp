package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProfileActivity extends Activity {
    private static final int HEATMAP_WEEKS = 27;
    private static final int REQUEST_EDIT_PROFILE = 100;
    private static final int REQUEST_IMPORT_FILE = 101;
    private static final int REQUEST_EXPORT_FILE = 102;

    private static final int[] DEFAULT_AVATAR_COLORS = {
            0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63,
            0xFF9C27B0, 0xFF00BCD4, 0xFFFFEB3B, 0xFFFF5722
    };

    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;
    private String username;

    private ImageView avatarImage;
    private TextView nameText;
    private TextView accountText;
    private TextView totalEventsText;
    private TextView todayCompletedText;
    private TextView totalChecksText;
    private TextView maxStreakText;
    private TextView categoryStatsText;
    private HeatmapView heatmapView;
    private GridLayout achievementContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        username = getIntent().getStringExtra("username");
        if (userId == DayMarkDbHelper.NO_USER) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        avatarImage = findViewById(R.id.profileAvatarImage);
        nameText = findViewById(R.id.profileNameText);
        accountText = findViewById(R.id.profileAccountText);
        totalEventsText = findViewById(R.id.totalEventsText);
        todayCompletedText = findViewById(R.id.todayCompletedText);
        totalChecksText = findViewById(R.id.totalChecksText);
        maxStreakText = findViewById(R.id.maxStreakText);
        categoryStatsText = findViewById(R.id.categoryStatsText);
        heatmapView = findViewById(R.id.heatmapView);
        achievementContainer = findViewById(R.id.achievementContainer);
        MaterialCardView categoryStatsCard = findViewById(R.id.categoryStatsCard);
        MaterialCardView achievementsCard = findViewById(R.id.achievementsCard);
        MaterialButton editProfileButton = findViewById(R.id.editProfileButton);
        MaterialButton exportBackupButton = findViewById(R.id.exportBackupButton);
        MaterialButton importBackupButton = findViewById(R.id.importBackupButton);
        MaterialButton logoutButton = findViewById(R.id.logoutButton);
        MaterialButton deleteAccountButton = findViewById(R.id.deleteAccountButton);
        MaterialButton backButton = findViewById(R.id.backButton);

        accountText.setText("账号名：" + (username == null ? "" : username));
        editProfileButton.setOnClickListener(v -> goToEditProfile());
        categoryStatsCard.setOnClickListener(v -> goToStats());
        achievementsCard.setOnClickListener(v -> goToAchievements());
        exportBackupButton.setOnClickListener(v -> exportBackup());
        importBackupButton.setOnClickListener(v -> showImportDialog());
        logoutButton.setOnClickListener(v -> goToLogin());
        deleteAccountButton.setOnClickListener(v -> confirmDeleteAccount());
        backButton.setOnClickListener(v -> finish());
        heatmapView.setOnDayClickListener(this::showDayDetails);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        AppExecutors.io().execute(() -> {
            String displayName = dbHelper.getDisplayName(userId);
            String avatarUri = dbHelper.getAvatarUri(userId);
            List<Habit> habits = dbHelper.getAllHabits(userId);
            int totalRecords = dbHelper.getTotalRecordCount(userId);
            int bestStreak = 0;
            int completedToday = 0;
            for (Habit habit : habits) {
                bestStreak = Math.max(bestStreak, habit.streakDays);
                if (habit.isCheckedToday()) {
                    completedToday++;
                }
            }
            final int finalBestStreak = bestStreak;
            final int finalCompletedToday = completedToday;
            long to = DateUtils.startOfDay(System.currentTimeMillis()) + DateUtils.DAY_MS;
            long from = DateUtils.startOfWeek(System.currentTimeMillis())
                    - (long) (HEATMAP_WEEKS - 1) * 7 * DateUtils.DAY_MS;
            Map<Long, Integer> counts = dbHelper.getDailyCheckCounts(userId, from, to);
            List<CategoryStat> categoryStats = dbHelper.getCategoryStats(userId);
            List<Achievement> achievements = dbHelper.getAchievements(userId);
            String categoryText = buildCategoryText(categoryStats);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                nameText.setText(displayName);
                loadAvatar(avatarUri);
                totalEventsText.setText(String.valueOf(habits.size()));
                todayCompletedText.setText(String.valueOf(finalCompletedToday));
                totalChecksText.setText(String.valueOf(totalRecords));
                maxStreakText.setText(String.valueOf(finalBestStreak));
                heatmapView.setData(counts, HEATMAP_WEEKS);
                categoryStatsText.setText(categoryText);
                renderAchievements(achievements);
            });
        });
    }

    private String buildCategoryText(List<CategoryStat> stats) {
        if (stats.isEmpty()) {
            return "还没有打卡事件";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < stats.size(); i++) {
            CategoryStat stat = stats.get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(stat.category)
                    .append("：")
                    .append(stat.habitCount).append(" 个事件，")
                    .append(stat.checkCount).append(" 次打卡");
        }
        return builder.toString();
    }

    private void renderAchievements(List<Achievement> achievements) {
        achievementContainer.removeAllViews();
        TextView noAchievementsText = findViewById(R.id.noAchievementsText);
        List<Achievement> unlockedAchievements = new ArrayList<>();
        for (Achievement achievement : achievements) {
            if (achievement.unlocked) {
                unlockedAchievements.add(achievement);
            }
        }
        if (unlockedAchievements.isEmpty()) {
            noAchievementsText.setVisibility(android.view.View.VISIBLE);
            return;
        }
        noAchievementsText.setVisibility(android.view.View.GONE);
        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (10 * density);
        for (int i = 0; i < unlockedAchievements.size(); i++) {
            Achievement achievement = unlockedAchievements.get(i);
            MaterialCardView card = AchievementBadgeRenderer.create(this, achievement, i, false);
            GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
            cardParams.width = 0;
            cardParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
            cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cardParams.setMargins(i % 2 == 0 ? 0 : gap / 2, 0, i % 2 == 0 ? gap / 2 : 0, gap);
            card.setLayoutParams(cardParams);
            achievementContainer.addView(card);
        }
    }

    private void loadAvatar(String avatarUri) {
        if (TextUtils.isEmpty(avatarUri)) {
            avatarImage.setImageResource(R.drawable.ic_profile_24);
            avatarImage.setBackgroundColor(DEFAULT_AVATAR_COLORS[0]);
        } else if (avatarUri.startsWith("default_")) {
            try {
                int index = Integer.parseInt(avatarUri.substring(8));
                if (index >= 0 && index < DEFAULT_AVATAR_COLORS.length) {
                    avatarImage.setImageDrawable(null);
                    avatarImage.setBackgroundColor(DEFAULT_AVATAR_COLORS[index]);
                    return;
                }
            } catch (NumberFormatException ignored) {
                // Fall back to the default avatar below.
            }
            avatarImage.setImageResource(R.drawable.ic_profile_24);
            avatarImage.setBackgroundColor(DEFAULT_AVATAR_COLORS[0]);
        } else {
            avatarImage.setBackground(null);
            ImageLoader.load(avatarImage, avatarUri, 200);
        }
    }

    private void showDayDetails(long dayTimestamp, int checkCount) {
        AppExecutors.io().execute(() -> {
            List<CheckRecord> records = dbHelper.getRecordsForDay(userId, dayTimestamp);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                String dateStr = DateUtils.formatDate(dayTimestamp);
                StringBuilder content = new StringBuilder();
                if (records.isEmpty()) {
                    content.append("这一天还没有打卡记录");
                } else {
                    content.append("共打卡 ").append(checkCount).append(" 次：\n\n");
                    for (CheckRecord record : records) {
                        content.append("• ").append(record.habitTitle).append("\n");
                        if (!TextUtils.isEmpty(record.note)) {
                            content.append("  备注：").append(record.note).append("\n");
                        }
                        content.append("  ").append(DateUtils.formatDateTime(record.checkedAt)).append("\n\n");
                    }
                }
                new AlertDialog.Builder(this)
                        .setTitle(dateStr)
                        .setMessage(content.toString())
                        .setPositiveButton("确定", null)
                        .show();
            });
        });
    }

    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("删除账号")
                .setMessage("将永久删除账号“" + (username == null ? "" : username)
                        + "”及其所有打卡事件和记录，且无法恢复。确定吗？")
                .setPositiveButton("永久删除", (dialog, which) -> {
                    AppExecutors.io().execute(() -> {
                        boolean success = dbHelper.deleteUser(userId);
                        AppExecutors.main().execute(() -> {
                            if (isFinishing()) {
                                return;
                            }
                            if (success) {
                                Toast.makeText(this, "账号已删除", Toast.LENGTH_SHORT).show();
                                goToLogin(true);
                            } else {
                                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void goToEditProfile() {
        Intent intent = new Intent(this, EditProfileActivity.class);
        intent.putExtra("user_id", userId);
        intent.putExtra("username", username);
        startActivityForResult(intent, REQUEST_EDIT_PROFILE);
    }

    private void goToAchievements() {
        Intent intent = new Intent(this, AchievementsActivity.class);
        intent.putExtra("user_id", userId);
        startActivity(intent);
    }

    private void goToStats() {
        Intent intent = new Intent(this, StatsActivity.class);
        intent.putExtra("user_id", userId);
        startActivity(intent);
    }

    private void goToLogin() {
        goToLogin(false);
    }

    private void goToLogin(boolean clearRememberedAccount) {
        try {
            String masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(
                    androidx.security.crypto.MasterKeys.AES256_GCM_SPEC);
            android.content.SharedPreferences securePrefs =
                    androidx.security.crypto.EncryptedSharedPreferences.create(
                            "secure_login",
                            masterKeyAlias,
                            this,
                            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            android.content.SharedPreferences.Editor secureEditor = securePrefs.edit()
                    .remove("session_user_id")
                    .remove("session_username");
            if (clearRememberedAccount) {
                secureEditor.remove("password");
            }
            secureEditor.apply();
        } catch (Exception e) {
            Logger.securityError("Failed to clear encrypted session", e);
        }
        android.content.SharedPreferences.Editor editor = getSharedPreferences("login", MODE_PRIVATE).edit()
                .remove("session_user_id")
                .remove("session_username");
        if (clearRememberedAccount) {
            editor.remove("remember")
                    .remove("username")
                    .remove("password");
        }
        editor.apply();
        Intent intent = new Intent(this, LoginActivity.class);
        if (clearRememberedAccount) {
            intent.putExtra("clear_login_fields", true);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showImportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("导入数据")
                .setMessage("警告：导入将覆盖当前所有数据。\n\n请确保已备份当前数据，导入操作不可撤销。")
                .setPositiveButton("选择备份文件", (dialog, which) -> selectImportFile())
                .setNegativeButton("取消", null)
                .show();
    }

    private void selectImportFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择备份文件"), REQUEST_IMPORT_FILE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "未找到文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "daymark_backup_" + System.currentTimeMillis() + ".json");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_FILE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "未找到文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    private void importFromUri(Uri uri) {
        AppExecutors.io().execute(() -> {
            try {
                java.io.File tempFile = new java.io.File(getCacheDir(), "import_temp.json");
                try (java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                     java.io.OutputStream outputStream = new java.io.FileOutputStream(tempFile)) {
                    if (inputStream == null) {
                        throw new java.io.IOException("无法读取文件");
                    }
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                boolean success = dbHelper.restoreFromJson(tempFile.getAbsolutePath());
                tempFile.delete();
                boolean currentUserStillExists = dbHelper.userExists(userId);

                AppExecutors.main().execute(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    if (success) {
                        if (currentUserStillExists) {
                            Toast.makeText(this, "导入成功，正在刷新...", Toast.LENGTH_SHORT).show();
                            refresh();
                        } else {
                            Toast.makeText(this, "导入成功，请重新登录恢复的账号", Toast.LENGTH_LONG).show();
                            goToLogin(true);
                        }
                    } else {
                        Toast.makeText(this, "导入失败，请检查文件格式", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Logger.e("Import failed", e);
                AppExecutors.main().execute(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void exportToUri(Uri uri) {
        AppExecutors.io().execute(() -> {
            boolean success = false;
            try (java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt")) {
                if (outputStream == null) {
                    throw new java.io.IOException("无法写入备份文件");
                }
                success = dbHelper.exportBackupToStream(outputStream);
            } catch (Exception e) {
                Logger.e("Export failed", e);
            }
            final boolean exportSuccess = success;
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                if (exportSuccess) {
                    Toast.makeText(this, "备份导出成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "备份导出失败", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_PROFILE && resultCode == RESULT_OK) {
            refresh();
        } else if (requestCode == REQUEST_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importFromUri(uri);
            }
        } else if (requestCode == REQUEST_EXPORT_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                exportToUri(uri);
            }
        }
    }

    private LinearLayout.LayoutParams topMarginParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }
}
