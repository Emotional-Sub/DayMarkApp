package com.example.daymark;

import android.app.Activity;
import android.os.Bundle;
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

        for (int i = 0; i < achievements.size(); i++) {
            Achievement achievement = achievements.get(i);
            MaterialCardView card = AchievementBadgeRenderer.create(this, achievement, i, true);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.bottomMargin = (int) (12 * density);
            card.setLayoutParams(cardParams);
            achievementsContainer.addView(card);
        }
    }
}
