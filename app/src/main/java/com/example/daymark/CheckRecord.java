package com.example.daymark;

public class CheckRecord {
    public long id;
    public long habitId;
    public String habitTitle;
    public String note;
    public long checkedAt;

    public CheckRecord(long id, long habitId, String habitTitle, String note, long checkedAt) {
        this.id = id;
        this.habitId = habitId;
        this.habitTitle = habitTitle;
        this.note = note;
        this.checkedAt = checkedAt;
    }
}
