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
 * GitHub-style yearly contribution grid: 53 columns (weeks) by 7 rows (Mon..Sun), each cell
 * shaded by how many check-ins happened that day. The data is supplied as a map of
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

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int weeks = DEFAULT_WEEKS;
    private float cellGap;
    private float cellRadius;

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
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        // Derive a square cell size from the available width so the whole grid fits horizontally.
        float cell = (availableWidth - (weeks - 1) * cellGap) / weeks;
        if (cell < 1f) {
            cell = 1f;
        }
        int height = Math.round(ROWS * cell + (ROWS - 1) * cellGap)
                + getPaddingTop() + getPaddingBottom();
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float cell = (availableWidth - (weeks - 1) * cellGap) / weeks;
        if (cell < 1f) {
            return;
        }
        long today = DateUtils.startOfDay(System.currentTimeMillis());
        RectF rect = new RectF();
        for (int col = 0; col < weeks; col++) {
            for (int row = 0; row < ROWS; row++) {
                long day = firstCellDay + ((long) col * 7 + row) * DateUtils.DAY_MS;
                if (day > today) {
                    continue; // Future days within the current week are left blank.
                }
                float left = getPaddingLeft() + col * (cell + cellGap);
                float top = getPaddingTop() + row * (cell + cellGap);
                rect.set(left, top, left + cell, top + cell);
                Integer count = counts.get(day);
                cellPaint.setColor(LEVEL_COLORS[levelFor(count == null ? 0 : count)]);
                canvas.drawRoundRect(rect, cellRadius, cellRadius, cellPaint);
            }
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
