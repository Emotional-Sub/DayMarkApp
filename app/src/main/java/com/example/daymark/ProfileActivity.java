package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-user profile page: account info + overview, a yearly check-in heatmap, per-category
 * stats, personal achievements, and account settings (edit profile / change password /
 * delete account).
 *
 * <p>The header separates the editable display name (nickname) from the fixed login username:
 * the display name is what's shown prominently and can be changed via "编辑账号", while the
 * login username stays put as it identifies the account.
 */
public class ProfileActivity extends Activity {
    /** Trailing weeks shown in the heatmap (~half a year, fits the card width comfortably). */
    private static final int HEATMAP_WEEKS = 27;

    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;
    /** The fixed login account name; never changes from this screen. */
    private String username;

    private TextView nameText;
    private TextView accountText;
    private TextView totalEventsText;
    private TextView todayCompletedText;
    private TextView totalChecksText;
    private TextView maxStreakText;
    private TextView categoryStatsText;
    private HeatmapView heatmapView;
    private LinearLayout achievementContainer;

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

        nameText = findViewById(R.id.profileNameText);
        accountText = findViewById(R.id.profileAccountText);
        totalEventsText = findViewById(R.id.totalEventsText);
        todayCompletedText = findViewById(R.id.todayCompletedText);
        totalChecksText = findViewById(R.id.totalChecksText);
        maxStreakText = findViewById(R.id.maxStreakText);
        categoryStatsText = findViewById(R.id.categoryStatsText);
        heatmapView = findViewById(R.id.heatmapView);
        achievementContainer = findViewById(R.id.achievementContainer);
        MaterialButton editProfileButton = findViewById(R.id.editProfileButton);
        MaterialButton logoutButton = findViewById(R.id.logoutButton);
        MaterialButton deleteAccountButton = findViewById(R.id.deleteAccountButton);
        MaterialButton backButton = findViewById(R.id.backButton);

        accountText.setText("账号名：" + (username == null ? "" : username));
        editProfileButton.setOnClickListener(v -> showEditProfileDialog());
        logoutButton.setOnClickListener(v -> goToLogin());
        deleteAccountButton.setOnClickListener(v -> confirmDeleteAccount());
        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        nameText.setText(dbHelper.getDisplayName(userId));

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
        float density = getResources().getDisplayMetrics().density;
        int rowPaddingV = (int) (8 * density);
        int unlockedColor = getColor(R.color.brand_green);
        int mutedColor = getColor(R.color.muted);

        for (Achievement achievement : achievements) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, rowPaddingV, 0, rowPaddingV);
            row.setAlpha(achievement.unlocked ? 1f : 0.5f);

            TextView title = new TextView(this);
            title.setText((achievement.unlocked ? "🏅 " : "🔒 ") + achievement.title);
            title.setTextColor(achievement.unlocked ? unlockedColor : mutedColor);
            title.setTextSize(15f);
            title.setGravity(Gravity.START);
            title.getPaint().setFakeBoldText(true);

            TextView desc = new TextView(this);
            desc.setText(achievement.description + (achievement.unlocked ? "（已解锁）" : "（未解锁）"));
            desc.setTextColor(mutedColor);
            desc.setTextSize(13f);

            row.addView(title);
            row.addView(desc);
            achievementContainer.addView(row);
        }
    }

    /**
     * Unified account editor: shows the fixed login account name (read-only), an editable
     * username (the display name), and optional password-change fields. Password fields left
     * blank mean "don't change the password"; filling them in requires the current password and,
     * on success, forces a re-login.
     *
     * <p>The save uses {@link AlertDialog#setOnShowListener} so a failed password check keeps the
     * dialog open (the default positive-button click always dismisses).
     */
    private void showEditProfileDialog() {
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        int labelTop = (int) (12 * getResources().getDisplayMetrics().density);

        TextView accountName = new TextView(this);
        accountName.setText("账号名：" + (username == null ? "" : username) + "（不可修改）");
        accountName.setTextColor(getColor(R.color.muted));
        accountName.setTextSize(14f);

        EditText nameInput = new EditText(this);
        nameInput.setHint("用户名");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setText(dbHelper.getDisplayName(userId));
        nameInput.setSelection(nameInput.getText().length());

        TextView passwordLabel = new TextView(this);
        passwordLabel.setText("修改密码（不改请留空）");
        passwordLabel.setTextColor(getColor(R.color.muted));
        passwordLabel.setTextSize(13f);

        EditText oldInput = new EditText(this);
        oldInput.setHint("当前密码");
        oldInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText newInput = new EditText(this);
        newInput.setHint("新密码");
        newInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding / 2, padding, 0);
        layout.addView(accountName);
        layout.addView(nameInput);
        passwordLabel.setLayoutParams(topMarginParams(labelTop));
        layout.addView(passwordLabel);
        layout.addView(oldInput);
        layout.addView(newInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("编辑账号")
                .setView(layout)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> saveProfile(dialog, nameInput, oldInput, newInput)));
        dialog.show();
    }

    /**
     * Persist the edits from {@link #showEditProfileDialog}. Always saves the username (display
     * name). If either password field is filled, both are required, the current password is
     * verified, and a successful change forces a re-login. Validation errors keep the dialog open.
     */
    private void saveProfile(AlertDialog dialog, EditText nameInput, EditText oldInput, EditText newInput) {
        String newName = nameInput.getText().toString().trim();
        String oldPwd = oldInput.getText().toString();
        String newPwd = newInput.getText().toString();
        boolean wantsPasswordChange = !TextUtils.isEmpty(oldPwd) || !TextUtils.isEmpty(newPwd);

        if (wantsPasswordChange && (TextUtils.isEmpty(oldPwd) || TextUtils.isEmpty(newPwd))) {
            Toast.makeText(this, "修改密码需填写当前密码和新密码", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!dbHelper.updateDisplayName(userId, newName)) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!wantsPasswordChange) {
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refresh();
            return;
        }

        // Username is already saved; now attempt the password change. A wrong current password
        // keeps the dialog open so the user can retry without losing the username edit.
        if (dbHelper.changePassword(userId, oldPwd, newPwd)) {
            Toast.makeText(this, "密码已修改，请重新登录", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            goToLogin();
        } else {
            Toast.makeText(this, "当前密码不正确", Toast.LENGTH_SHORT).show();
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
        // Clear the saved session, otherwise LoginActivity's auto-login would immediately send the
        // user (with now-stale credentials) right back to the app instead of showing the login screen.
        getSharedPreferences("login", MODE_PRIVATE).edit()
                .remove("session_user_id")
                .remove("session_username")
                .apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
