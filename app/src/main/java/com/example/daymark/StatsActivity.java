package com.example.daymark;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
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
        LinearLayout achievementContainer = findViewById(R.id.achievementContainer);
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

        renderAchievements(achievementContainer, dbHelper.getAchievements(userId));
        backButton.setOnClickListener(v -> finish());
    }

    /** Build one card per achievement; unlocked cards use the brand color, locked ones are muted. */
    private void renderAchievements(LinearLayout container, List<Achievement> achievements) {
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (14 * density);
        int marginTop = (int) (10 * density);
        int unlockedColor = getColor(R.color.brand_green);
        int mutedColor = getColor(R.color.muted);

        for (Achievement achievement : achievements) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding(padding, padding, padding, padding);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = marginTop;
            card.setLayoutParams(cardParams);
            card.setAlpha(achievement.unlocked ? 1f : 0.5f);

            TextView title = new TextView(this);
            title.setText((achievement.unlocked ? "🏅 " : "🔒 ") + achievement.title);
            title.setTextColor(achievement.unlocked ? unlockedColor : mutedColor);
            title.setTextSize(16f);
            title.setGravity(Gravity.START);
            title.getPaint().setFakeBoldText(true);

            TextView desc = new TextView(this);
            desc.setText(achievement.description + (achievement.unlocked ? "（已解锁）" : "（未解锁）"));
            desc.setTextColor(mutedColor);
            desc.setTextSize(13f);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = (int) (4 * density);
            desc.setLayoutParams(descParams);

            card.addView(title);
            card.addView(desc);
            container.addView(card);
        }
    }
}
