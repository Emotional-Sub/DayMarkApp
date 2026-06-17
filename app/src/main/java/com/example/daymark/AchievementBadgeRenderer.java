package com.example.daymark;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AchievementBadgeRenderer {
    private static final Map<String, BadgeStyle> STYLES = createStyles();

    private AchievementBadgeRenderer() {
    }

    public static MaterialCardView create(Context context, Achievement achievement, int index, boolean wide) {
        float density = context.getResources().getDisplayMetrics().density;
        BadgeStyle style = styleFor(achievement, index);

        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(14 * density);
        card.setStrokeWidth((int) (1.5f * density));
        card.setStrokeColor(achievement.unlocked ? style.borderColor : Color.parseColor("#D1D5DB"));
        card.setCardElevation(2 * density);
        card.setCardBackgroundColor(achievement.unlocked ? style.surfaceColor : Color.parseColor("#F7F8FA"));
        card.setContentPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (14 * density));
        card.setAlpha(achievement.unlocked ? 1f : 0.78f);

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);

        TextView ribbon = new TextView(context);
        ribbon.setText(achievement.unlocked ? style.ribbonText : "未解锁");
        ribbon.setTextSize(11f);
        ribbon.setTypeface(Typeface.DEFAULT_BOLD);
        ribbon.setTextColor(Color.WHITE);
        ribbon.setGravity(Gravity.CENTER);
        ribbon.setPadding((int) (10 * density), (int) (5 * density), (int) (10 * density), (int) (5 * density));
        GradientDrawable ribbonBg = new GradientDrawable();
        ribbonBg.setColor(achievement.unlocked ? style.ribbonColor : Color.parseColor("#9CA3AF"));
        ribbonBg.setCornerRadius(999f);
        ribbon.setBackground(ribbonBg);
        shell.addView(ribbon);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (12 * density);
        shell.addView(row, rowParams);

        TextView emblem = new TextView(context);
        emblem.setText(achievement.unlocked ? style.emoji : "🔒");
        emblem.setTextSize(26f);
        emblem.setGravity(Gravity.CENTER);
        int size = (int) ((wide ? 58 : 52) * density);
        LinearLayout.LayoutParams emblemParams = new LinearLayout.LayoutParams(size, size);
        emblemParams.rightMargin = (int) (10 * density);
        GradientDrawable emblemBg = new GradientDrawable();
        emblemBg.setColor(achievement.unlocked ? style.emblemBgColor : Color.parseColor("#E5E7EB"));
        if (style.shapeMode == 0) {
            emblemBg.setShape(GradientDrawable.OVAL);
        } else if (style.shapeMode == 1) {
            emblemBg.setCornerRadius(18 * density);
        } else {
            emblemBg.setCornerRadii(new float[]{24 * density, 24 * density, 8 * density, 8 * density, 24 * density, 24 * density, 8 * density, 8 * density});
        }
        emblemBg.setStroke((int) (1.5f * density), achievement.unlocked ? style.borderColor : Color.parseColor("#D1D5DB"));
        emblem.setBackground(emblemBg);
        row.addView(emblem, emblemParams);

        LinearLayout textGroup = new LinearLayout(context);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        textGroup.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText(achievement.title);
        title.setTextSize(wide ? 16f : 15f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(achievement.unlocked ? style.titleColor : Color.parseColor("#5F6B7A"));

        TextView status = new TextView(context);
        status.setText(achievement.unlocked ? "已点亮" : "未解锁");
        status.setTextSize(12f);
        status.setTextColor(achievement.unlocked ? style.titleColor : Color.parseColor("#7B8794"));
        status.setPadding(0, (int) (4 * density), 0, 0);

        textGroup.addView(title);
        textGroup.addView(status);
        row.addView(textGroup);

        TextView desc = new TextView(context);
        desc.setText(achievement.description);
        desc.setTextSize(wide ? 13f : 12.5f);
        desc.setTextColor(achievement.unlocked ? style.descriptionColor : Color.parseColor("#7B8794"));
        desc.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = (int) (8 * density);
        shell.addView(desc, descParams);

        View line = new View(context);
        GradientDrawable lineBg = new GradientDrawable();
        lineBg.setColor(achievement.unlocked ? style.accentColor : Color.parseColor("#D7DDE7"));
        lineBg.setCornerRadius(999f);
        line.setBackground(lineBg);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (4 * density));
        lineParams.topMargin = (int) (12 * density);
        shell.addView(line, lineParams);

        card.addView(shell);
        return card;
    }

    private static BadgeStyle styleFor(Achievement achievement, int index) {
        BadgeStyle style = STYLES.get(achievement.title);
        if (style != null) {
            return style;
        }
        BadgeStyle[] values = STYLES.values().toArray(new BadgeStyle[0]);
        return values[index % values.length];
    }

    private static Map<String, BadgeStyle> createStyles() {
        Map<String, BadgeStyle> map = new LinkedHashMap<>();
        map.put("初次打卡", new BadgeStyle("✨", "新星", "#FFF8E1", "#F59E0B", "#FDE68A", "#7C4A03", "#8A5A00", "#FFFDF5", "#A16207", 0));
        map.put("初创事件", new BadgeStyle("🌱", "起步", "#EEFCEB", "#2F9E44", "#A7F3D0", "#1F5F2F", "#2B8A3E", "#F7FFF8", "#166534", 1));
        map.put("连续三天", new BadgeStyle("🔥", "连击", "#FFF1F0", "#E76F51", "#F9C0B0", "#8E3B2E", "#D94841", "#FFF8F6", "#9A3412", 2));
        map.put("坚持一周", new BadgeStyle("📅", "周冠军", "#EEF5FF", "#3B82F6", "#BBD7FF", "#1F4F9C", "#2563EB", "#F7FBFF", "#1D4ED8", 1));
        map.put("多样化", new BadgeStyle("🎨", "探索", "#FFF7ED", "#FB923C", "#FED7AA", "#9A4D14", "#EA580C", "#FFFCF8", "#C2410C", 0));
        map.put("多面手", new BadgeStyle("🧩", "全能", "#F5F3FF", "#8B5CF6", "#DDD6FE", "#5B21B6", "#7C3AED", "#FBFAFF", "#6D28D9", 2));
        map.put("五十次", new BadgeStyle("🏃", "里程碑", "#ECFEFF", "#06B6D4", "#A5F3FC", "#155E75", "#0891B2", "#F3FFFF", "#0E7490", 0));
        map.put("坚持一月", new BadgeStyle("🌙", "月度", "#F8F5FF", "#7C6EE6", "#DCD2FF", "#4C3FAF", "#6D5BD0", "#FCFBFF", "#5B4FC0", 1));
        map.put("百次达成", new BadgeStyle("💯", "百次", "#FFF3F1", "#EF4444", "#FECACA", "#991B1B", "#DC2626", "#FFFAFA", "#B91C1C", 2));
        map.put("目标达成", new BadgeStyle("🎯", "达成", "#EFFCF6", "#10B981", "#A7F3D0", "#065F46", "#059669", "#F7FFFB", "#047857", 0));
        map.put("事件达人", new BadgeStyle("🛠️", "达人", "#F6F7FB", "#64748B", "#CBD5E1", "#334155", "#475569", "#FCFDFF", "#475569", 1));
        map.put("坚持百日", new BadgeStyle("👑", "百日王冠", "#FFF7D6", "#D4A017", "#FDE68A", "#7A5300", "#CA8A04", "#FFFDF3", "#A16207", 0));
        map.put("五百次", new BadgeStyle("💎", "宝石", "#EEF9FF", "#0EA5E9", "#BAE6FD", "#0C4A6E", "#0284C7", "#F8FDFF", "#0369A1", 2));
        map.put("全能选手", new BadgeStyle("🪄", "全能", "#FDF2F8", "#EC4899", "#FBCFE8", "#831843", "#DB2777", "#FFF9FC", "#BE185D", 1));
        map.put("目标规划师", new BadgeStyle("🧠", "策略", "#F4F8FF", "#4F46E5", "#C7D2FE", "#312E81", "#4338CA", "#FAFBFF", "#3730A3", 0));
        map.put("坚持一年", new BadgeStyle("🏆", "年度", "#FFF8E7", "#C08400", "#FDE68A", "#78350F", "#CA8A04", "#FFFCF1", "#92400E", 2));
        map.put("千次里程碑", new BadgeStyle("🚀", "千次", "#EEFDF9", "#14B8A6", "#99F6E4", "#115E59", "#0F766E", "#F7FFFD", "#0F766E", 1));
        map.put("事件大师", new BadgeStyle("🌟", "大师", "#FFF4E6", "#F97316", "#FDBA74", "#9A3412", "#EA580C", "#FFFBF5", "#C2410C", 0));
        return map;
    }

    private static class BadgeStyle {
        final String emoji;
        final String ribbonText;
        final int surfaceColor;
        final int accentColor;
        final int emblemBgColor;
        final int titleColor;
        final int borderColor;
        final int ribbonColor;
        final int descriptionColor;
        final int shapeMode;

        BadgeStyle(String emoji, String ribbonText, String surfaceHex, String accentHex,
                   String emblemHex, String titleHex, String borderHex, String ribbonHex,
                   String descriptionHex, int shapeMode) {
            this.emoji = emoji;
            this.ribbonText = ribbonText;
            this.surfaceColor = Color.parseColor(surfaceHex);
            this.accentColor = Color.parseColor(accentHex);
            this.emblemBgColor = Color.parseColor(emblemHex);
            this.titleColor = Color.parseColor(titleHex);
            this.borderColor = Color.parseColor(borderHex);
            this.ribbonColor = Color.parseColor(ribbonHex);
            this.descriptionColor = Color.parseColor(descriptionHex);
            this.shapeMode = shapeMode;
        }
    }
}
