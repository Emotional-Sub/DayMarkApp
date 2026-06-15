package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detail page for a single day, opened from the calendar. Lists every check-in recorded that
 * day and offers a "back-fill" button to add a check-in for the date (the normal flow can only
 * record "now"). The button is hidden for future days, which can't be back-filled.
 */
public class DayDetailActivity extends Activity {
    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;
    private long dayStart;

    private TextView summaryText;
    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_day_detail);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        dayStart = getIntent().getLongExtra("day_start", -1);
        if (userId == DayMarkDbHelper.NO_USER || dayStart < 0) {
            finish();
            return;
        }

        TextView titleText = findViewById(R.id.dayTitleText);
        summaryText = findViewById(R.id.daySummaryText);
        ListView recordList = findViewById(R.id.recordList);
        recordList.setEmptyView(findViewById(R.id.emptyView));
        Button addCheckButton = findViewById(R.id.addCheckButton);
        Button backButton = findViewById(R.id.backButton);

        titleText.setText(DateUtils.formatDate(dayStart));

        adapter = new RecordAdapter();
        recordList.setAdapter(adapter);

        // Future days can't be back-filled; hide the action there.
        long todayStart = DateUtils.startOfDay(System.currentTimeMillis());
        if (dayStart > todayStart) {
            addCheckButton.setVisibility(View.GONE);
        } else {
            addCheckButton.setOnClickListener(v -> showHabitPicker());
        }

        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecords();
    }

    private void loadRecords() {
        AppExecutors.io().execute(() -> {
            List<CheckRecord> records = dbHelper.getRecordsForDay(userId, dayStart);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                adapter.submitList(records);
                summaryText.setText(String.format(Locale.CHINA, "共 %d 条打卡记录", records.size()));
            });
        });
    }

    /** Pick which event to back-fill a check-in for on this day. */
    private void showHabitPicker() {
        AppExecutors.io().execute(() -> {
            // Only show habits that haven't been checked on this day yet
            List<Habit> uncheckedHabits = dbHelper.getUncheckedHabitsForDay(userId, dayStart);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                if (uncheckedHabits.isEmpty()) {
                    Toast.makeText(this, "当天所有事件都已打卡", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] titles = new String[uncheckedHabits.size()];
                for (int i = 0; i < uncheckedHabits.size(); i++) {
                    titles[i] = uncheckedHabits.get(i).title;
                }
                new AlertDialog.Builder(this)
                        .setTitle("补打卡")
                        .setItems(titles, (dialog, which) -> showNoteDialog(uncheckedHabits.get(which)))
                        .setNegativeButton("取消", null)
                        .show();
            });
        });
    }

    private void showNoteDialog(Habit habit) {
        EditText input = new EditText(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        input.setHint("当天完成了什么？可不填");
        input.setMinLines(2);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new AlertDialog.Builder(this)
                .setTitle("补打卡：" + habit.title)
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String note = input.getText().toString().trim();
                    // Back-fill touches the DB (getHabit + insert + update); run it off the UI
                    // thread and report back on the main thread.
                    AppExecutors.io().execute(() -> {
                        int result = dbHelper.addCheckForDay(habit.id, note, dayStart);
                        AppExecutors.main().execute(() -> {
                            if (isFinishing()) {
                                return;
                            }
                            if (result == DayMarkDbHelper.BACKFILL_ADDED) {
                                Toast.makeText(this, "已补打卡", Toast.LENGTH_SHORT).show();
                                loadRecords();
                            } else if (result == DayMarkDbHelper.BACKFILL_ALREADY_CHECKED) {
                                // The day was already credited; we only appended the note, no extra count.
                                Toast.makeText(this, "当天已有打卡，已追加备注", Toast.LENGTH_SHORT).show();
                                loadRecords();
                            } else {
                                Toast.makeText(this, "补打卡失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class RecordAdapter extends BaseAdapter {
        private List<CheckRecord> records = new ArrayList<>();

        void submitList(List<CheckRecord> data) {
            records = data;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return records.size();
        }

        @Override
        public CheckRecord getItem(int position) {
            return records.get(position);
        }

        @Override
        public long getItemId(int position) {
            return records.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(DayDetailActivity.this)
                        .inflate(R.layout.item_check_record, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            CheckRecord record = getItem(position);
            holder.titleText.setText(record.habitTitle);
            holder.timeText.setText(DateUtils.formatDateTime(record.checkedAt));
            if (TextUtils.isEmpty(record.note)) {
                holder.noteText.setText("（无备注）");
                holder.noteText.setTextColor(getColor(R.color.muted));
            } else {
                holder.noteText.setText(record.note);
                holder.noteText.setTextColor(getColor(R.color.ink));
            }
            return convertView;
        }
    }

    private static class ViewHolder {
        final TextView titleText;
        final TextView timeText;
        final TextView noteText;

        ViewHolder(View view) {
            titleText = view.findViewById(R.id.recordTitleText);
            timeText = view.findViewById(R.id.recordTimeText);
            noteText = view.findViewById(R.id.recordNoteText);
        }
    }
}
