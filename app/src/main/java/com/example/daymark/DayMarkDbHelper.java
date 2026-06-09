package com.example.daymark;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DayMarkDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "daymark.db";
    private static final int DB_VERSION = 5;

    /** Returned by {@link #login} / {@link #register} when there is no matching or valid user. */
    public static final long NO_USER = -1;

    public DayMarkDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL," +
                "salt TEXT)");
        db.execSQL("CREATE TABLE habits (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL DEFAULT 1," +
                "title TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "time_text TEXT NOT NULL," +
                "image_uri TEXT," +
                "category TEXT NOT NULL DEFAULT '学习'," +
                "reminder_time TEXT," +
                "check_count INTEGER NOT NULL DEFAULT 0," +
                "last_check_at INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "frequency_type INTEGER NOT NULL DEFAULT 0," +
                "frequency_days TEXT," +
                "frequency_count INTEGER NOT NULL DEFAULT 0," +
                "target_days INTEGER NOT NULL DEFAULT 0," +
                "sort_order INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE check_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "habit_id INTEGER NOT NULL," +
                "note TEXT," +
                "checked_at INTEGER NOT NULL)");
        insertDefaultData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v3 introduced per-user habits. Add the column without dropping existing data;
        // pre-existing habits are assigned to the first user (the seeded demo account).
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE habits ADD COLUMN user_id INTEGER NOT NULL DEFAULT 1");
            } catch (SQLException e) {
                // Column already present or the table predates this schema; rebuild as a fallback.
                db.execSQL("DROP TABLE IF EXISTS check_records");
                db.execSQL("DROP TABLE IF EXISTS habits");
                db.execSQL("DROP TABLE IF EXISTS users");
                onCreate(db);
                return; // onCreate already builds the latest schema with seeded data.
            }
        }
        // v4 added per-habit frequency/goal columns and salted password hashing.
        if (oldVersion < 4) {
            addColumnQuietly(db, "habits", "frequency_type INTEGER NOT NULL DEFAULT 0");
            addColumnQuietly(db, "habits", "frequency_days TEXT");
            addColumnQuietly(db, "habits", "frequency_count INTEGER NOT NULL DEFAULT 0");
            addColumnQuietly(db, "habits", "target_days INTEGER NOT NULL DEFAULT 0");
            addColumnQuietly(db, "users", "salt TEXT");
            migratePlaintextPasswords(db);
        }
        // v5 added a manual sort_order column. Seed it from id so existing habits keep a stable,
        // deterministic order until the user reorders them.
        if (oldVersion < 5) {
            addColumnQuietly(db, "habits", "sort_order INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE habits SET sort_order = id");
        }
    }

    private void addColumnQuietly(SQLiteDatabase db, String table, String columnDef) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
        } catch (SQLException ignored) {
            // Column already exists; safe to continue the upgrade.
        }
    }

    /**
     * Convert any user rows still holding a plaintext password (no salt) into a salted hash.
     * Reads everything first, then writes, so the cursor is closed before the updates run.
     */
    private void migratePlaintextPasswords(SQLiteDatabase db) {
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<String> plaintexts = new ArrayList<>();
        try (Cursor cursor = db.query("users", new String[]{"id", "password", "salt"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String salt = cursor.isNull(2) ? "" : cursor.getString(2);
                if (TextUtils.isEmpty(salt)) {
                    ids.add(cursor.getLong(0));
                    plaintexts.add(cursor.getString(1) == null ? "" : cursor.getString(1));
                }
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            String salt = PasswordUtils.newSalt();
            ContentValues values = new ContentValues();
            values.put("salt", salt);
            values.put("password", PasswordUtils.hash(plaintexts.get(i), salt));
            db.update("users", values, "id=?", new String[]{String.valueOf(ids.get(i))});
        }
    }

    private void insertDefaultData(SQLiteDatabase db) {
        String salt = PasswordUtils.newSalt();
        ContentValues user = new ContentValues();
        user.put("username", "demo");
        user.put("password", PasswordUtils.hash("123456", salt));
        user.put("salt", salt);
        long demoId = db.insert("users", null, user);
        if (demoId == NO_USER) {
            demoId = 1;
        }

        long now = System.currentTimeMillis();
        long readId = insertHabit(db, demoId, "晨间阅读", "每天阅读 20 分钟，记录一句喜欢的话。", "每天 07:30",
                "", "学习", "07:30", 3, now - DateUtils.DAY_MS, now,
                Habit.FREQ_DAILY, "", 0, 21);
        long sportId = insertHabit(db, demoId, "运动打卡", "完成一次散步、跑步或拉伸，让身体醒过来。", "每周三次",
                "", "运动", "18:30", 1, now, now + 1,
                Habit.FREQ_WEEKLY_COUNT, "", 3, 0);
        insertRecord(db, readId, "读完一章，状态不错。", now - DateUtils.DAY_MS);
        insertRecord(db, readId, "今天继续阅读。", now - 2 * DateUtils.DAY_MS);
        insertRecord(db, sportId, "散步 30 分钟。", now);
    }

    private long insertHabit(SQLiteDatabase db, long userId, String title, String content, String timeText,
                             String imageUri, String category, String reminderTime, int checkCount,
                             long lastCheckAt, long createdAt, int frequencyType, String frequencyDays,
                             int frequencyCount, int targetDays) {
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("title", title);
        values.put("content", content);
        values.put("time_text", timeText);
        values.put("image_uri", imageUri);
        values.put("category", category);
        values.put("reminder_time", reminderTime);
        values.put("check_count", checkCount);
        values.put("last_check_at", lastCheckAt);
        values.put("created_at", createdAt);
        values.put("frequency_type", frequencyType);
        values.put("frequency_days", frequencyDays);
        values.put("frequency_count", frequencyCount);
        values.put("target_days", targetDays);
        // New habits go to the end of the user's manual order.
        values.put("sort_order", nextSortOrder(db, userId));
        return db.insert("habits", null, values);
    }

    /** One past the user's current highest sort_order, so a new habit lands at the bottom. */
    private long nextSortOrder(SQLiteDatabase db, long userId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM habits WHERE user_id=?",
                new String[]{String.valueOf(userId)})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 1;
        }
    }

    private long insertRecord(SQLiteDatabase db, long habitId, String note, long checkedAt) {
        ContentValues values = new ContentValues();
        values.put("habit_id", habitId);
        values.put("note", note);
        values.put("checked_at", checkedAt);
        return db.insert("check_records", null, values);
    }

    /**
     * @return the matching user's id, or {@link #NO_USER} when the credentials are invalid.
     */
    public long login(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("users", new String[]{"id", "password", "salt"},
                "username=?", new String[]{username}, null, null, null)) {
            if (cursor.moveToFirst()) {
                String storedHash = cursor.getString(1);
                String salt = cursor.isNull(2) ? "" : cursor.getString(2);
                if (PasswordUtils.matches(password, salt, storedHash)) {
                    return cursor.getLong(0);
                }
            }
            return NO_USER;
        }
    }

    /**
     * @return the new user's id, or {@link #NO_USER} when the username already exists.
     */
    public long register(String username, String password) {
        String salt = PasswordUtils.newSalt();
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", PasswordUtils.hash(password, salt));
        values.put("salt", salt);
        return getWritableDatabase().insert("users", null, values);
    }

    public List<Habit> getAllHabits(long userId) {
        return queryHabits(userId, null, null);
    }

    /**
     * Every habit (across all users) that has a reminder time set. Used at boot to re-register
     * alarms, which AlarmManager drops on reboot; there is no logged-in user at that point.
     */
    public List<Habit> getHabitsWithReminder() {
        ArrayList<Habit> habits = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("habits", null, "reminder_time IS NOT NULL AND reminder_time<>''",
                null, null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                habits.add(readHabit(cursor));
            }
        }
        return habits;
    }

    public List<Habit> searchHabits(String keyword, int filterMode, long userId) {
        String where = null;
        String[] args = null;
        if (!TextUtils.isEmpty(keyword)) {
            where = "(title LIKE ? OR content LIKE ? OR category LIKE ?)";
            String like = "%" + keyword + "%";
            args = new String[]{like, like, like};
        }
        List<Habit> habits = queryHabits(userId, where, args);
        ArrayList<Habit> filtered = new ArrayList<>();
        for (Habit habit : habits) {
            // "今日待完成" = scheduled today and not yet checked; "今日已完成" = checked today.
            if (filterMode == 1 && !habit.isDueToday()) {
                continue;
            }
            if (filterMode == 2 && !habit.isCheckedToday()) {
                continue;
            }
            filtered.add(habit);
        }
        return filtered;
    }

    private List<Habit> queryHabits(long userId, String where, String[] args) {
        String finalWhere = "user_id=?";
        String[] finalArgs;
        if (where == null) {
            finalArgs = new String[]{String.valueOf(userId)};
        } else {
            finalWhere = finalWhere + " AND " + where;
            finalArgs = new String[args.length + 1];
            finalArgs[0] = String.valueOf(userId);
            System.arraycopy(args, 0, finalArgs, 1, args.length);
        }

        ArrayList<Habit> habits = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        // Base order is the user's manual sort_order; the non-custom sort modes reorder this
        // list in memory (streak/due-today are runtime-computed and can't be sorted in SQL).
        try (Cursor cursor = db.query("habits", null, finalWhere, finalArgs, null, null,
                "sort_order ASC, id ASC")) {
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

    public long addHabit(long userId, String title, String content, String timeText, String imageUri,
                         String category, String reminderTime, int frequencyType, String frequencyDays,
                         int frequencyCount, int targetDays) {
        return insertHabit(getWritableDatabase(), userId, title, content, timeText, imageUri,
                category, reminderTime, 0, 0, System.currentTimeMillis(),
                frequencyType, frequencyDays, frequencyCount, targetDays);
    }

    public boolean updateHabit(long id, String title, String content, String timeText, String imageUri,
                               String category, String reminderTime, int frequencyType,
                               String frequencyDays, int frequencyCount, int targetDays) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("content", content);
        values.put("time_text", timeText);
        values.put("image_uri", imageUri);
        values.put("category", category);
        values.put("reminder_time", reminderTime);
        values.put("frequency_type", frequencyType);
        values.put("frequency_days", frequencyDays);
        values.put("frequency_count", frequencyCount);
        values.put("target_days", targetDays);
        return getWritableDatabase().update("habits", values, "id=?",
                new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteHabit(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("check_records", "habit_id=?", new String[]{String.valueOf(id)});
        return db.delete("habits", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * Persist a new manual order for the given habit ids: the first id gets the smallest
     * sort_order, and so on. Written in one transaction so the list never ends up half-reordered.
     * Ids are matched on their own row only, so passing a filtered subset is safe (though the
     * caller only drags when the full unfiltered list is shown).
     */
    public void updateHabitOrder(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            for (int i = 0; i < orderedIds.size(); i++) {
                values.clear();
                values.put("sort_order", i + 1);
                db.update("habits", values, "id=?",
                        new String[]{String.valueOf(orderedIds.get(i))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
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
        return insertRecord(getWritableDatabase(), habitId, note, System.currentTimeMillis()) != NO_USER;
    }

    /**
     * Add a make-up check-in for a chosen day (used by the calendar to fill in missed days).
     * Unlike {@link #markChecked}, the record is timestamped at noon of {@code dayStart} rather
     * than "now", so it lands squarely inside the intended day regardless of the current time.
     * check_count is incremented; last_check_at only moves forward, so back-dating a missed day
     * never clobbers a more recent check-in (which would wrongly reset the "checked today" state).
     *
     * @param dayStart start-of-day timestamp of the day to credit
     * @return true if the check-in was recorded
     */
    public boolean addCheckForDay(long habitId, String note, long dayStart) {
        Habit habit = getHabit(habitId);
        if (habit == null) {
            return false;
        }
        long checkedAt = dayStart + DateUtils.DAY_MS / 2; // noon: safely within the day
        SQLiteDatabase db = getWritableDatabase();
        insertRecord(db, habitId, note, checkedAt);

        ContentValues values = new ContentValues();
        values.put("check_count", habit.checkCount + 1);
        values.put("last_check_at", Math.max(habit.lastCheckAt, checkedAt));
        return db.update("habits", values, "id=?", new String[]{String.valueOf(habitId)}) > 0;
    }

    /**
     * Undo the most recent check-in made today for the given habit. Deletes that record,
     * decrements the cached check_count, and resets last_check_at to the newest remaining
     * record (0 if none remain) so the "checked today" state is restored correctly.
     *
     * @return true if a record from today was removed.
     */
    public boolean undoTodayCheck(long habitId) {
        Habit habit = getHabit(habitId);
        if (habit == null) {
            return false;
        }
        long dayStart = DateUtils.startOfDay(System.currentTimeMillis());
        long dayEnd = dayStart + DateUtils.DAY_MS;
        SQLiteDatabase db = getWritableDatabase();

        long recordId = NO_USER;
        try (Cursor cursor = db.query("check_records", new String[]{"id"},
                "habit_id=? AND checked_at>=? AND checked_at<?",
                new String[]{String.valueOf(habitId), String.valueOf(dayStart), String.valueOf(dayEnd)},
                null, null, "checked_at DESC", "1")) {
            if (cursor.moveToFirst()) {
                recordId = cursor.getLong(0);
            }
        }
        if (recordId == NO_USER) {
            return false;
        }

        db.delete("check_records", "id=?", new String[]{String.valueOf(recordId)});

        long lastCheckAt = 0;
        try (Cursor cursor = db.query("check_records", new String[]{"checked_at"}, "habit_id=?",
                new String[]{String.valueOf(habitId)}, null, null, "checked_at DESC", "1")) {
            if (cursor.moveToFirst()) {
                lastCheckAt = cursor.getLong(0);
            }
        }

        ContentValues values = new ContentValues();
        values.put("check_count", Math.max(0, habit.checkCount - 1));
        values.put("last_check_at", lastCheckAt);
        db.update("habits", values, "id=?", new String[]{String.valueOf(habitId)});
        return true;
    }

    /**
     * @return true if the current password matched and was updated.
     */
    public boolean changePassword(long userId, String oldPassword, String newPassword) {
        SQLiteDatabase db = getWritableDatabase();
        String storedHash;
        String salt;
        try (Cursor cursor = db.query("users", new String[]{"password", "salt"}, "id=?",
                new String[]{String.valueOf(userId)}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return false;
            }
            storedHash = cursor.getString(0);
            salt = cursor.isNull(1) ? "" : cursor.getString(1);
        }
        if (!PasswordUtils.matches(oldPassword, salt, storedHash)) {
            return false;
        }
        String newSalt = PasswordUtils.newSalt();
        ContentValues values = new ContentValues();
        values.put("salt", newSalt);
        values.put("password", PasswordUtils.hash(newPassword, newSalt));
        return db.update("users", values, "id=?", new String[]{String.valueOf(userId)}) > 0;
    }

    /**
     * Delete a user along with all of their habits and check records.
     *
     * @return true if the user existed and was removed.
     */
    public boolean deleteUser(long userId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Remove check records belonging to this user's habits first.
            db.execSQL("DELETE FROM check_records WHERE habit_id IN " +
                    "(SELECT id FROM habits WHERE user_id=?)", new Object[]{userId});
            db.delete("habits", "user_id=?", new String[]{String.valueOf(userId)});
            int removed = db.delete("users", "id=?", new String[]{String.valueOf(userId)});
            db.setTransactionSuccessful();
            return removed > 0;
        } finally {
            db.endTransaction();
        }
    }

    public List<CheckRecord> getAllRecords(long userId) {
        ArrayList<CheckRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT r.id, r.habit_id, h.title, r.note, r.checked_at " +
                "FROM check_records r JOIN habits h ON r.habit_id=h.id " +
                "WHERE h.user_id=? " +
                "ORDER BY r.checked_at DESC";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(userId)})) {
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

    /** All check-in records for one day [dayStart, dayStart+1day), newest first. */
    public List<CheckRecord> getRecordsForDay(long userId, long dayStart) {
        ArrayList<CheckRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        long dayEnd = dayStart + DateUtils.DAY_MS;
        String sql = "SELECT r.id, r.habit_id, h.title, r.note, r.checked_at " +
                "FROM check_records r JOIN habits h ON r.habit_id=h.id " +
                "WHERE h.user_id=? AND r.checked_at>=? AND r.checked_at<? " +
                "ORDER BY r.checked_at DESC";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(userId),
                String.valueOf(dayStart), String.valueOf(dayEnd)})) {
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

    public int getTotalRecordCount(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM check_records r JOIN habits h ON r.habit_id=h.id " +
                "WHERE h.user_id=?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(userId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public int getCheckedHabitCountForDay(long dayStart, long userId) {
        SQLiteDatabase db = getReadableDatabase();
        long dayEnd = dayStart + DateUtils.DAY_MS;
        String sql = "SELECT COUNT(DISTINCT r.habit_id) FROM check_records r " +
                "JOIN habits h ON r.habit_id=h.id " +
                "WHERE h.user_id=? AND r.checked_at>=? AND r.checked_at<?";
        try (Cursor cursor = db.rawQuery(sql,
                new String[]{String.valueOf(userId), String.valueOf(dayStart), String.valueOf(dayEnd)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /**
     * Number of check-in records per day for the user within [fromInclusive, toExclusive),
     * keyed by start-of-day timestamp. Days with no check-ins are simply absent from the map.
     * One query backs the whole heatmap rather than one query per day.
     */
    public Map<Long, Integer> getDailyCheckCounts(long userId, long fromInclusive, long toExclusive) {
        Map<Long, Integer> counts = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT r.checked_at FROM check_records r JOIN habits h ON r.habit_id=h.id " +
                "WHERE h.user_id=? AND r.checked_at>=? AND r.checked_at<?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(userId),
                String.valueOf(fromInclusive), String.valueOf(toExclusive)})) {
            while (cursor.moveToNext()) {
                long day = DateUtils.startOfDay(cursor.getLong(0));
                Integer existing = counts.get(day);
                counts.put(day, existing == null ? 1 : existing + 1);
            }
        }
        return counts;
    }

    /** Per-category summary (habit count + total check-ins) for the profile page. */
    public List<CategoryStat> getCategoryStats(long userId) {
        ArrayList<CategoryStat> stats = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT h.category, COUNT(DISTINCT h.id), COUNT(r.id) " +
                "FROM habits h LEFT JOIN check_records r ON r.habit_id=h.id " +
                "WHERE h.user_id=? GROUP BY h.category ORDER BY COUNT(r.id) DESC, h.category ASC";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(userId)})) {
            while (cursor.moveToNext()) {
                stats.add(new CategoryStat(cursor.getString(0), cursor.getInt(1), cursor.getInt(2)));
            }
        }
        return stats;
    }

    /**
     * Milestone list derived from the user's current aggregate data. Recomputed on each call,
     * so it always reflects reality without a separate achievements table.
     */
    public List<Achievement> getAchievements(long userId) {
        List<Habit> habits = getAllHabits(userId);
        int totalRecords = getTotalRecordCount(userId);
        int bestStreak = 0;
        Set<String> categories = new HashSet<>();
        boolean anyGoalReached = false;
        for (Habit habit : habits) {
            bestStreak = Math.max(bestStreak, habit.streakDays);
            categories.add(habit.category);
            if (habit.goalReached()) {
                anyGoalReached = true;
            }
        }

        ArrayList<Achievement> list = new ArrayList<>();
        list.add(new Achievement("初次打卡", "完成第一次打卡", totalRecords >= 1));
        list.add(new Achievement("坚持一周", "连续打卡达到 7 天", bestStreak >= 7));
        list.add(new Achievement("坚持一月", "连续打卡达到 30 天", bestStreak >= 30));
        list.add(new Achievement("百次达成", "累计打卡满 100 次", totalRecords >= 100));
        list.add(new Achievement("多面手", "创建至少 3 个不同分类的事件", categories.size() >= 3));
        list.add(new Achievement("目标达成", "完成一个事件设定的坚持目标", anyGoalReached));
        return list;
    }

    public String buildExportText(long userId) {
        StringBuilder builder = new StringBuilder();
        builder.append("DayMark 打卡记录导出\n\n");
        for (Habit habit : getAllHabits(userId)) {
            builder.append("事件：").append(habit.title).append('\n');
            builder.append("分类：").append(habit.category).append('\n');
            builder.append("时间：").append(habit.timeText).append('\n');
            builder.append("频率：").append(habit.frequencyLabel()).append('\n');
            builder.append("提醒：").append(TextUtils.isEmpty(habit.reminderTime) ? "未设置" : habit.reminderTime).append('\n');
            builder.append("累计：").append(habit.checkCount).append(" 次，").append(habit.streakLabel()).append('\n');
            if (habit.hasGoal()) {
                builder.append("目标：坚持 ").append(habit.targetDays).append(" 天（已完成 ")
                        .append(habit.totalDays).append(" 天，").append(habit.goalProgress()).append("%）\n");
            }
            builder.append("内容：").append(habit.content).append("\n\n");
        }
        builder.append("详细打卡记录\n");
        for (CheckRecord record : getAllRecords(userId)) {
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
        int frequencyType = getInt(cursor, "frequency_type", Habit.FREQ_DAILY);
        String frequencyDays = getString(cursor, "frequency_days", "");
        int frequencyCount = getInt(cursor, "frequency_count", 0);
        int targetDays = getInt(cursor, "target_days", 0);
        int[] streakWeekTotal = computeStreakWeekTotal(id, frequencyType, frequencyDays, frequencyCount);
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
                streakWeekTotal[0],
                getLastNote(id),
                frequencyType,
                frequencyDays,
                frequencyCount,
                targetDays,
                streakWeekTotal[1],
                streakWeekTotal[2]
        );
    }

    private static int getInt(Cursor cursor, String column, int defaultValue) {
        int index = cursor.getColumnIndex(column);
        return (index < 0 || cursor.isNull(index)) ? defaultValue : cursor.getInt(index);
    }

    private static String getString(Cursor cursor, String column, String defaultValue) {
        int index = cursor.getColumnIndex(column);
        return (index < 0 || cursor.isNull(index)) ? defaultValue : cursor.getString(index);
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

    /** Distinct start-of-day timestamps on which this habit was checked in. */
    private Set<Long> getCheckedDays(long habitId) {
        Set<Long> days = new HashSet<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("check_records", new String[]{"checked_at"}, "habit_id=?",
                new String[]{String.valueOf(habitId)}, null, null, null)) {
            while (cursor.moveToNext()) {
                days.add(DateUtils.startOfDay(cursor.getLong(0)));
            }
        }
        return days;
    }

    /**
     * @return {streakDays, weekCheckCount, totalDays} computed once from the habit's check days.
     * The streak unit/rules depend on the frequency type (see the helpers below).
     */
    private int[] computeStreakWeekTotal(long habitId, int frequencyType, String frequencyDays,
                                         int frequencyCount) {
        Set<Long> days = getCheckedDays(habitId);
        int total = days.size();

        long weekStart = DateUtils.startOfWeek(System.currentTimeMillis());
        long weekEnd = weekStart + 7 * DateUtils.DAY_MS;
        int weekCount = 0;
        for (long day : days) {
            if (day >= weekStart && day < weekEnd) {
                weekCount++;
            }
        }

        int streak = (frequencyType == Habit.FREQ_WEEKLY_COUNT)
                ? weeklyCountStreak(days, Math.max(1, frequencyCount))
                : dayBasedStreak(days, frequencyType, parseDayNumbers(frequencyDays));
        return new int[]{streak, weekCount, total};
    }

    /**
     * Streak in days for daily / specific-weekday habits. Walks backwards from today (skipping
     * today if it isn't checked yet, since it may still be pending). For weekday habits, days
     * not on the schedule are skipped; a scheduled day with no check-in ends the streak.
     */
    private int dayBasedStreak(Set<Long> days, int frequencyType, Set<Integer> scheduledDays) {
        boolean weeklyDays = frequencyType == Habit.FREQ_WEEKLY_DAYS && !scheduledDays.isEmpty();
        long cursor = DateUtils.startOfDay(System.currentTimeMillis());
        if (!days.contains(cursor)) {
            // Today not checked yet; don't count it as a miss, just start from yesterday.
            cursor -= DateUtils.DAY_MS;
        }
        int streak = 0;
        for (int guard = 0; guard < 4000; guard++) {
            if (weeklyDays && !scheduledDays.contains(DateUtils.isoDayOfWeek(cursor))) {
                cursor -= DateUtils.DAY_MS;
                continue;
            }
            if (days.contains(cursor)) {
                streak++;
                cursor -= DateUtils.DAY_MS;
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * Streak in weeks for "N times per week" habits: consecutive weeks (Mon-based) meeting the
     * target count. The current week is only counted once the target is met; otherwise it's
     * treated as in-progress and the streak is measured from the previous week.
     */
    private int weeklyCountStreak(Set<Long> days, int target) {
        Map<Long, Integer> perWeek = new HashMap<>();
        for (long day : days) {
            long week = DateUtils.startOfWeek(day);
            Integer existing = perWeek.get(week);
            perWeek.put(week, existing == null ? 1 : existing + 1);
        }
        long weekCursor = DateUtils.startOfWeek(System.currentTimeMillis());
        Integer current = perWeek.get(weekCursor);
        if (current == null || current < target) {
            weekCursor -= 7 * DateUtils.DAY_MS;
        }
        int streak = 0;
        for (int guard = 0; guard < 1000; guard++) {
            Integer count = perWeek.get(weekCursor);
            if (count != null && count >= target) {
                streak++;
                weekCursor -= 7 * DateUtils.DAY_MS;
            } else {
                break;
            }
        }
        return streak;
    }

    private static Set<Integer> parseDayNumbers(String frequencyDays) {
        Set<Integer> days = new HashSet<>();
        if (TextUtils.isEmpty(frequencyDays)) {
            return days;
        }
        for (String part : frequencyDays.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    days.add(Integer.parseInt(trimmed));
                } catch (NumberFormatException ignored) {
                    // Skip malformed entries.
                }
            }
        }
        return days;
    }

    public String buildWeekSummary(long userId) {
        StringBuilder builder = new StringBuilder("最近 7 天打卡情况\n");
        long today = DateUtils.startOfDay(System.currentTimeMillis());
        for (int i = 6; i >= 0; i--) {
            long day = today - i * DateUtils.DAY_MS;
            builder.append(DateUtils.formatMonthDay(day))
                    .append("：")
                    .append(getCheckedHabitCountForDay(day, userId))
                    .append(" 个事件\n");
        }
        return builder.toString().trim();
    }

    public double getCompletionRateToday(long userId) {
        int habitCount = getAllHabits(userId).size();
        if (habitCount == 0) {
            return 0;
        }
        int checked = getCheckedHabitCountForDay(DateUtils.startOfDay(System.currentTimeMillis()), userId);
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
