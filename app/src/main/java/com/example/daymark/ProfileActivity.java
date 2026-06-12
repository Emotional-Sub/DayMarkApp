package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
    private TextView overviewText;
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
        overviewText = findViewById(R.id.profileOverviewText);
        categoryStatsText = findViewById(R.id.categoryStatsText);
        heatmapView = findViewById(R.id.heatmapView);
        achievementContainer = findViewById(R.id.achievementContainer);
        Button editProfileButton = findViewById(R.id.editProfileButton);
        Button changePasswordButton = findViewById(R.id.changePasswordButton);
        Button deleteAccountButton = findViewById(R.id.deleteAccountButton);
        Button backButton = findViewById(R.id.backButton);

        accountText.setText("登录账号：" + (username == null ? "" : username));
        editProfileButton.setOnClickListener(v -> showEditProfileDialog());
        changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());
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
        overviewText.setText(String.format(Locale.CHINA,
                "事件总数：%d\n今日完成：%d\n累计打卡：%d 次\n最高连续：%d 天",
                habits.size(), completedToday, totalRecords, bestStreak));

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

    /** Edit the display name only; the login username is shown read-only for context. */
    private void showEditProfileDialog() {
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        EditText nameInput = new EditText(this);
        nameInput.setHint("显示名称");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setText(dbHelper.getDisplayName(userId));
        nameInput.setSelection(nameInput.getText().length());

        TextView accountHint = new TextView(this);
        accountHint.setText("登录账号：" + (username == null ? "" : username) + "（不可修改）");
        accountHint.setTextColor(getColor(R.color.muted));
        accountHint.setTextSize(13f);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding / 2, padding, 0);
        layout.addView(nameInput);
        layout.addView(accountHint);

        new AlertDialog.Builder(this)
                .setTitle("编辑账号")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    if (dbHelper.updateDisplayName(userId, newName)) {
                        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                        refresh();
                    } else {
                        Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showChangePasswordDialog() {
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        EditText oldInput = new EditText(this);
        oldInput.setHint("当前密码");
        oldInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText newInput = new EditText(this);
        newInput.setHint("新密码");
        newInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding / 2, padding, 0);
        layout.addView(oldInput);
        layout.addView(newInput);

        new AlertDialog.Builder(this)
                .setTitle("修改密码")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String oldPwd = oldInput.getText().toString();
                    String newPwd = newInput.getText().toString();
                    if (TextUtils.isEmpty(oldPwd) || TextUtils.isEmpty(newPwd)) {
                        Toast.makeText(this, "请填写当前密码和新密码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (dbHelper.changePassword(userId, oldPwd, newPwd)) {
                        Toast.makeText(this, "密码已修改，请重新登录", Toast.LENGTH_SHORT).show();
                        goToLogin();
                    } else {
                        Toast.makeText(this, "当前密码不正确", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
