package com.example.daymark;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Collections;
import java.util.Map;

/**
 * GitHub-style yearly contribution grid: columns are weeks, rows are Mon..Sun, each cell shaded
 * by how many check-ins happened that day. A left gutter labels a few weekdays and a top gutter
 * labels months at the column where each new month begins. The data is supplied as a map of
 * start-of-day timestamp to count (see {@link DayMarkDbHelper#getDailyCheckCounts}); the view
 * lays out the most recent {@code weeks} columns ending on the current week.
 */
public class HeatmapView extends View {
    private static final int ROWS = 7;
    private static final int DEFAULT_WEEKS = 27;

    // Five intensity buckets, light (no activity) to brand green (busiest day).
    private static final int[] LEVEL_COLORS = {
            Color.parseColor("#E9EDE6"),
            Color.parseColor("#C3DBCB"),
            Color.parseColor("#8FC1A4"),
            Color.parseColor("#549E7C"),
            Color.parseColor("#2F7D68"),
    };

    /** Weekday labels per row (Mon..Sun); blank rows are left unlabeled to avoid clutter. */
    private static final String[] WEEKDAY_LABELS = {"一", "", "三", "", "五", "", "日"};

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int weeks = DEFAULT_WEEKS;
    private float cellGap;
    private float cellRadius;
    private float leftGutter;
    private float topGutter;

    private Map<Long, Integer> counts = Collections.emptyMap();
    private long firstCellDay;

    public HeatmapView(Context context) {
        super(context);
        init();
    }

    public HeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HeatmapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        cellGap = 3f * density;
        cellRadius = 2.5f * density;
        cellPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(Color.parseColor("#9AA59B"));
        labelPaint.setTextSize(10f * density);
        // Left gutter fits a single CJK weekday glyph plus a little breathing room; the top gutter
        // holds one line of month text above the grid.
        leftGutter = labelPaint.measureText("一") + 4f * density;
        topGutter = labelPaint.getTextSize() + 4f * density;
    }

    /**
     * Provide the daily counts and how many trailing weeks to display. The grid's last column is
     * the current week (Mon-based); {@code firstCellDay} is computed as the Monday {@code weeks-1}
     * weeks before this week.
     */
    public void setData(Map<Long, Integer> dailyCounts, int weeksToShow) {
        this.counts = dailyCounts == null ? Collections.<Long, Integer>emptyMap() : dailyCounts;
        this.weeks = Math.max(1, weeksToShow);
        long thisWeekStart = DateUtils.startOfWeek(System.currentTimeMillis());
        this.firstCellDay = thisWeekStart - (long) (this.weeks - 1) * 7 * DateUtils.DAY_MS;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight() - Math.round(leftGutter);
        // Derive a square cell size from the width left after the weekday gutter.
        float cell = (availableWidth - (weeks - 1) * cellGap) / weeks;
        if (cell < 1f) {
            cell = 1f;
        }
        int height = Math.round(topGutter + ROWS * cell + (ROWS - 1) * cellGap)
                + getPaddingTop() + getPaddingBottom();
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight() - Math.round(leftGutter);
        float cell = (availableWidth - (weeks - 1) * cellGap) / weeks;
        if (cell < 1f) {
            return;
        }
        float gridLeft = getPaddingLeft() + leftGutter;
        float gridTop = getPaddingTop() + topGutter;
        float step = cell + cellGap;

        drawWeekdayLabels(canvas, gridLeft, gridTop, cell);
        drawMonthLabels(canvas, gridLeft, step, cell);

        long today = DateUtils.startOfDay(System.currentTimeMillis());
        RectF rect = new RectF();
        for (int col = 0; col < weeks; col++) {
            for (int row = 0; row < ROWS; row++) {
                long day = firstCellDay + ((long) col * 7 + row) * DateUtils.DAY_MS;
                if (day > today) {
                    continue; // Future days within the current week are left blank.
                }
                float left = gridLeft + col * step;
                float top = gridTop + row * step;
                rect.set(left, top, left + cell, top + cell);
                Integer count = counts.get(day);
                cellPaint.setColor(LEVEL_COLORS[levelFor(count == null ? 0 : count)]);
                canvas.drawRoundRect(rect, cellRadius, cellRadius, cellPaint);
            }
        }
    }

    /** Weekday labels down the left gutter, vertically centered on their row. */
    private void drawWeekdayLabels(Canvas canvas, float gridLeft, float gridTop, float cell) {
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float step = cell + cellGap;
        labelPaint.setTextAlign(Paint.Align.LEFT);
        for (int row = 0; row < ROWS; row++) {
            String label = WEEKDAY_LABELS[row];
            if (label.isEmpty()) {
                continue;
            }
            float rowCenter = gridTop + row * step + cell / 2f;
            float baseline = rowCenter - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, getPaddingLeft(), baseline, labelPaint);
        }
    }

    /**
     * Month labels across the top gutter. A label is drawn above the first column whose top
     * cell (Monday) falls in a new month, as long as it doesn't crowd the previous label.
     */
    private void drawMonthLabels(Canvas canvas, float gridLeft, float step, float cell) {
        labelPaint.setTextAlign(Paint.Align.LEFT);
        float baseline = getPaddingTop() + labelPaint.getTextSize();
        int lastMonth = -1;
        float lastLabelRight = -Float.MAX_VALUE;
        for (int col = 0; col < weeks; col++) {
            long colDay = firstCellDay + (long) col * 7 * DateUtils.DAY_MS;
            int month = DateUtils.monthOfYear(colDay);
            if (month == lastMonth) {
                continue;
            }
            lastMonth = month;
            float x = gridLeft + col * step;
            // Skip if it would overlap the previous month label.
            if (x < lastLabelRight) {
                continue;
            }
            String text = month + "月";
            canvas.drawText(text, x, baseline, labelPaint);
            lastLabelRight = x + labelPaint.measureText(text) + cell;
        }
    }

    private int levelFor(int count) {
        if (count <= 0) {
            return 0;
        }
        if (count == 1) {
            return 1;
        }
        if (count == 2) {
            return 2;
        }
        if (count <= 4) {
            return 3;
        }
        return 4;
    }
}
