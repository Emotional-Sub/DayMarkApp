package com.example.daymark;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CalendarActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        DayMarkDbHelper dbHelper = new DayMarkDbHelper(this);
        GridView calendarGrid = findViewById(R.id.calendarGrid);
        Button backButton = findViewById(R.id.backButton);

        List<String> days = new ArrayList<>();
        days.add("一");
        days.add("二");
        days.add("三");
        days.add("四");
        days.add("五");
        days.add("六");
        days.add("日");

        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDay = calendar.get(Calendar.DAY_OF_WEEK);
        int blanks = firstDay == Calendar.SUNDAY ? 6 : firstDay - 2;
        for (int i = 0; i < blanks; i++) {
            days.add("");
        }

        int maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= maxDay; day++) {
            calendar.set(Calendar.DAY_OF_MONTH, day);
            long dayStart = DateUtils.startOfDay(calendar.getTimeInMillis());
            int count = dbHelper.getCheckedHabitCountForDay(dayStart);
            days.add(day + "\n" + (count > 0 ? count + " 项" : "-"));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_calendar_day, R.id.dayText, days);
        calendarGrid.setAdapter(adapter);
        backButton.setOnClickListener(v -> finish());
    }
}
