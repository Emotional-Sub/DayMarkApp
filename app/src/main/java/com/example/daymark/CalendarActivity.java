package com.example.daymark;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarActivity extends Activity {
    private static final String[] WEEKDAY_HEADERS = {"一", "二", "三", "四", "五", "六", "日"};

    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;

    private GridView calendarGrid;
    private TextView monthText;
    private ArrayAdapter<String> adapter;
    private final List<String> cells = new ArrayList<>();
    private final List<Long> cellDates = new ArrayList<>();
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

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        Calendar month = (Calendar) displayMonth.clone();
        month.set(Calendar.DAY_OF_MONTH, 1);
        final long monthStart = DateUtils.startOfDay(month.getTimeInMillis());
        final long nextMonthStart = DateUtils.addMonths(monthStart, 1);
        monthText.setText(String.format(Locale.CHINA, "%d 年 %d 月",
                month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1));

        AppExecutors.io().execute(() -> {
            Map<Long, Integer> counts = dbHelper.getCheckedHabitCountsByDay(userId, monthStart, nextMonthStart);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                buildMonthCells(month, counts);
            });
        });
    }

    private void buildMonthCells(Calendar month, Map<Long, Integer> counts) {
        cells.clear();
        cellDates.clear();

        for (String header : WEEKDAY_HEADERS) {
            cells.add(header);
            cellDates.add(-1L);
        }

        int firstDay = month.get(Calendar.DAY_OF_WEEK);
        int blanks = firstDay == Calendar.SUNDAY ? 6 : firstDay - 2;
        for (int i = 0; i < blanks; i++) {
            cells.add("");
            cellDates.add(-1L);
        }

        int maxDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar dayCursor = (Calendar) month.clone();
        for (int day = 1; day <= maxDay; day++) {
            dayCursor.set(Calendar.DAY_OF_MONTH, day);
            long dayStart = DateUtils.startOfDay(dayCursor.getTimeInMillis());
            Integer count = counts.get(dayStart);
            cells.add(day + "\n" + ((count != null && count > 0) ? count + " 项" : "-"));
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
            return;
        }
        Intent intent = new Intent(this, DayDetailActivity.class);
        intent.putExtra("user_id", userId);
        intent.putExtra("day_start", dayStart);
        startActivity(intent);
    }
}
