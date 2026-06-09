package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Month-at-a-glance check-in calendar. Each day cell shows how many distinct events were
 * checked in that day. Tapping a day (today or earlier) lets the user back-fill a check-in
 * for that date, since the normal check-in flow can only record "now". Future days are not
 * tappable. The ‹ / › buttons move between months.
 */
public class CalendarActivity extends Activity {
    private static final String[] WEEKDAY_HEADERS = {"一", "二", "三", "四", "五", "六", "日"};

    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;

    private GridView calendarGrid;
    private TextView monthText;
    private ArrayAdapter<String> adapter;
    private final List<String> cells = new ArrayList<>();
    /** Parallel to {@link #cells}: start-of-day for a day cell, or -1 for headers/blanks. */
    private final List<Long> cellDates = new ArrayList<>();

    /** First day of the month currently shown. */
    private final Calendar displayMonth = Calendar.getInstance(Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);

        calendarGrid = findViewById(R.id.calendarGrid);
        monthText = findViewById(R.id.monthText);
        Button prevMonthButton = findViewById(R.id.prevMonthButton);
        Button nextMonthButton = findViewById(R.id.nextMonthButton);
        Button backButton = findViewById(R.id.backButton);

        adapter = new ArrayAdapter<>(this, R.layout.item_calendar_day, R.id.dayText, cells);
        calendarGrid.setAdapter(adapter);

        displayMonth.set(Calendar.DAY_OF_MONTH, 1);
        render();

        calendarGrid.setOnItemClickListener((parent, view, position, id) -> onCellTapped(position));
        prevMonthButton.setOnClickListener(v -> {
            displayMonth.add(Calendar.MONTH, -1);
            render();
        });
        nextMonthButton.setOnClickListener(v -> {
            displayMonth.add(Calendar.MONTH, 1);
            render();
        });
        backButton.setOnClickListener(v -> finish());
    }

    /** Rebuild the grid (headers, leading blanks, day cells) for {@link #displayMonth}. */
    private void render() {
        cells.clear();
        cellDates.clear();

        for (String header : WEEKDAY_HEADERS) {
            cells.add(header);
            cellDates.add(-1L);
        }

        Calendar month = (Calendar) displayMonth.clone();
        month.set(Calendar.DAY_OF_MONTH, 1);
        monthText.setText(String.format(Locale.CHINA, "%d 年 %d 月",
                month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1));

        int firstDay = month.get(Calendar.DAY_OF_WEEK);
        int blanks = firstDay == Calendar.SUNDAY ? 6 : firstDay - 2;
        for (int i = 0; i < blanks; i++) {
            cells.add("");
            cellDates.add(-1L);
        }

        int maxDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= maxDay; day++) {
            month.set(Calendar.DAY_OF_MONTH, day);
            long dayStart = DateUtils.startOfDay(month.getTimeInMillis());
            int count = dbHelper.getCheckedHabitCountForDay(dayStart, userId);
            cells.add(day + "\n" + (count > 0 ? count + " 项" : "-"));
            cellDates.add(dayStart);
        }

        adapter.notifyDataSetChanged();
    }

    private void onCellTapped(int position) {
        if (position < 0 || position >= cellDates.size()) {
            return;
        }
        long dayStart = cellDates.get(position);
        if (dayStart < 0) {
            return; // Header or blank cell.
        }
        long todayStart = DateUtils.startOfDay(System.currentTimeMillis());
        if (dayStart > todayStart) {
            Toast.makeText(this, "不能给未来的日期补打卡", Toast.LENGTH_SHORT).show();
            return;
        }
        showHabitPicker(dayStart);
    }

    /** Pick which event to back-fill a check-in for on the tapped day. */
    private void showHabitPicker(long dayStart) {
        List<Habit> habits = dbHelper.getAllHabits(userId);
        if (habits.isEmpty()) {
            Toast.makeText(this, "还没有事件，先去添加打卡事件", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[habits.size()];
        for (int i = 0; i < habits.size(); i++) {
            titles[i] = habits.get(i).title;
        }
        new AlertDialog.Builder(this)
                .setTitle(DateUtils.formatDate(dayStart) + " 补打卡")
                .setItems(titles, (dialog, which) -> showNoteDialog(habits.get(which), dayStart))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showNoteDialog(Habit habit, long dayStart) {
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
                    if (dbHelper.addCheckForDay(habit.id, note, dayStart)) {
                        Toast.makeText(this, "已补打卡", Toast.LENGTH_SHORT).show();
                        render();
                    } else {
                        Toast.makeText(this, "补打卡失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
