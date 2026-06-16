package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-user profile page: account info + overview, a yearly check-in heatmap, per-category
 * stats, personal achievements, and account settings (edit profile / change password /
 * delete account).
 *
 * <p>The header separates the editable display name (nickname) from the fixed login username:
 * the display name is what's shown prominently and can be changed via "编辑资料", while the
 * login username stays put as it identifies the account.
 */
public class ProfileActivity extends Activity {
    /** Trailing weeks shown in the heatmap (~half a year, fits the card width comfortably). */
    private static final int HEATMAP_WEEKS = 27;
    private static final int REQUEST_EDIT_PROFILE = 100;
    /** Request code for importing data from JSON backup file. */
    private static final int REQUEST_IMPORT_FILE = 101;

    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;
    /** The fixed login account name; never changes from this screen. */
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
    private LinearLayout achievementContainer;

    // 默认头像颜色
    private static final int[] DEFAULT_AVATAR_COLORS = {
            0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63,
            0xFF9C27B0, 0xFF00BCD4, 0xFFFFEB3B, 0xFFFF5722
    };

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
        MaterialButton importBackupButton = findViewById(R.id.importBackupButton);
        MaterialButton logoutButton = findViewById(R.id.logoutButton);
        MaterialButton deleteAccountButton = findViewById(R.id.deleteAccountButton);
        MaterialButton backButton = findViewById(R.id.backButton);

        accountText.setText("账号名：" + (username == null ? "" : username));
        editProfileButton.setOnClickListener(v -> goToEditProfile());
        categoryStatsCard.setOnClickListener(v -> goToStats());
        achievementsCard.setOnClickListener(v -> goToAchievements());
        importBackupButton.setOnClickListener(v -> showImportDialog());
        logoutButton.setOnClickListener(v -> goToLogin());
        deleteAccountButton.setOnClickListener(v -> confirmDeleteAccount());
        backButton.setOnClickListener(v -> finish());

        // 设置热力图点击监听
        heatmapView.setOnDayClickListener((dayTimestamp, checkCount) -> {
            showDayDetails(dayTimestamp, checkCount);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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

    /**
     * 显示某一天的打卡详情对话框
     */
    private void showDayDetails(long dayTimestamp, int checkCount) {
        AppExecutors.io().execute(() -> {
            // 获取该天的打卡记录
            List<CheckRecord> records = dbHelper.getRecordsForDay(userId, dayTimestamp);

            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }

                // 构建对话框内容
                String dateStr = DateUtils.formatDate(dayTimestamp);
                StringBuilder content = new StringBuilder();

                if (records.isEmpty()) {
                    content.append("这一天还没有打卡记录");
                } else {
                    content.append("共打卡 ").append(checkCount).append(" 次：\n\n");
                    for (CheckRecord record : records) {
                        content.append("✓ ").append(record.habitTitle).append("\n");
                        if (record.note != null && !record.note.isEmpty()) {
                            content.append("  备注：").append(record.note).append("\n");
                        }
                        content.append("  ").append(DateUtils.formatDateTime(record.checkedAt)).append("\n\n");
                    }
                }

                // 显示对话框
                new AlertDialog.Builder(this)
                        .setTitle(dateStr)
                        .setMessage(content.toString())
                        .setPositiveButton("确定", null)
                        .show();
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_PROFILE && resultCode == RESULT_OK) {
            // 刷新数据
            refresh();
        } else if (requestCode == REQUEST_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                importFromUri(uri);
            }
        }
    }

    private void refresh() {
        nameText.setText(dbHelper.getDisplayName(userId));

        // 加载头像
        String avatarUri = dbHelper.getAvatarUri(userId);
        loadAvatar(avatarUri);

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

        // 更新统计卡片
        totalEventsText.setText(String.valueOf(habits.size()));
        todayCompletedText.setText(String.valueOf(completedToday));
        totalChecksText.setText(String.valueOf(totalRecords));
        maxStreakText.setText(String.valueOf(bestStreak));

        long to = DateUtils.startOfDay(System.currentTimeMillis()) + DateUtils.DAY_MS;
        long from = DateUtils.startOfWeek(System.currentTimeMillis())
                - (long) (HEATMAP_WEEKS - 1) * 7 * DateUtils.DAY_MS;
        Map<Long, Integer> counts = dbHelper.getDailyCheckCounts(userId, from, to);
        heatmapView.setData(counts, HEATMAP_WEEKS);

        categoryStatsText.setText(buildCategoryText());
        renderAchievements(dbHelper.getAchievements(userId));
    }

    private String buildCategoryText() {
        List<CategoryStat> stats = dbHelper.getCategoryStats(userId);
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

    /** Build one row per achievement; unlocked rows use the brand color, locked ones are muted. */
    private void renderAchievements(List<Achievement> achievements) {
        achievementContainer.removeAllViews();
        TextView noAchievementsText = findViewById(R.id.noAchievementsText);

        // 只显示已解锁的勋章
        List<Achievement> unlockedAchievements = new ArrayList<>();
        for (Achievement achievement : achievements) {
            if (achievement.unlocked) {
                unlockedAchievements.add(achievement);
            }
        }

        if (unlockedAchievements.isEmpty()) {
            // 没有获得勋章，显示提示
            noAchievementsText.setVisibility(android.view.View.VISIBLE);
            return;
        }

        noAchievementsText.setVisibility(android.view.View.GONE);

        float density = getResources().getDisplayMetrics().density;
        int rowPaddingV = (int) (8 * density);
        int unlockedColor = getColor(R.color.primary);

        for (Achievement achievement : unlockedAchievements) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, rowPaddingV, 0, rowPaddingV);

            TextView title = new TextView(this);
            title.setText("🏅 " + achievement.title);
            title.setTextColor(unlockedColor);
            title.setTextSize(15f);
            title.setGravity(Gravity.START);
            title.getPaint().setFakeBoldText(true);

            TextView desc = new TextView(this);
            desc.setText(achievement.description);
            desc.setTextColor(getColor(R.color.onSurfaceVariant));
            desc.setTextSize(13f);

            row.addView(title);
            row.addView(desc);
            achievementContainer.addView(row);
        }
    }

