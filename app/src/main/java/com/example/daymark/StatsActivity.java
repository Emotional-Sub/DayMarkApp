package com.example.daymark;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class StatsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        DayMarkDbHelper dbHelper = new DayMarkDbHelper(this);
        long userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        TextView overviewText = findViewById(R.id.overviewText);
        TextView weekText = findViewById(R.id.weekText);
        Button backButton = findViewById(R.id.backButton);

        List<Habit> habits = dbHelper.getAllHabits(userId);
        int totalChecks = dbHelper.getTotalRecordCount(userId);
        int bestStreak = 0;
        int completedToday = 0;
        for (Habit habit : habits) {
            bestStreak = Math.max(bestStreak, habit.streakDays);
            if (habit.isCheckedToday()) {
                completedToday++;
            }
        }

        overviewText.setText(String.format(Locale.CHINA,
                "总事件数：%d\n今日已完成：%d\n总打卡记录：%d\n今日完成率：%.1f%%\n最高连续打卡：%d 天",
                habits.size(), completedToday, totalChecks, dbHelper.getCompletionRateToday(userId), bestStreak));
        weekText.setText(dbHelper.buildWeekSummary(userId));
        backButton.setOnClickListener(v -> finish());
    }
}
