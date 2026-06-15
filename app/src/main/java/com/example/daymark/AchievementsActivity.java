package com.example.daymark;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class AchievementsActivity extends Activity {
    private DayMarkDbHelper dbHelper;
    private long userId;

    private TextView unlockedCountText;
    private TextView totalCountText;
    private LinearLayout achievementsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);

        dbHelper = new DayMarkDbHelper(this);
        userId = getIntent().getLongExtra("user_id", DayMarkDbHelper.NO_USER);

        if (userId == DayMarkDbHelper.NO_USER) {
            finish();
            return;
        }

        unlockedCountText = findViewById(R.id.unlockedCountText);
        totalCountText = findViewById(R.id.totalCountText);
        achievementsContainer = findViewById(R.id.achievementsContainer);
        MaterialButton backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());

        loadAchievements();
    }

    private void loadAchievements() {
        List<Achievement> achievements = dbHelper.getAchievements(userId);
        int unlockedCount = 0;

        for (Achievement achievement : achievements) {
            if (achievement.unlocked) {
                unlockedCount++;
            }
        }

        unlockedCountText.setText("已获得: " + unlockedCount);
        totalCountText.setText("总计: " + achievements.size());

        renderAchievements(achievements);
    }

    private void renderAchievements(List<Achievement> achievements) {
        achievementsContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (Achievement achievement : achievements) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.bottomMargin = (int) (12 * density);
            card.setLayoutParams(cardParams);
            card.setRadius(12 * density);
            card.setCardElevation(2 * density);
            card.setContentPadding(
                    (int) (16 * density),
                    (int) (16 * density),
                    (int) (16 * density),
                    (int) (16 * density)
            );

            // 未解锁的勋章降低透明度
            card.setAlpha(achievement.unlocked ? 1f : 0.5f);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);

            // 标题行（图标 + 标题）
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView icon = new TextView(this);
            icon.setText(achievement.unlocked ? "🏅" : "🔒");
            icon.setTextSize(32f);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            iconParams.rightMargin = (int) (12 * density);
            icon.setLayoutParams(iconParams);

            LinearLayout textContainer = new LinearLayout(this);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            TextView title = new TextView(this);
            title.setText(achievement.title);
            title.setTextColor(getColor(achievement.unlocked ? R.color.primary : R.color.onSurfaceVariant));
            title.setTextSize(18f);
            title.getPaint().setFakeBoldText(true);

            TextView status = new TextView(this);
            status.setText(achievement.unlocked ? "✓ 已解锁" : "未解锁");
            status.setTextColor(getColor(achievement.unlocked ? R.color.secondary : R.color.onSurfaceVariant));
            status.setTextSize(13f);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            statusParams.topMargin = (int) (4 * density);
            status.setLayoutParams(statusParams);

            textContainer.addView(title);
            textContainer.addView(status);

            titleRow.addView(icon);
            titleRow.addView(textContainer);

            // 描述
            TextView desc = new TextView(this);
            desc.setText(achievement.description);
            desc.setTextColor(getColor(R.color.onSurfaceVariant));
            desc.setTextSize(14f);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            descParams.topMargin = (int) (12 * density);
            desc.setLayoutParams(descParams);

            content.addView(titleRow);
            content.addView(desc);

            card.addView(content);
            achievementsContainer.addView(card);
        }
    }
}
