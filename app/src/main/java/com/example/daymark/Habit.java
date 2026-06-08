package com.example.daymark;

public class Habit {
    public long id;
    public String title;
    public String content;
    public String timeText;
    public String imageUri;
    public String category;
    public String reminderTime;
    public int checkCount;
    public long lastCheckAt;
    public long createdAt;
    public int streakDays;
    public String lastNote;

    public Habit(long id, String title, String content, String timeText, String imageUri,
                 String category, String reminderTime, int checkCount, long lastCheckAt,
                 long createdAt, int streakDays, String lastNote) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timeText = timeText;
        this.imageUri = imageUri;
        this.category = category;
        this.reminderTime = reminderTime;
        this.checkCount = checkCount;
        this.lastCheckAt = lastCheckAt;
        this.createdAt = createdAt;
        this.streakDays = streakDays;
        this.lastNote = lastNote;
    }

    public boolean isCheckedToday() {
        return DateUtils.isToday(lastCheckAt);
    }
}
