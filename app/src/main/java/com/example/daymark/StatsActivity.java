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
    private static final int VIEW_WEEK = 0;
    private static final int VIEW_MONTH = 1;

    private DayMarkDbHelper dbHelper;
    private long userId = DayMarkDbHelper.NO_USER;
    private int boardMode = VIEW_WEEK;

    private Button weekBoardButton;
    private Button monthBoardButton;
    private TextView overviewText;
    private TextView weekText;
    private TextView compareText;
    private PieChartView pieChartView;
    private LinearLayout legendContainer;
    private TextView noCategoryText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        weekBoardButton = findViewById(R.id.weekBoardButton);
        monthBoardButton = findViewById(R.id.monthBoardButton);
        overviewText = findViewById(R.id.overviewText);
        weekText = findViewById(R.id.weekText);
        compareText = findViewById(R.id.compareText);
        pieChartView = findViewById(R.id.pieChartView);
        legendContainer = findViewById(R.id.legendContainer);
        noCategoryText = findViewById(R.id.noCategoryText);
        Button backButton = findViewById(R.id.backButton);

        weekBoardButton.setOnClickListener(v -> {
            boardMode = VIEW_WEEK;
            updateBoardButtons();
            loadBoard();
        });
        monthBoardButton.setOnClickListener(v -> {
            boardMode = VIEW_MONTH;
            updateBoardButtons();
            loadBoard();
        });
        backButton.setOnClickListener(v -> finish());

        updateBoardButtons();
        loadOverviewAndPie();
        loadBoard();
    }

    private void updateBoardButtons() {
        weekBoardButton.setBackgroundResource(boardMode == VIEW_WEEK
                ? R.drawable.bg_button_primary : R.drawable.bg_button_outline);
        monthBoardButton.setBackgroundResource(boardMode == VIEW_MONTH
                ? R.drawable.bg_button_primary : R.drawable.bg_button_outline);
        weekBoardButton.setTextColor(getColor(boardMode == VIEW_WEEK
                ? android.R.color.white : R.color.brand_green));
        monthBoardButton.setTextColor(getColor(boardMode == VIEW_MONTH
                ? android.R.color.white : R.color.brand_green));
    }

    private void loadOverviewAndPie() {
        AppExecutors.io().execute(() -> {
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
            double completionRate = habits.isEmpty() ? 0 : completedToday * 100.0 / habits.size();
            String overview = String.format(Locale.CHINA,
                    "总事件数：%d\n今日已完成：%d\n总打卡记录：%d\n今日完成率：%.1f%%\n最高连续打卡：%d 天",
                    habits.size(), completedToday, totalChecks, completionRate, bestStreak);
            List<CategoryStat> stats = dbHelper.getCategoryStats(userId);
            List<PieChartView.PieSlice> slices = PieChartView.createSlicesFromStats(stats);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                overviewText.setText(overview);
                bindPieChart(slices);
            });
        });
    }

    private void loadBoard() {
        final int modeSnapshot = boardMode;
        AppExecutors.io().execute(() -> {
            String boardTextValue = modeSnapshot == VIEW_MONTH
                    ? dbHelper.buildMonthSummary(userId)
                    : dbHelper.buildWeekSummary(userId);
            String compareTextValue = modeSnapshot == VIEW_MONTH
                    ? dbHelper.buildMonthCompare(userId)
                    : dbHelper.buildWeekCompare(userId);
            AppExecutors.main().execute(() -> {
                if (isFinishing() || modeSnapshot != boardMode) {
                    return;
                }
                weekText.setText(boardTextValue);
                compareText.setText(compareTextValue);
            });
        });
    }

    private void bindPieChart(List<PieChartView.PieSlice> slices) {
        if (slices.isEmpty()) {
            pieChartView.setVisibility(View.GONE);
            legendContainer.setVisibility(View.GONE);
            noCategoryText.setVisibility(View.VISIBLE);
            return;
        }
        pieChartView.setVisibility(View.VISIBLE);
        legendContainer.setVisibility(View.VISIBLE);
        noCategoryText.setVisibility(View.GONE);
        pieChartView.setData(slices);
        renderLegend(legendContainer, slices);
    }

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

            View colorBox = new View(this);
            colorBox.setBackgroundColor(slice.color);
            LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(
                    colorBoxSize, colorBoxSize);
            colorBox.setLayoutParams(colorParams);

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
}
