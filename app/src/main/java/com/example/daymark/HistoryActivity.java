package com.example.daymark;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Timeline of every check-in record for the logged-in user, newest first. A spinner filters the
 * list down to a single event. Records are read once on resume and filtered in memory, since the
 * full set is already small enough to hold and the filter only narrows it.
 */
public class HistoryActivity extends Activity {
    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;

    private TextView summaryText;
    private RecordAdapter adapter;
    private Spinner habitFilterSpinner;

    private List<CheckRecord> allRecords = new ArrayList<>();
    /** Spinner positions → habit id; index 0 is the "all events" entry (id = -1). */
    private final List<Long> filterHabitIds = new ArrayList<>();
    private boolean spinnerInitialized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        if (userId == DayMarkDbHelper.NO_USER) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        summaryText = findViewById(R.id.historySummaryText);
        habitFilterSpinner = findViewById(R.id.habitFilterSpinner);
        ListView recordList = findViewById(R.id.recordList);
        recordList.setEmptyView(findViewById(R.id.emptyView));

        adapter = new RecordAdapter();
        recordList.setAdapter(adapter);

        habitFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilter(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        allRecords = dbHelper.getAllRecords(userId);
        buildFilterOptions();
        applyFilter(habitFilterSpinner.getSelectedItemPosition());
    }

    /**
     * Rebuild the spinner from the events present in the current records. Preserves the selected
     * habit across reloads when it still exists, otherwise falls back to "all events".
     */
    private void buildFilterOptions() {
        long previouslySelectedId = -1;
        int selectedPos = habitFilterSpinner.getSelectedItemPosition();
        if (spinnerInitialized && selectedPos >= 0 && selectedPos < filterHabitIds.size()) {
            previouslySelectedId = filterHabitIds.get(selectedPos);
        }

        // Distinct events in encounter order (records are already newest-first).
        Map<Long, String> habitsInRecords = new LinkedHashMap<>();
        for (CheckRecord record : allRecords) {
            if (!habitsInRecords.containsKey(record.habitId)) {
                habitsInRecords.put(record.habitId, record.habitTitle);
            }
        }

        filterHabitIds.clear();
        List<String> labels = new ArrayList<>();
        filterHabitIds.add(-1L);
        labels.add("全部事件");
        for (Map.Entry<Long, String> entry : habitsInRecords.entrySet()) {
            filterHabitIds.add(entry.getKey());
            labels.add(entry.getValue());
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        habitFilterSpinner.setAdapter(spinnerAdapter);
        spinnerInitialized = true;

        int restorePos = filterHabitIds.indexOf(previouslySelectedId);
        habitFilterSpinner.setSelection(restorePos < 0 ? 0 : restorePos);
    }

    private void applyFilter(int position) {
        long habitId = (position >= 0 && position < filterHabitIds.size())
                ? filterHabitIds.get(position) : -1L;
        List<CheckRecord> shown = new ArrayList<>();
        for (CheckRecord record : allRecords) {
            if (habitId == -1L || record.habitId == habitId) {
                shown.add(record);
            }
        }
        adapter.submitList(shown);
        summaryText.setText(String.format(Locale.CHINA, "共 %d 条打卡记录", shown.size()));
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
                convertView = LayoutInflater.from(HistoryActivity.this)
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
