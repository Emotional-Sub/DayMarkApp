package com.example.daymark;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements HabitAdapter.HabitActionListener {
    /** Sort modes, matching the order of the sort spinner entries. */
    private static final int SORT_CUSTOM = 0;
    private static final int SORT_DUE_FIRST = 1;
    private static final int SORT_STREAK = 2;
    private static final int SORT_CREATED = 3;

    /** Request code for the Android 13+ runtime notification permission prompt. */
    private static final int REQUEST_POST_NOTIFICATIONS = 30;

    private DayMarkDbHelper dbHelper;
    private HabitAdapter adapter;
    private RecyclerView habitList;
    private View emptyView;
    private TextView welcomeText;
    private TextView summaryText;
    private TextInputEditText searchEdit;
    private MaterialButton allButton;
    private MaterialButton todoButton;
    private MaterialButton doneTodayButton;
    private Spinner sortSpinner;
    private ItemTouchHelper itemTouchHelper;
    private int filterMode = 0;
    private int sortMode = SORT_CUSTOM;
    private long userId = DayMarkDbHelper.NO_USER;
    private String username;
    /** Bumped on every refresh; a background load only applies if it's still the latest request. */
    private int refreshGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DayMarkDbHelper(this);
        adapter = new HabitAdapter(this, dbHelper, this);

        welcomeText = findViewById(R.id.welcomeText);
        summaryText = findViewById(R.id.summaryText);
        searchEdit = findViewById(R.id.searchEdit);
        habitList = findViewById(R.id.habitList);
        emptyView = findViewById(R.id.emptyView);
        sortSpinner = findViewById(R.id.sortSpinner);
        MaterialButton addButton = findViewById(R.id.addButton);
        MaterialButton accountButton = findViewById(R.id.accountButton);
        MaterialButton calendarButton = findViewById(R.id.calendarButton);
        MaterialButton statsButton = findViewById(R.id.statsButton);
        MaterialButton historyButton = findViewById(R.id.historyButton);
        MaterialButton exportButton = findViewById(R.id.exportButton);
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

        // Android 13+ requires an explicit runtime grant before any notification (incl. reminders)
        // can be posted. Ask once on entry; if denied, reminders stay silent but the app still works.
        requestNotificationPermissionIfNeeded();

        habitList.setLayoutManager(new LinearLayoutManager(this));
        habitList.setAdapter(adapter);
        // Spacing between cards; ListView's dividerHeight has no RecyclerView equivalent.
        int gap = (int) (12 * getResources().getDisplayMetrics().density);
        habitList.addItemDecoration(new SpacingDecoration(gap));

        // Drag-to-reorder; only attached while a manual order makes sense (see updateDragEnabled).
        itemTouchHelper = new ItemTouchHelper(new ReorderCallback());

        setupSortSpinner(sortSpinner);

        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditHabitActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });
        accountButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("user_id", userId);
            intent.putExtra("username", username);
            startActivity(intent);
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
        historyButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
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

    private void setupSortSpinner(Spinner spinner) {
        updateSortSpinner();
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 在筛选模式下，位置需要调整（因为没有"自定义"选项）
                if (filterMode != 0) {
                    // 筛选模式下：0=待完成优先, 1=连续最久, 2=创建最新
                    sortMode = position + 1; // +1 跳过 SORT_CUSTOM
                } else {
                    // 全部模式下：0=自定义, 1=待完成优先, 2=连续最久, 3=创建最新
                    sortMode = position;
                }
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updateSortSpinner() {
        ArrayAdapter<String> sortAdapter;
        if (filterMode == 0) {
            // 全部模式：显示所有选项包括"自定义（拖拽）"
            sortAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item,
                    new String[]{"自定义（拖拽）", "待完成优先", "连续最久", "创建最新"});
            sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sortSpinner.setAdapter(sortAdapter);
            sortSpinner.setSelection(sortMode, false);
        } else {
            // 筛选模式：不显示"自定义（拖拽）"
            sortAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item,
                    new String[]{"待完成优先", "连续最久", "创建最新"});
            sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sortSpinner.setAdapter(sortAdapter);
            // sortMode - 1 因为跳过了 SORT_CUSTOM(0)
            int position = sortMode > 0 ? sortMode - 1 : 0;
            sortSpinner.setSelection(position, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /**
     * On Android 13 (API 33)+ notifications need an explicit runtime permission; without it every
     * reminder is silently dropped. Older versions grant it at install time, so we skip the prompt.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_POST_NOTIFICATIONS
                && (grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "未授予通知权限，打卡提醒将无法弹出", Toast.LENGTH_LONG).show();
        }
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

        // 使用 MaterialButton 的样式切换方式
        updateButtonStyle(allButton, mode == 0);
        updateButtonStyle(todoButton, mode == 1);
        updateButtonStyle(doneTodayButton, mode == 2);

        // 切换到筛选模式时，自动切换到"待完成优先"排序
        if (mode != 0 && sortMode == SORT_CUSTOM) {
            sortMode = SORT_DUE_FIRST;
            Toast.makeText(this, "已自动切换到待完成优先排序", Toast.LENGTH_SHORT).show();
        }

        // 更新排序下拉框（根据筛选模式显示不同的选项）
        updateSortSpinner();

        refresh();
    }

    private void updateButtonStyle(MaterialButton button, boolean isSelected) {
        if (isSelected) {
            // 选中状态：实心按钮
            button.setBackgroundColor(getColor(R.color.primary));
            button.setTextColor(getColor(R.color.onPrimary));
            button.setStrokeWidth(0);
        } else {
            // 未选中状态：轮廓按钮
            button.setBackgroundColor(getColor(android.R.color.transparent));
            button.setTextColor(getColor(R.color.primary));
            button.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
            button.setStrokeColor(getColorStateList(R.color.primary));
        }
    }

    private void refresh() {
        final String keyword = searchEdit == null ? "" : searchEdit.getText().toString().trim();
        final int generation = ++refreshGeneration;
        AppExecutors.io().execute(() -> {
            // All DB work happens here, off the UI thread.
            List<Habit> habits = dbHelper.searchHabits(keyword, filterMode, userId);
            applySort(habits);
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
            String summary = String.format(Locale.CHINA,
                    "共 %d 个事件，今日完成 %d 个，累计 %d 次，最高连续 %d 天",
                    allHabits.size(), completedToday, totalChecks, bestStreak);
            // Show the editable display name (falls back to the login username) in the greeting,
            // so a nickname changed on the profile page is reflected here after returning.
            final String displayName = dbHelper.getDisplayName(userId);
            AppExecutors.main().execute(() -> {
                // A newer refresh started while this one was running (e.g. fast typing); drop ours.
                if (generation != refreshGeneration || isFinishing()) {
                    return;
                }
                adapter.submitList(habits);
                emptyView.setVisibility(habits.isEmpty() ? View.VISIBLE : View.GONE);
                updateDragEnabled(keyword);
                summaryText.setText(summary);
                welcomeText.setText(TextUtils.isEmpty(displayName) ? "我的打卡" : displayName + " 的打卡");
            });
        });
    }

    /**
     * Reorder the already-filtered list in place. The DB returns rows in custom (sort_order)
     * order, so SORT_CUSTOM is a no-op; the others sort by runtime-computed fields.
     */
    private void applySort(List<Habit> habits) {
        switch (sortMode) {
            case SORT_DUE_FIRST:
                // Due-today habits first; ties keep the underlying custom order (stable sort).
                Collections.sort(habits, new Comparator<Habit>() {
                    @Override
                    public int compare(Habit a, Habit b) {
                        return Boolean.compare(b.isDueToday(), a.isDueToday());
                    }
                });
                break;
            case SORT_STREAK:
                Collections.sort(habits, new Comparator<Habit>() {
                    @Override
                    public int compare(Habit a, Habit b) {
                        return Integer.compare(b.streakDays, a.streakDays);
                    }
                });
                break;
            case SORT_CREATED:
                Collections.sort(habits, new Comparator<Habit>() {
                    @Override
                    public int compare(Habit a, Habit b) {
                        return Long.compare(b.createdAt, a.createdAt);
                    }
                });
                break;
            case SORT_CUSTOM:
            default:
                // Already in sort_order from the DB query.
                break;
        }
    }

    /**
     * Dragging is enabled when viewing the full unfiltered list ("all" filter, no search keyword).
     * If the user drags while in a non-custom sort mode, the sort automatically switches to custom
     * so the manual order is persisted.
     */
    private void updateDragEnabled(String keyword) {
        boolean canDrag = filterMode == 0 && TextUtils.isEmpty(keyword);
        itemTouchHelper.attachToRecyclerView(canDrag ? habitList : null);
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

    /** Even vertical gap between cards, applied as a bottom offset on every row. */
    private static class SpacingDecoration extends RecyclerView.ItemDecoration {
        private final int gap;

        SpacingDecoration(int gap) {
            this.gap = gap;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.bottom = gap;
        }
    }

    /** Long-press drag to reorder; persists the new order to the DB when the finger lifts. */
    private class ReorderCallback extends ItemTouchHelper.SimpleCallback {
        ReorderCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            adapter.onItemMove(viewHolder.getBindingAdapterPosition(),
                    target.getBindingAdapterPosition());
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            // Swipe disabled.
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            // Drag finished; write the new order so it survives navigation and restart.
            dbHelper.updateHabitOrder(adapter.currentOrderIds());

            // If the user dragged while in a non-custom sort mode, automatically switch to custom
            // sort so the manual order is preserved and visible on the next refresh.
            if (sortMode != SORT_CUSTOM) {
                sortMode = SORT_CUSTOM;
                Spinner sortSpinner = findViewById(R.id.sortSpinner);
                if (sortSpinner != null) {
                    sortSpinner.setSelection(sortMode, false);
                }
                Toast.makeText(MainActivity.this, "已切换到自定义排序", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
