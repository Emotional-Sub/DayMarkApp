package com.example.daymark;

public class Habit {
    public long id;
    public String title;
    public String content;
    public String timeText;
    public String imageUri;
    public int checkCount;
    public long lastCheckAt;
    public long createdAt;

    public Habit(long id, String title, String content, String timeText, String imageUri,
                 int checkCount, long lastCheckAt, long createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timeText = timeText;
        this.imageUri = imageUri;
        this.checkCount = checkCount;
        this.lastCheckAt = lastCheckAt;
        this.createdAt = createdAt;
    }
}
