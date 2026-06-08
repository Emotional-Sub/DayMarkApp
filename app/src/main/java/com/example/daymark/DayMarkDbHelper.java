package com.example.daymark;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DayMarkDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "daymark.db";
    private static final int DB_VERSION = 2;

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
                "category TEXT NOT NULL DEFAULT '学习'," +
                "reminder_time TEXT," +
                "check_count INTEGER NOT NULL DEFAULT 0," +
                "last_check_at INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE check_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "habit_id INTEGER NOT NULL," +
                "note TEXT," +
                "checked_at INTEGER NOT NULL)");
        insertDefaultData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS check_records");
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
        long readId = insertHabit(db, "晨间阅读", "每天阅读 20 分钟，记录一句喜欢的话。", "每天 07:30",
                "", "学习", "07:30", 3, now - DateUtils.DAY_MS, now);
        long sportId = insertHabit(db, "运动打卡", "完成一次散步、跑步或拉伸，让身体醒过来。", "每天 18:30",
                "", "运动", "18:30", 1, now, now + 1);
        insertRecord(db, readId, "读完一章，状态不错。", now - DateUtils.DAY_MS);
        insertRecord(db, readId, "今天继续阅读。", now - 2 * DateUtils.DAY_MS);
        insertRecord(db, sportId, "散步 30 分钟。", now);
    }

    private long insertHabit(SQLiteDatabase db, String title, String content, String timeText, String imageUri,
                             String category, String reminderTime, int checkCount, long lastCheckAt,
                             long createdAt) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("content", content);
        values.put("time_text", timeText);
        values.put("image_uri", imageUri);
        values.put("category", category);
        values.put("reminder_time", reminderTime);
        values.put("check_count", checkCount);
        values.put("last_check_at", lastCheckAt);
        values.put("created_at", createdAt);
        return db.insert("habits", null, values);
    }

    private long insertRecord(SQLiteDatabase db, long habitId, String note, long checkedAt) {
        ContentValues values = new ContentValues();
        values.put("habit_id", habitId);
        values.put("note", note);
        values.put("checked_at", checkedAt);
        return db.insert("check_records", null, values);
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
        return queryHabits(null, null);
    }

    public List<Habit> searchHabits(String keyword, int filterMode) {
        String where = null;
        String[] args = null;
        if (!TextUtils.isEmpty(keyword)) {
            where = "(title LIKE ? OR content LIKE ? OR category LIKE ?)";
            String like = "%" + keyword + "%";
            args = new String[]{like, like, like};
        }
        List<Habit> habits = queryHabits(where, args);
        ArrayList<Habit> filtered = new ArrayList<>();
        for (Habit habit : habits) {
            if (filterMode == 1 && habit.isCheckedToday()) {
                continue;
            }
            if (filterMode == 2 && !habit.isCheckedToday()) {
                continue;
            }
            filtered.add(habit);
        }
        return filtered;
    }

    private List<Habit> queryHabits(String where, String[] args) {
        ArrayList<Habit> habits = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("habits", null, where, args, null, null,
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

    public long addHabit(String title, String content, String timeText, String imageUri,
                         String category, String reminderTime) {
        return insertHabit(getWritableDatabase(), title, content, timeText, imageUri,
                category, reminderTime, 0, 0, System.currentTimeMillis());
    }

    public boolean updateHabit(long id, String title, String content, String timeText, String imageUri,
                               String category, String reminderTime) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("content", content);
        values.put("time_text", timeText);
        values.put("image_uri", imageUri);
        values.put("category", category);
        values.put("reminder_time", reminderTime);
        return getWritableDatabase().update("habits", values, "id=?",
                new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteHabit(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("check_records", "habit_id=?", new String[]{String.valueOf(id)});
        return db.delete("habits", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean markChecked(long id, String note) {
        Habit habit = getHabit(id);
        if (habit == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        insertRecord(db, id, note, now);

        ContentValues values = new ContentValues();
        values.put("check_count", habit.checkCount + 1);
        values.put("last_check_at", now);
        return db.update("habits", values, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean addNote(long habitId, String note) {
        if (getHabit(habitId) == null) {
            return false;
        }
        return insertRecord(getWritableDatabase(), habitId, note, System.currentTimeMillis()) != -1;
    }

    public List<CheckRecord> getAllRecords() {
        ArrayList<CheckRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT r.id, r.habit_id, h.title, r.note, r.checked_at " +
                "FROM check_records r LEFT JOIN habits h ON r.habit_id=h.id " +
                "ORDER BY r.checked_at DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                records.add(new CheckRecord(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2) == null ? "已删除事件" : cursor.getString(2),
                        cursor.getString(3) == null ? "" : cursor.getString(3),
                        cursor.getLong(4)
                ));
            }
        }
        return records;
    }

    public int getTotalRecordCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM check_records", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public int getCheckedHabitCountForDay(long dayStart) {
        SQLiteDatabase db = getReadableDatabase();
        long dayEnd = dayStart + DateUtils.DAY_MS;
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(DISTINCT habit_id) FROM check_records WHERE checked_at>=? AND checked_at<?",
                new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public String buildExportText() {
        StringBuilder builder = new StringBuilder();
        builder.append("DayMark 打卡记录导出\n\n");
        for (Habit habit : getAllHabits()) {
            builder.append("事件：").append(habit.title).append('\n');
            builder.append("分类：").append(habit.category).append('\n');
            builder.append("时间：").append(habit.timeText).append('\n');
            builder.append("提醒：").append(TextUtils.isEmpty(habit.reminderTime) ? "未设置" : habit.reminderTime).append('\n');
            builder.append("累计：").append(habit.checkCount).append(" 次，连续 ")
                    .append(habit.streakDays).append(" 天\n");
            builder.append("内容：").append(habit.content).append("\n\n");
        }
        builder.append("详细打卡记录\n");
        for (CheckRecord record : getAllRecords()) {
            builder.append(DateUtils.formatDateTime(record.checkedAt))
                    .append("  ")
                    .append(record.habitTitle);
            if (!TextUtils.isEmpty(record.note)) {
                builder.append("  备注：").append(record.note);
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private Habit readHabit(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        return new Habit(
                id,
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("content")),
                cursor.getString(cursor.getColumnIndexOrThrow("time_text")),
                cursor.getString(cursor.getColumnIndexOrThrow("image_uri")),
                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getString(cursor.getColumnIndexOrThrow("reminder_time")),
                cursor.getInt(cursor.getColumnIndexOrThrow("check_count")),
                cursor.getLong(cursor.getColumnIndexOrThrow("last_check_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                calculateStreakDays(id),
                getLastNote(id)
        );
    }

    private String getLastNote(long habitId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("check_records", new String[]{"note"}, "habit_id=? AND note<>''",
                new String[]{String.valueOf(habitId)}, null, null, "checked_at DESC", "1")) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return "";
        }
    }

    private int calculateStreakDays(long habitId) {
        Set<Long> days = new HashSet<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("check_records", new String[]{"checked_at"}, "habit_id=?",
                new String[]{String.valueOf(habitId)}, null, null, "checked_at DESC")) {
            while (cursor.moveToNext()) {
                days.add(DateUtils.startOfDay(cursor.getLong(0)));
            }
        }
        long cursorDay = DateUtils.startOfDay(System.currentTimeMillis());
        if (!days.contains(cursorDay)) {
            cursorDay -= DateUtils.DAY_MS;
        }
        int streak = 0;
        while (days.contains(cursorDay)) {
            streak++;
            cursorDay -= DateUtils.DAY_MS;
        }
        return streak;
    }

    public String buildWeekSummary() {
        StringBuilder builder = new StringBuilder("最近 7 天打卡情况\n");
        long today = DateUtils.startOfDay(System.currentTimeMillis());
        for (int i = 6; i >= 0; i--) {
            long day = today - i * DateUtils.DAY_MS;
            builder.append(DateUtils.formatMonthDay(day))
                    .append("：")
                    .append(getCheckedHabitCountForDay(day))
                    .append(" 个事件\n");
        }
        return builder.toString().trim();
    }

    public double getCompletionRateToday() {
        int habitCount = getAllHabits().size();
        if (habitCount == 0) {
            return 0;
        }
        int checked = getCheckedHabitCountForDay(DateUtils.startOfDay(System.currentTimeMillis()));
        return checked * 100.0 / habitCount;
    }

    public String normalizeCategory(String category) {
        return TextUtils.isEmpty(category) ? "学习" : category.trim();
    }

    public String normalizeReminder(String reminderTime) {
        if (TextUtils.isEmpty(reminderTime)) {
            return "";
        }
        return reminderTime.trim();
    }

    public String buildReminderMessage(Habit habit) {
        return String.format(Locale.CHINA, "%s 到时间啦：%s", habit.title, habit.timeText);
    }
}
