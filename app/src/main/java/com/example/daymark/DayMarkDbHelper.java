package com.example.daymark;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DayMarkDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "daymark.db";
    private static final int DB_VERSION = 1;

    public DayMarkDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL)");
        db.execSQL("CREATE TABLE habits (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "time_text TEXT NOT NULL," +
                "image_uri TEXT," +
                "check_count INTEGER NOT NULL DEFAULT 0," +
                "last_check_at INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL)");
        insertDefaultData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS habits");
        db.execSQL("DROP TABLE IF EXISTS users");
        onCreate(db);
    }

    private void insertDefaultData(SQLiteDatabase db) {
        ContentValues user = new ContentValues();
        user.put("username", "demo");
        user.put("password", "123456");
        db.insert("users", null, user);

        long now = System.currentTimeMillis();
        insertHabit(db, "晨间阅读", "每天阅读 20 分钟，记录一句喜欢的话。", "每天 07:30", "", 3, 0, now);
        insertHabit(db, "运动打卡", "完成一次散步、跑步或拉伸，让身体醒过来。", "每天 18:30", "", 1, 0, now + 1);
    }

    private long insertHabit(SQLiteDatabase db, String title, String content, String timeText, String imageUri,
                             int checkCount, long lastCheckAt, long createdAt) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("content", content);
        values.put("time_text", timeText);
        values.put("image_uri", imageUri);
        values.put("check_count", checkCount);
        values.put("last_check_at", lastCheckAt);
        values.put("created_at", createdAt);
        return db.insert("habits", null, values);
    }

    public boolean login(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("users", new String[]{"id"},
                "username=? AND password=?", new String[]{username, password},
                null, null, null)) {
            return cursor.moveToFirst();
        }
    }

    public boolean register(String username, String password) {
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", password);
        return getWritableDatabase().insert("users", null, values) != -1;
    }

    public List<Habit> getAllHabits() {
        ArrayList<Habit> habits = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("habits", null, null, null, null, null,
                "created_at DESC, id DESC")) {
            while (cursor.moveToNext()) {
                habits.add(readHabit(cursor));
            }
        }
        return habits;
    }

    public Habit getHabit(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("habits", null, "id=?", new String[]{String.valueOf(id)},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return readHabit(cursor);
            }
            return null;
        }
    }

    public long addHabit(String title, String content, String timeText, String imageUri) {
        return insertHabit(getWritableDatabase(), title, content, timeText, imageUri,
                0, 0, System.currentTimeMillis());
    }

    public boolean updateHabit(long id, String title, String content, String timeText, String imageUri) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("content", content);
        values.put("time_text", timeText);
        values.put("image_uri", imageUri);
        return getWritableDatabase().update("habits", values, "id=?",
                new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteHabit(long id) {
        return getWritableDatabase().delete("habits", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean markChecked(long id) {
        Habit habit = getHabit(id);
        if (habit == null) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put("check_count", habit.checkCount + 1);
        values.put("last_check_at", System.currentTimeMillis());
        return getWritableDatabase().update("habits", values, "id=?",
                new String[]{String.valueOf(id)}) > 0;
    }

    private Habit readHabit(Cursor cursor) {
        return new Habit(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("content")),
                cursor.getString(cursor.getColumnIndexOrThrow("time_text")),
                cursor.getString(cursor.getColumnIndexOrThrow("image_uri")),
                cursor.getInt(cursor.getColumnIndexOrThrow("check_count")),
                cursor.getLong(cursor.getColumnIndexOrThrow("last_check_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        );
    }
}
