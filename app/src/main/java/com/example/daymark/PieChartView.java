package com.example.daymark;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单的饼状图View，用于展示分类打卡次数占比，带有展开动画效果
 */
public class PieChartView extends View {
    private List<PieSlice> slices = new ArrayList<>();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();
    private float animationProgress = 0f; // 动画进度 0.0 ~ 1.0
    private ValueAnimator animator;

    // 预定义的颜色方案
    private static final int[] COLORS = {
            0xFF4CAF50, // 学习 - 绿色
            0xFF2196F3, // 运动 - 蓝色
            0xFFFF9800, // 生活 - 橙色
            0xFFE91E63, // 工作 - 粉色
            0xFF9C27B0, // 健康 - 紫色
            0xFF00BCD4, // 其他 - 青色
    };

    public static class PieSlice {
        public String label;
        public int count;
        public float percentage;
        public int color;

        public PieSlice(String label, int count, float percentage, int color) {
            this.label = label;
            this.count = count;
            this.percentage = percentage;
            this.color = color;
        }
    }

    public PieChartView(Context context) {
        super(context);
        init();
    }

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PieChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<PieSlice> slices) {
        this.slices = slices;
        startAnimation();
    }

    /**
     * 启动扇形展开动画
     */
    private void startAnimation() {
        // 取消之前的动画
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        // 创建动画：从0到1，持续800ms
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate(); // 触发重绘
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 清理动画，避免内存泄漏
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (slices == null || slices.isEmpty()) {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        // 计算饼图圆心和半径
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.min(width, height) / 2 - 20; // 留20px边距

        rect.set(centerX - radius, centerY - radius,
                 centerX + radius, centerY + radius);

        // 绘制饼图扇形，应用动画进度
        float startAngle = -90f; // 从12点方向开始
        for (PieSlice slice : slices) {
            paint.setColor(slice.color);
            float sweepAngle = slice.percentage * 360f / 100f * animationProgress; // 应用动画进度
            canvas.drawArc(rect, startAngle, sweepAngle, true, paint);
            startAngle += sweepAngle;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // 保持正方形
        int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }

    /**
     * 根据分类统计数据创建饼图切片
     */
    public static List<PieSlice> createSlicesFromStats(List<CategoryStat> stats) {
        List<PieSlice> slices = new ArrayList<>();

        if (stats == null || stats.isEmpty()) {
            return slices;
        }

        // 计算总打卡次数
        int totalChecks = 0;
        for (CategoryStat stat : stats) {
            totalChecks += stat.checkCount;
        }

        if (totalChecks == 0) {
            return slices;
        }

        // 创建切片
        int colorIndex = 0;
        for (CategoryStat stat : stats) {
            float percentage = stat.checkCount * 100f / totalChecks;
            int color = COLORS[colorIndex % COLORS.length];
            slices.add(new PieSlice(stat.category, stat.checkCount, percentage, color));
            colorIndex++;
        }

        return slices;
    }
}
