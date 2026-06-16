package com.example.daymark;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
        PieChartView pieChartView = findViewById(R.id.pieChartView);
        LinearLayout legendContainer = findViewById(R.id.legendContainer);
        TextView noCategoryText = findViewById(R.id.noCategoryText);
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

        // Completion rate reuses the already-loaded habits/completedToday rather than re-querying.
        double completionRate = habits.isEmpty() ? 0 : completedToday * 100.0 / habits.size();
        overviewText.setText(String.format(Locale.CHINA,
                "总事件数：%d\n今日已完成：%d\n总打卡记录：%d\n今日完成率：%.1f%%\n最高连续打卡：%d 天",
                habits.size(), completedToday, totalChecks, completionRate, bestStreak));
        weekText.setText(dbHelper.buildWeekSummary(userId));

        // 加载分类统计饼状图
        loadCategoryPieChart(dbHelper, userId, pieChartView, legendContainer, noCategoryText);

        renderAchievements(achievementContainer, dbHelper.getAchievements(userId));
        backButton.setOnClickListener(v -> finish());
    }

    /**
     * 加载并显示分类打卡占比饼状图
     */
    private void loadCategoryPieChart(DayMarkDbHelper dbHelper, long userId,
                                     PieChartView pieChartView,
                                     LinearLayout legendContainer,
                                     TextView noCategoryText) {
        AppExecutors.io().execute(() -> {
            List<CategoryStat> stats = dbHelper.getCategoryStats(userId);
            List<PieChartView.PieSlice> slices = PieChartView.createSlicesFromStats(stats);

            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }

                if (slices.isEmpty()) {
                    // 没有数据，显示提示
                    pieChartView.setVisibility(View.GONE);
                    legendContainer.setVisibility(View.GONE);
                    noCategoryText.setVisibility(View.VISIBLE);
                } else {
                    // 有数据，显示饼图和图例
                    pieChartView.setVisibility(View.VISIBLE);
                    legendContainer.setVisibility(View.VISIBLE);
                    noCategoryText.setVisibility(View.GONE);

                    pieChartView.setData(slices);
                    renderLegend(legendContainer, slices);
                }
            });
        });
    }

    /**
     * 渲染饼图图例
     */
    private void renderLegend(LinearLayout container, List<PieChartView.PieSlice> slices) {
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int marginTop = (int) (8 * density);
        int colorBoxSize = (int) (16 * density);
        int paddingStart = (int) (12 * density);

        for (PieChartView.PieSlice slice : slices) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = marginTop;
            row.setLayoutParams(rowParams);

            // 颜色方块
            View colorBox = new View(this);
            colorBox.setBackgroundColor(slice.color);
            LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(
                    colorBoxSize, colorBoxSize);
            colorBox.setLayoutParams(colorParams);

            // 文本标签
            TextView label = new TextView(this);
            label.setText(String.format(Locale.CHINA, "%s: %d次 (%.1f%%)",
                    slice.label, slice.count, slice.percentage));
            label.setTextColor(getColor(R.color.ink));
            label.setTextSize(14f);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.leftMargin = paddingStart;
            label.setLayoutParams(labelParams);

            row.addView(colorBox);
            row.addView(label);
            container.addView(row);
        }
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
