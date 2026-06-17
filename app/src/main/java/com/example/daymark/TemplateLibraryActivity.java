package com.example.daymark;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class TemplateLibraryActivity extends Activity {
    private static final HabitTemplate[] TEMPLATES = new HabitTemplate[]{
            new HabitTemplate("晨间阅读", "每天阅读 20 分钟，并写下一句值得记录的话。", "学习",
                    "07:30", Habit.FREQ_DAILY, "", 0, 21, "07:30"),
            new HabitTemplate("运动打卡", "完成跑步、散步或拉伸，让身体保持活力。", "运动",
                    "18:30", Habit.FREQ_WEEKLY_COUNT, "", 3, 30, "18:30"),
            new HabitTemplate("背单词", "每天积累新单词，巩固旧词。", "学习",
                    "21:00", Habit.FREQ_DAILY, "", 0, 60, "21:00"),
            new HabitTemplate("喝水提醒", "按时补水，维持良好状态。", "健康",
                    "10:00", Habit.FREQ_DAILY, "", 0, 14, "10:00"),
            new HabitTemplate("居家整理", "保持桌面和房间整洁。", "生活",
                    "20:30", Habit.FREQ_WEEKLY_DAYS, "1,3,6", 0, 12, "20:30"),
            new HabitTemplate("项目复盘", "每周回顾本周工作进展与问题。", "工作",
                    "19:00", Habit.FREQ_WEEKLY_DAYS, "5", 0, 16, "19:00")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_template_library);

        long userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);
        LinearLayout templateContainer = findViewById(R.id.templateContainer);
        MaterialButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        float density = getResources().getDisplayMetrics().density;
        for (HabitTemplate template : TEMPLATES) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = (int) (12 * density);
            card.setLayoutParams(cardParams);
            card.setCardElevation(2 * density);
            card.setRadius(12 * density);
            card.setContentPadding((int) (16 * density), (int) (16 * density),
                    (int) (16 * density), (int) (16 * density));

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(this);
            title.setText(template.title);
            title.setTextAppearance(this, R.style.TextAppearance_DayMark_Title_Medium);

            TextView meta = new TextView(this);
            meta.setText(template.category + "  |  " + template.timeText + "  |  " + templateLabel(template));
            meta.setTextColor(getColor(R.color.onSurfaceVariant));
            meta.setTextSize(13f);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            metaParams.topMargin = (int) (6 * density);
            meta.setLayoutParams(metaParams);

            TextView desc = new TextView(this);
            desc.setText(template.content);
            desc.setTextColor(getColor(R.color.onSurface));
            desc.setTextSize(14f);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = (int) (10 * density);
            desc.setLayoutParams(descParams);

            MaterialButton useButton = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            useButton.setText("使用此模板");
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            buttonParams.topMargin = (int) (14 * density);
            useButton.setLayoutParams(buttonParams);
            useButton.setOnClickListener(v -> openEditor(userId, template));

            content.addView(title);
            content.addView(meta);
            content.addView(desc);
            content.addView(useButton);
            card.addView(content);
            templateContainer.addView(card);
        }
    }

    private void openEditor(long userId, HabitTemplate template) {
        Intent intent = new Intent(this, EditHabitActivity.class);
        intent.putExtra("user_id", userId);
        intent.putExtra("prefill_title", template.title);
        intent.putExtra("prefill_content", template.content);
        intent.putExtra("prefill_category", template.category);
        intent.putExtra("prefill_time", template.timeText);
        intent.putExtra("prefill_frequency_type", template.frequencyType);
        intent.putExtra("prefill_frequency_days", template.frequencyDays);
        intent.putExtra("prefill_frequency_count", template.frequencyCount);
        intent.putExtra("prefill_target_days", template.targetDays);
        intent.putExtra("prefill_reminder_time", template.reminderTime);
        startActivity(intent);
    }

    private String templateLabel(HabitTemplate template) {
        if (template.frequencyType == Habit.FREQ_WEEKLY_DAYS) {
            return "每周指定日";
        }
        if (template.frequencyType == Habit.FREQ_WEEKLY_COUNT) {
            return "每周 " + template.frequencyCount + " 次";
        }
        return "每天";
    }
}
