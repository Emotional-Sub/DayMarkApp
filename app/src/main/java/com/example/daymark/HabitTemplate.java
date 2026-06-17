package com.example.daymark;

public class HabitTemplate {
    public final String title;
    public final String content;
    public final String category;
    public final String timeText;
    public final int frequencyType;
    public final String frequencyDays;
    public final int frequencyCount;
    public final int targetDays;
    public final String reminderTime;

    public HabitTemplate(String title, String content, String category, String timeText,
                         int frequencyType, String frequencyDays, int frequencyCount,
                         int targetDays, String reminderTime) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.timeText = timeText;
        this.frequencyType = frequencyType;
        this.frequencyDays = frequencyDays;
        this.frequencyCount = frequencyCount;
        this.targetDays = targetDays;
        this.reminderTime = reminderTime;
    }
}
