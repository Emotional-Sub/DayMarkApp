package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements HabitAdapter.HabitActionListener {
    private DayMarkDbHelper dbHelper;
    private HabitAdapter adapter;
    private TextView summaryText;
    private EditText searchEdit;
    private Button allButton;
    private Button todoButton;
    private Button doneTodayButton;
    private int filterMode = 0;
    private long userId = DayMarkDbHelper.NO_USER;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DayMarkDbHelper(this);
        adapter = new HabitAdapter(this, dbHelper, this);

        TextView welcomeText = findViewById(R.id.welcomeText);
        summaryText = findViewById(R.id.summaryText);
        searchEdit = findViewById(R.id.searchEdit);
        ListView habitList = findViewById(R.id.habitList);
        habitList.setEmptyView(findViewById(R.id.emptyView));
        Button addButton = findViewById(R.id.addButton);
        Button accountButton = findViewById(R.id.accountButton);
        Button logoutButton = findViewById(R.id.logoutButton);
        Button calendarButton = findViewById(R.id.calendarButton);
        Button statsButton = findViewById(R.id.statsButton);
        Button exportButton = findViewById(R.id.exportButton);
        allButton = findViewById(R.id.allButton);
        todoButton = findViewById(R.id.todoButton);
        doneTodayButton = findViewById(R.id.doneTodayButton);

        username = getIntent().getStringExtra("username");
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        if (userId == DayMarkDbHelper.NO_USER) {
            // No valid session (e.g. launched without logging in); send back to login.
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        welcomeText.setText(username == null ? "我的打卡" : username + " 的打卡");
        habitList.setAdapter(adapter);

        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditHabitActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });
        accountButton.setOnClickListener(v -> showAccountMenu());
        logoutButton.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        calendarButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CalendarActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });
        statsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, StatsActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });
        exportButton.setOnClickListener(v -> exportReport());

        allButton.setOnClickListener(v -> setFilterMode(0));
        todoButton.setOnClickListener(v -> setFilterMode(1));
        doneTodayButton.setOnClickListener(v -> setFilterMode(2));
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onEdit(Habit habit) {
        Intent intent = new Intent(this, EditHabitActivity.class);
        intent.putExtra("habit_id", habit.id);
        startActivity(intent);
    }

    @Override
    public void onChanged() {
        refresh();
    }

    private void setFilterMode(int mode) {
        filterMode = mode;
        allButton.setBackgroundResource(mode == 0 ? R.drawable.bg_button_primary : R.drawable.bg_button_outline);
        todoButton.setBackgroundResource(mode == 1 ? R.drawable.bg_button_primary : R.drawable.bg_button_outline);
        doneTodayButton.setBackgroundResource(mode == 2 ? R.drawable.bg_button_primary : R.drawable.bg_button_outline);
        allButton.setTextColor(getColor(mode == 0 ? android.R.color.white : R.color.brand_green));
        todoButton.setTextColor(getColor(mode == 1 ? android.R.color.white : R.color.brand_green));
        doneTodayButton.setTextColor(getColor(mode == 2 ? android.R.color.white : R.color.brand_green));
        refresh();
    }

    private void refresh() {
        String keyword = searchEdit == null ? "" : searchEdit.getText().toString().trim();
        List<Habit> habits = dbHelper.searchHabits(keyword, filterMode, userId);
        adapter.submitList(habits);

        List<Habit> allHabits = dbHelper.getAllHabits(userId);
        int totalChecks = 0;
        int completedToday = 0;
        int bestStreak = 0;
        for (Habit habit : allHabits) {
            totalChecks += habit.checkCount;
            if (habit.isCheckedToday()) {
                completedToday++;
            }
            bestStreak = Math.max(bestStreak, habit.streakDays);
        }
        summaryText.setText(String.format(Locale.CHINA,
                "共 %d 个事件，今日完成 %d 个，累计 %d 次，最高连续 %d 天",
                allHabits.size(), completedToday, totalChecks, bestStreak));
    }

    private void showAccountMenu() {
        new AlertDialog.Builder(this)
                .setTitle("账号设置")
                .setItems(new String[]{"修改密码", "删除账号"}, (dialog, which) -> {
                    if (which == 0) {
                        showChangePasswordDialog();
                    } else {
                        confirmDeleteAccount();
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
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
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
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportReport() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) {
            dir = getFilesDir();
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "导出目录创建失败", Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(dir, "DayMark_export_" + System.currentTimeMillis() + ".txt");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(dbHelper.buildExportText(userId).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "已导出：" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        }
    }
}
