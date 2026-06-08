package com.example.daymark;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity implements HabitAdapter.HabitActionListener {
    private DayMarkDbHelper dbHelper;
    private HabitAdapter adapter;
    private TextView summaryText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DayMarkDbHelper(this);
        adapter = new HabitAdapter(this, dbHelper, this);

        TextView welcomeText = findViewById(R.id.welcomeText);
        summaryText = findViewById(R.id.summaryText);
        ListView habitList = findViewById(R.id.habitList);
        Button addButton = findViewById(R.id.addButton);
        Button logoutButton = findViewById(R.id.logoutButton);

        String username = getIntent().getStringExtra("username");
        welcomeText.setText(username == null ? "我的打卡" : username + " 的打卡");
        habitList.setAdapter(adapter);

        addButton.setOnClickListener(v -> startActivity(new Intent(this, EditHabitActivity.class)));
        logoutButton.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onEdit(Habit habit) {
        Intent intent = new Intent(this, EditHabitActivity.class);
        intent.putExtra("habit_id", habit.id);
        startActivity(intent);
    }

    @Override
    public void onChanged() {
        refresh();
    }

    private void refresh() {
        List<Habit> habits = dbHelper.getAllHabits();
        adapter.submitList(habits);
        int totalChecks = 0;
        for (Habit habit : habits) {
            totalChecks += habit.checkCount;
        }
        summaryText.setText("共 " + habits.size() + " 个事件，累计打卡 " + totalChecks + " 次");
    }
}
