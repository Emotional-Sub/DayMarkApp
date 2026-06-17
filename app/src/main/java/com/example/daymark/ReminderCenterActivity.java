package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReminderCenterActivity extends Activity {
    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;
    private TextView summaryText;
    private ReminderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_center);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        if (userId == DayMarkDbHelper.NO_USER) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        summaryText = findViewById(R.id.reminderSummaryText);
        ListView reminderList = findViewById(R.id.reminderList);
        reminderList.setEmptyView(findViewById(R.id.emptyView));
        adapter = new ReminderAdapter();
        reminderList.setAdapter(adapter);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReminders();
    }

    private void loadReminders() {
        AppExecutors.io().execute(() -> {
            List<Habit> reminders = dbHelper.getHabitsWithReminder(userId);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                adapter.submitList(reminders);
                summaryText.setText(String.format(Locale.CHINA,
                        "当前共有 %d 个提醒", reminders.size()));
            });
        });
    }

    private void editReminder(Habit habit) {
        Intent intent = new Intent(this, EditHabitActivity.class);
        intent.putExtra("habit_id", habit.id);
        intent.putExtra("user_id", userId);
        startActivity(intent);
    }

    private void confirmCloseReminder(Habit habit) {
        new AlertDialog.Builder(this)
                .setTitle("关闭提醒")
                .setMessage("确定关闭“" + habit.title + "”的提醒吗？")
                .setPositiveButton("关闭", (dialog, which) -> {
                    AppExecutors.io().execute(() -> {
                        boolean ok = dbHelper.updateReminderTime(habit.id, "");
                        ReminderReceiver.cancel(this, habit.id);
                        AppExecutors.main().execute(() -> {
                            if (ok) {
                                Toast.makeText(this, "提醒已关闭", Toast.LENGTH_SHORT).show();
                                loadReminders();
                            } else {
                                Toast.makeText(this, "关闭失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class ReminderAdapter extends BaseAdapter {
        private List<Habit> data = new ArrayList<>();

        void submitList(List<Habit> reminders) {
            data = reminders == null ? new ArrayList<>() : reminders;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Habit getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return data.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(ReminderCenterActivity.this)
                        .inflate(R.layout.item_reminder, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Habit habit = getItem(position);
            holder.titleText.setText(habit.title);
            holder.timeText.setText(habit.reminderTime);
            holder.metaText.setText("分类：" + habit.category + "  |  " + habit.frequencyLabel()
                    + (TextUtils.isEmpty(habit.timeText) ? "" : "  |  打卡时间 " + habit.timeText));
            holder.editButton.setOnClickListener(v -> editReminder(habit));
            holder.closeButton.setOnClickListener(v -> confirmCloseReminder(habit));
            return convertView;
        }
    }

    private static class ViewHolder {
        final TextView titleText;
        final TextView timeText;
        final TextView metaText;
        final Button editButton;
        final Button closeButton;

        ViewHolder(View view) {
            titleText = view.findViewById(R.id.reminderTitleText);
            timeText = view.findViewById(R.id.reminderTimeText);
            metaText = view.findViewById(R.id.reminderMetaText);
            editButton = view.findViewById(R.id.editReminderButton);
            closeButton = view.findViewById(R.id.closeReminderButton);
        }
    }
}