    private void loadAvatar(String avatarUri) {
        if (TextUtils.isEmpty(avatarUri)) {
            // 没有头像，使用默认图标
            avatarImage.setImageResource(R.drawable.ic_profile_24);
            avatarImage.setBackgroundColor(DEFAULT_AVATAR_COLORS[0]);
        } else if (avatarUri.startsWith("default_")) {
            // 默认头像（纯色）
            int index = Integer.parseInt(avatarUri.substring(8));
            if (index >= 0 && index < DEFAULT_AVATAR_COLORS.length) {
                avatarImage.setImageDrawable(null);
                avatarImage.setBackgroundColor(DEFAULT_AVATAR_COLORS[index]);
            }
        } else {
            // 用户上传的头像
            ImageLoader.load(avatarImage, avatarUri, 200);
        }
    }

    /** A MATCH_PARENT/WRAP_CONTENT layout param with the given top margin, for stacked dialog views. */
    private LinearLayout.LayoutParams topMarginParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("删除账号")
                .setMessage("将永久删除账号“" + (username == null ? "" : username) +
                        "”及其所有打卡事件和记录，且无法恢复。确定吗？")
                .setPositiveButton("永久删除", (dialog, which) -> {
                    if (dbHelper.deleteUser(userId)) {
                        Toast.makeText(this, "账号已删除", Toast.LENGTH_SHORT).show();
                        goToLogin();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** Return to login and clear the back stack so the deleted/changed session can't be resumed. */
    private void goToLogin() {
        // Clear the saved session from encrypted preferences, otherwise LoginActivity's auto-login
        // would immediately send the user (with now-stale credentials) right back to the app
        // instead of showing the login screen.
        try {
            String masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(
                    androidx.security.crypto.MasterKeys.AES256_GCM_SPEC);
            android.content.SharedPreferences securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                    "secure_login",
                    masterKeyAlias,
                    this,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            securePrefs.edit()
                    .remove("session_user_id")
                    .remove("session_username")
                    .apply();
            Logger.d("Encrypted session cleared");
        } catch (Exception e) {
            Logger.securityError("Failed to clear encrypted session", e);
        }

        // Also clear any legacy unencrypted session data
        getSharedPreferences("login", MODE_PRIVATE).edit()
                .remove("session_user_id")
                .remove("session_username")
                .apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Show a dialog to warn the user about data import.
     */
    private void showImportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("导入数据")
                .setMessage("警告：导入将会覆盖当前所有数据！\n\n请确保您已备份当前数据，导入操作不可撤销。\n\n支持的文件格式：\n• JSON备份文件（db_backup_*.json）")
                .setPositiveButton("选择备份文件", (dialog, which) -> selectImportFile())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * Launch file picker to select a backup JSON file.
     */
    private void selectImportFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, "选择备份文件"),
                    REQUEST_IMPORT_FILE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "未找到文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Import database from a URI pointing to a backup JSON file.
     */
    private void importFromUri(android.net.Uri uri) {
        AppExecutors.io().execute(() -> {
            try {
                // Copy the URI content to a temporary file
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

                // Restore from the temporary file
                boolean success = dbHelper.restoreFromJson(tempFile.getAbsolutePath());

                // Clean up temp file
                tempFile.delete();

                // Show result on main thread
                AppExecutors.main().execute(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    if (success) {
                        Toast.makeText(this, "导入成功！正在刷新...", Toast.LENGTH_SHORT).show();
                        refresh();
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
}
