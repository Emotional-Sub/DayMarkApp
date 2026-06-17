package com.example.daymark;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DayMarkDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "daymark.db";
    private static final int DB_VERSION = 8;

    /** Returned by {@link #login} / {@link #register} when there is no matching or valid user. */
    public static final long NO_USER = -1;
    public static final int PROFILE_UPDATE_OK = 0;
    public static final int PROFILE_UPDATE_OLD_PASSWORD_REQUIRED = 1;
    public static final int PROFILE_UPDATE_OLD_PASSWORD_INCORRECT = 2;
    public static final int PROFILE_UPDATE_FAILED = 3;

    private final Context context;

    public DayMarkDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // Enable Write-Ahead Logging for better concurrency: reads don't block writes
        db.enableWriteAheadLogging();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL," +
                "display_name TEXT," +
                "salt TEXT," +
                "avatar_uri TEXT)");
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
        Logger.i("Database upgrade started: v" + oldVersion + " -> v" + newVersion);

        // v3 introduced per-user habits. Add the column without dropping existing data;
        // pre-existing habits are assigned to the first user (the seeded demo account).
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE habits ADD COLUMN user_id INTEGER NOT NULL DEFAULT 1");
                Logger.dbSuccess("Added user_id column to habits table");
            } catch (SQLException e) {
                Logger.dbError("Failed to add user_id column", e);
                // Column already present or the table predates this schema; backup and rebuild.
                if (!backupDatabaseToJson(db)) {
                    Logger.e("Database backup failed before DROP. Aborting upgrade.");
                    throw new RuntimeException("Cannot upgrade: backup failed and data would be lost", e);
                }
                Logger.w("Rebuilding database schema (backup saved)");
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
            Logger.dbSuccess("Added sort_order column");
        }
        // v6 split the editable display name from the fixed login username. Existing users get a
        // null display_name, which callers fall back to the username for until the user sets one.
        if (oldVersion < 6) {
            addColumnQuietly(db, "users", "display_name TEXT");
            Logger.dbSuccess("Added display_name column");
        }
        // v7 added avatar_uri for user profile pictures.
        if (oldVersion < 7) {
            addColumnQuietly(db, "users", "avatar_uri TEXT");
            Logger.dbSuccess("Added avatar_uri column");
        }
        // v8 keeps the users schema consistent for installs created when DB v7's onCreate()
        // accidentally omitted avatar_uri even though the rest of the app already queried it.
        if (oldVersion < 8) {
            addColumnQuietly(db, "users", "avatar_uri TEXT");
            Logger.dbSuccess("Added avatar_uri column");
        }

        Logger.i("Database upgrade completed successfully");
    }

    private void addColumnQuietly(SQLiteDatabase db, String table, String columnDef) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
        } catch (SQLException e) {
            // Column already exists; safe to continue the upgrade.
            Logger.d("Column already exists: " + table + "." + columnDef);
        }
    }

    /**
     * Backup all database tables to a JSON file before a potentially destructive operation.
     * The backup is saved to the app's files directory as "db_backup_[timestamp].json".
     *
     * @param db the database to backup
     * @return true if backup succeeded, false otherwise
     */
    private boolean backupDatabaseToJson(SQLiteDatabase db) {
        try {
            JSONObject backup = buildBackupJson(db);

            // Write to file
            File backupDir = new File(context.getFilesDir(), "db_backups");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Logger.e("Failed to create backup directory");
                return false;
            }

            String filename = "db_backup_" + System.currentTimeMillis() + ".json";
            File backupFile = new File(backupDir, filename);

            try (FileOutputStream fos = new FileOutputStream(backupFile)) {
                writeBackupJson(backup, fos);
            }

            Logger.i("Database backup saved to: " + backupFile.getAbsolutePath());
            Logger.i("Backup contains: " + backup.getJSONArray("users").length() + " users, " +
                    backup.getJSONArray("habits").length() + " habits, " +
                    backup.getJSONArray("check_records").length() + " records");
            return true;

        } catch (Exception e) {
            Logger.e("Database backup failed", e);
            return false;
        }
    }

    private JSONObject buildBackupJson(SQLiteDatabase db) throws Exception {
        Logger.i("Starting database backup to JSON");
        JSONObject backup = new JSONObject();
        backup.put("backup_version", 1);
        backup.put("db_version", DB_VERSION);
        backup.put("timestamp", System.currentTimeMillis());

        // Backup users table
        JSONArray users = new JSONArray();
        try (Cursor cursor = db.query("users", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                JSONObject user = new JSONObject();
                user.put("id", cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                user.put("username", cursor.getString(cursor.getColumnIndexOrThrow("username")));
                user.put("password", cursor.getString(cursor.getColumnIndexOrThrow("password")));
                int saltIdx = cursor.getColumnIndex("salt");
                if (saltIdx >= 0 && !cursor.isNull(saltIdx)) {
                    user.put("salt", cursor.getString(saltIdx));
                }
                int displayNameIdx = cursor.getColumnIndex("display_name");
                if (displayNameIdx >= 0 && !cursor.isNull(displayNameIdx)) {
                    user.put("display_name", cursor.getString(displayNameIdx));
                }
                int avatarUriIdx = cursor.getColumnIndex("avatar_uri");
                if (avatarUriIdx >= 0 && !cursor.isNull(avatarUriIdx)) {
                    user.put("avatar_uri", cursor.getString(avatarUriIdx));
                }
                users.put(user);
            }
        }
        backup.put("users", users);

        // Backup habits table
        JSONArray habits = new JSONArray();
        try (Cursor cursor = db.query("habits", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                JSONObject habit = new JSONObject();
                habit.put("id", cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                int userIdIdx = cursor.getColumnIndex("user_id");
                if (userIdIdx >= 0) {
                    habit.put("user_id", cursor.getLong(userIdIdx));
                }
                habit.put("title", cursor.getString(cursor.getColumnIndexOrThrow("title")));
                habit.put("content", cursor.getString(cursor.getColumnIndexOrThrow("content")));
                habit.put("time_text", cursor.getString(cursor.getColumnIndexOrThrow("time_text")));
                habit.put("category", cursor.getString(cursor.getColumnIndexOrThrow("category")));
                habit.put("check_count", cursor.getInt(cursor.getColumnIndexOrThrow("check_count")));
                habit.put("last_check_at", cursor.getLong(cursor.getColumnIndexOrThrow("last_check_at")));
                habit.put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));

                // Optional columns
                copyColumnIfExists(cursor, habit, "image_uri");
                copyColumnIfExists(cursor, habit, "reminder_time");
                copyColumnIfExists(cursor, habit, "frequency_type");
                copyColumnIfExists(cursor, habit, "frequency_days");
                copyColumnIfExists(cursor, habit, "frequency_count");
                copyColumnIfExists(cursor, habit, "target_days");
                copyColumnIfExists(cursor, habit, "sort_order");

                habits.put(habit);
            }
        }
        backup.put("habits", habits);

        // Backup check_records table
        JSONArray records = new JSONArray();
        try (Cursor cursor = db.query("check_records", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                JSONObject record = new JSONObject();
                record.put("id", cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                record.put("habit_id", cursor.getLong(cursor.getColumnIndexOrThrow("habit_id")));
                record.put("checked_at", cursor.getLong(cursor.getColumnIndexOrThrow("checked_at")));
                int noteIdx = cursor.getColumnIndex("note");
                if (noteIdx >= 0 && !cursor.isNull(noteIdx)) {
                    record.put("note", cursor.getString(noteIdx));
                }
                records.put(record);
            }
        }
        backup.put("check_records", records);
        return backup;
    }

    private void writeBackupJson(JSONObject backup, OutputStream outputStream) throws Exception {
        outputStream.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    @WorkerThread
    public boolean exportBackupToStream(OutputStream outputStream) {
        try {
            JSONObject backup = buildBackupJson(getReadableDatabase());
            writeBackupJson(backup, outputStream);
            Logger.i("Database backup exported to destination stream");
            return true;
        } catch (Exception e) {
            Logger.e("Database export failed", e);
            return false;
        }
    }

    /**
     * Helper to safely copy a column value from cursor to JSON if the column exists.
     */
    private void copyColumnIfExists(Cursor cursor, JSONObject json, String columnName) throws Exception {
        int idx = cursor.getColumnIndex(columnName);
        if (idx >= 0 && !cursor.isNull(idx)) {
            int type = cursor.getType(idx);
            switch (type) {
                case Cursor.FIELD_TYPE_INTEGER:
                    json.put(columnName, cursor.getLong(idx));
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    json.put(columnName, cursor.getString(idx));
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    json.put(columnName, cursor.getDouble(idx));
                    break;
            }
        }
    }

    /**
     * Convert any user rows still holding a plaintext password (no salt) into a salted hash.
     * Reads everything first, then writes, so the cursor is closed before the updates run.
     * Wrapped in a transaction to ensure all-or-nothing migration (no partial failures).
     */
    private void migratePlaintextPasswords(SQLiteDatabase db) {
        Logger.dbStart("Migrating plaintext passwords to PBKDF2");
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
        if (ids.isEmpty()) {
            Logger.d("No plaintext passwords to migrate");
            return; // No plaintext passwords to migrate.
        }
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                String salt = PasswordUtils.newSalt();
                ContentValues values = new ContentValues();
                values.put("salt", salt);
                values.put("password", PasswordUtils.hash(plaintexts.get(i), salt));
                db.update("users", values, "id=?", new String[]{String.valueOf(ids.get(i))});
            }
            db.setTransactionSuccessful();
            Logger.dbSuccess("Migrated " + ids.size() + " passwords to PBKDF2");
        } catch (Exception e) {
            Logger.dbError("Password migration failed", e);
        } finally {
            db.endTransaction();
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
    @WorkerThread
    public long login(String username, String password) {
        long userId = NO_USER;
        String storedHash = null;
        String salt = "";
        try (Cursor cursor = getReadableDatabase().query("users",
                new String[]{"id", "password", "salt"},
                "username=?", new String[]{username}, null, null, null)) {
            if (cursor.moveToFirst()) {
                storedHash = cursor.getString(1);
                salt = cursor.isNull(2) ? "" : cursor.getString(2);
                if (PasswordUtils.matches(password, salt, storedHash)) {
                    userId = cursor.getLong(0);
                }
            }
        }
        // Transparently upgrade a legacy salted-SHA-256 hash to PBKDF2 now that we have the
        // plaintext in hand and have confirmed it matches. Done after the cursor is closed.
        if (userId != NO_USER && PasswordUtils.isLegacy(storedHash)) {
            String newSalt = PasswordUtils.newSalt();
            ContentValues values = new ContentValues();
            values.put("salt", newSalt);
            values.put("password", PasswordUtils.hash(password, newSalt));
            getWritableDatabase().update("users", values, "id=?",
                    new String[]{String.valueOf(userId)});
        }
        return userId;
    }

    /**
     * @return the new user's id, or {@link #NO_USER} when the username already exists.
     */
    @WorkerThread
    public long register(String username, String password) {
        String salt = PasswordUtils.newSalt();
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", PasswordUtils.hash(password, salt));
        values.put("salt", salt);
        return getWritableDatabase().insert("users", null, values);
    }

    @WorkerThread
    public List<Habit> getAllHabits(long userId) {
        return queryHabits(userId, null, null);
    }

    /**
     * Get all habits for a user that have NOT been checked on the specified day.
     * Used for back-fill to show only habits that still need a check-in on that day.
     *
     * @param userId the user whose habits to query
     * @param dayStart start-of-day timestamp for the target day
     * @return list of habits without a check-in on that day
     */
    @WorkerThread
    public List<Habit> getUncheckedHabitsForDay(long userId, long dayStart) {
        List<Habit> allHabits = getAllHabits(userId);
        List<Habit> unchecked = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        long dayEnd = dayStart + DateUtils.DAY_MS;

        for (Habit habit : allHabits) {
            // Check if this habit has any check-in on the target day
            try (Cursor cursor = db.query("check_records", new String[]{"id"},
                    "habit_id=? AND checked_at>=? AND checked_at<?",
                    new String[]{String.valueOf(habit.id), String.valueOf(dayStart), String.valueOf(dayEnd)},
                    null, null, null, "1")) {
                if (!cursor.moveToFirst()) {
                    // No check-in found for this day, so it's unchecked
                    unchecked.add(habit);
                }
            }
        }
        return unchecked;
    }

    /**
     * Every habit (across all users) that has a reminder time set. Used at boot to re-register
     * alarms, which AlarmManager drops on reboot; there is no logged-in user at that point.
     */
    @WorkerThread
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

    @WorkerThread
    public List<Habit> getHabitsWithReminder(long userId) {
        ArrayList<Habit> habits = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("habits", null,
                "user_id=? AND reminder_time IS NOT NULL AND reminder_time<>''",
                new String[]{String.valueOf(userId)}, null, null, "reminder_time ASC, id ASC")) {
            while (cursor.moveToNext()) {
                habits.add(readHabit(cursor));
            }
        }
        return habits;
    }

    @WorkerThread
    public List<Habit> searchHabits(String keyword, int filterMode, long userId) {
        String where = null;
        String[] args = null;
        if (!TextUtils.isEmpty(keyword)) {
            // Escape LIKE wildcards in the user's input so a literal % or _ matches itself
            // instead of acting as a wildcard; the ESCAPE clause designates '\' as the escape char.
            where = "(title LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\' OR category LIKE ? ESCAPE '\\')";
            String like = "%" + escapeLike(keyword) + "%";
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

    /**
     * Escape the SQL LIKE wildcards ('%' and '_') and the escape char itself ('\') in user input,
     * so the keyword is matched literally. Pairs with the {@code ESCAPE '\'} clause in the query.
     * Optimized to only allocate a StringBuilder when special characters are actually found.
     */
    private static String escapeLike(String input) {
        // Fast path: if no special chars, return as-is to avoid allocation.
        if (input.indexOf('\\') < 0 && input.indexOf('%') < 0 && input.indexOf('_') < 0) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length() + 10);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
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
                habits.add(readHabitBase(cursor));
            }
        }
        // Fill the derived fields (streak/week/total/last note) in two batched queries for the
        // whole list, rather than two per habit. Avoids an N+1 query storm on the main thread.
        fillComputedBatch(db, habits);
        return habits;
    }

    /**
     * Populate the runtime-computed fields for a whole list of habits using two queries total:
     * one for every check-in (grouped into per-habit day sets) and one for the latest note per
     * habit. Replaces the per-habit {@link #getCheckedDays}/{@link #getLastNote} calls.
     */
    private void fillComputedBatch(SQLiteDatabase db, List<Habit> habits) {
        if (habits.isEmpty()) {
            return;
        }
        StringBuilder idList = new StringBuilder();
        for (int i = 0; i < habits.size(); i++) {
            if (i > 0) {
                idList.append(',');
            }
            idList.append(habits.get(i).id);
        }
        String inClause = "habit_id IN (" + idList + ")";

        // habit_id -> distinct start-of-day timestamps checked in.
        Map<Long, Set<Long>> daysByHabit = new HashMap<>();
        try (Cursor cursor = db.query("check_records", new String[]{"habit_id", "checked_at"},
                inClause, null, null, null, null)) {
            int habitCol = cursor.getColumnIndexOrThrow("habit_id");
            int atCol = cursor.getColumnIndexOrThrow("checked_at");
            while (cursor.moveToNext()) {
                long habitId = cursor.getLong(habitCol);
                Set<Long> days = daysByHabit.get(habitId);
                if (days == null) {
                    days = new HashSet<>();
                    daysByHabit.put(habitId, days);
                }
                days.add(DateUtils.startOfDay(cursor.getLong(atCol)));
            }
        }

        // habit_id -> latest non-empty note. Ordered oldest-first so the last write per habit wins.
        Map<Long, String> noteByHabit = new HashMap<>();
        try (Cursor cursor = db.query("check_records", new String[]{"habit_id", "note"},
                inClause + " AND note<>''", null, null, null, "checked_at ASC")) {
            int habitCol = cursor.getColumnIndexOrThrow("habit_id");
            int noteCol = cursor.getColumnIndexOrThrow("note");
            while (cursor.moveToNext()) {
                noteByHabit.put(cursor.getLong(habitCol), cursor.getString(noteCol));
            }
        }

        for (Habit habit : habits) {
            Set<Long> days = daysByHabit.get(habit.id);
            String note = noteByHabit.get(habit.id);
            fillComputed(habit, days == null ? new HashSet<>() : days, note == null ? "" : note);
        }
    }

    @WorkerThread
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

    @WorkerThread
    public long addHabit(long userId, String title, String content, String timeText, String imageUri,
                         String category, String reminderTime, int frequencyType, String frequencyDays,
                         int frequencyCount, int targetDays) {
        return insertHabit(getWritableDatabase(), userId, title, content, timeText, imageUri,
                category, reminderTime, 0, 0, System.currentTimeMillis(),
                frequencyType, frequencyDays, frequencyCount, targetDays);
    }

    @WorkerThread
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

    @WorkerThread
    public boolean updateReminderTime(long habitId, String reminderTime) {
        ContentValues values = new ContentValues();
        values.put("reminder_time", normalizeReminder(reminderTime));
        return getWritableDatabase().update("habits", values, "id=?",
                new String[]{String.valueOf(habitId)}) > 0;
    }

    @WorkerThread
    public boolean deleteHabit(long id) {
        // Get the habit before deleting to access its image URI
        Habit habit = getHabit(id);
        String imageUri = (habit != null) ? habit.imageUri : null;

        SQLiteDatabase db = getWritableDatabase();
        db.delete("check_records", "habit_id=?", new String[]{String.valueOf(id)});
        boolean deleted = db.delete("habits", "id=?", new String[]{String.valueOf(id)}) > 0;

        // Clean up the image file after successful deletion
        if (deleted && imageUri != null && !imageUri.isEmpty()) {
            deleteImageFile(context, imageUri);
        }

        return deleted;
    }

    /**
     * Delete a habit's image file from disk. Called when a habit is deleted or its image is replaced.
     *
     * @param context application context
     * @param imageUri the URI string of the image to delete
     */
    private void deleteImageFile(Context context, String imageUri) {
        try {
            if (ImageUtils.deleteOwnedImage(context, imageUri)) {
                Logger.d("Deleted habit image: " + imageUri);
            }
        } catch (Exception e) {
            Logger.w("Failed to delete habit image: " + imageUri, e);
        }
    }

    /**
     * Persist a new manual order for the given habit ids: the first id gets the smallest
     * sort_order, and so on. Written in one transaction so the list never ends up half-reordered.
     * Ids are matched on their own row only, so passing a filtered subset is safe (though the
     * caller only drags when the full unfiltered list is shown).
     */
    @WorkerThread
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

    @WorkerThread
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

    @WorkerThread
    public boolean addNote(long habitId, String note) {
        if (getHabit(habitId) == null) {
            return false;
        }
        return insertRecord(getWritableDatabase(), habitId, note, System.currentTimeMillis()) != NO_USER;
    }

    /** Back-fill result: the operation failed (e.g. habit gone). */
    public static final int BACKFILL_FAILED = 0;
    /** Back-fill result: the day had no prior check-in, so a new one was credited. */
    public static final int BACKFILL_ADDED = 1;
    /** Back-fill result: the day was already checked in; the note was appended without re-crediting. */
    public static final int BACKFILL_ALREADY_CHECKED = 2;

    /**
     * Add a make-up check-in for a chosen day (used by the calendar to fill in missed days).
     * Unlike {@link #markChecked}, the record is timestamped at noon of {@code dayStart} rather
     * than "now", so it lands squarely inside the intended day regardless of the current time.
     *
     * <p>If the day already has a check-in for this habit, the record (note) is still stored, but
     * check_count is NOT incremented: that day was already credited, and bumping the count again
     * would drift the "累计次数" away from the actual number of checked days. last_check_at only
     * ever moves forward, so back-dating a missed day never clobbers a more recent check-in (which
     * would wrongly reset the "checked today" state).
     *
     * @param dayStart start-of-day timestamp of the day to credit
     * @return one of {@link #BACKFILL_FAILED}, {@link #BACKFILL_ADDED}, {@link #BACKFILL_ALREADY_CHECKED}
     */
    @WorkerThread
    public int addCheckForDay(long habitId, String note, long dayStart) {
        Habit habit = getHabit(habitId);
        if (habit == null) {
            return BACKFILL_FAILED;
        }
        long checkedAt = dayStart + DateUtils.DAY_MS / 2; // noon: safely within the day
        SQLiteDatabase db = getWritableDatabase();
        boolean alreadyChecked = hasCheckOnDay(db, habitId, dayStart);
        insertRecord(db, habitId, note, checkedAt);

        ContentValues values = new ContentValues();
        // Only credit a new day; appending to an already-checked day must not inflate the count.
        if (!alreadyChecked) {
            values.put("check_count", habit.checkCount + 1);
        }
        values.put("last_check_at", Math.max(habit.lastCheckAt, checkedAt));
        db.update("habits", values, "id=?", new String[]{String.valueOf(habitId)});
        return alreadyChecked ? BACKFILL_ALREADY_CHECKED : BACKFILL_ADDED;
    }

    /** Whether the habit already has at least one check-in within [dayStart, dayStart+1day). */
    private boolean hasCheckOnDay(SQLiteDatabase db, long habitId, long dayStart) {
        long dayEnd = dayStart + DateUtils.DAY_MS;
        try (Cursor cursor = db.query("check_records", new String[]{"id"},
                "habit_id=? AND checked_at>=? AND checked_at<?",
                new String[]{String.valueOf(habitId), String.valueOf(dayStart), String.valueOf(dayEnd)},
                null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    /**
     * Undo the most recent check-in made today for the given habit. Deletes that record,
     * decrements the cached check_count, and resets last_check_at to the newest remaining
     * record (0 if none remain) so the "checked today" state is restored correctly.
     *
     * @return true if a record from today was removed.
     */
    @WorkerThread
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
     * The user's editable display name, or their login username as a fallback when no display
     * name has been set (e.g. accounts created before v6, or one that was cleared to empty).
     */
    @WorkerThread
    public String getDisplayName(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("users", new String[]{"username", "display_name"}, "id=?",
                new String[]{String.valueOf(userId)}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return "";
            }
            String username = cursor.getString(0);
            String displayName = cursor.isNull(1) ? "" : cursor.getString(1).trim();
            return TextUtils.isEmpty(displayName) ? username : displayName;
        }
    }

    /**
     * Set the user's display name (the editable nickname shown on screen), leaving the login
     * username untouched. An empty/blank value is stored as null so {@link #getDisplayName} falls
     * back to the username again.
     *
     * @return true if the row existed and was updated.
     */
    @WorkerThread
    public boolean updateDisplayName(long userId, String displayName) {
        ContentValues values = new ContentValues();
        String trimmed = displayName == null ? "" : displayName.trim();
        if (TextUtils.isEmpty(trimmed)) {
            values.putNull("display_name");
        } else {
            values.put("display_name", trimmed);
        }
        return getWritableDatabase().update("users", values, "id=?",
                new String[]{String.valueOf(userId)}) > 0;
    }

    /**
     * Get the user's avatar URI. Returns null if no avatar is set.
     */
    @WorkerThread
    public String getAvatarUri(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("users", new String[]{"avatar_uri"}, "id=?",
                new String[]{String.valueOf(userId)}, null, null, null)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return cursor.getString(0);
        }
    }

    /**
     * Set the user's avatar URI. Can be a file URI, content URI, or a special "default_N" string
     * for default colored avatars.
     */
    @WorkerThread
    public void setAvatarUri(long userId, String avatarUri) {
        ContentValues values = new ContentValues();
        if (TextUtils.isEmpty(avatarUri)) {
            values.putNull("avatar_uri");
        } else {
            values.put("avatar_uri", avatarUri);
        }
        getWritableDatabase().update("users", values, "id=?",
                new String[]{String.valueOf(userId)});
    }

    /**
     * Convenience method for updateDisplayName that returns void.
     * Used for compatibility with existing code.
     */
    @WorkerThread
    public void setDisplayName(long userId, String displayName) {
        updateDisplayName(userId, displayName);
    }

    /**
     * Update display name/avatar and optionally password in one transaction so validation failure
     * never leaves the profile half-saved.
     */
    @WorkerThread
    public int updateProfile(long userId, String displayName, String avatarUri,
                             String oldPassword, String newPassword) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            boolean changePassword = !TextUtils.isEmpty(newPassword);
            ContentValues values = new ContentValues();
            String trimmedName = displayName == null ? "" : displayName.trim();
            if (TextUtils.isEmpty(trimmedName)) {
                values.putNull("display_name");
            } else {
                values.put("display_name", trimmedName);
            }
            if (TextUtils.isEmpty(avatarUri)) {
                values.putNull("avatar_uri");
            } else {
                values.put("avatar_uri", avatarUri);
            }

            if (changePassword) {
                if (TextUtils.isEmpty(oldPassword)) {
                    return PROFILE_UPDATE_OLD_PASSWORD_REQUIRED;
                }
                String storedHash;
                String salt;
                try (Cursor cursor = db.query("users", new String[]{"password", "salt"}, "id=?",
                        new String[]{String.valueOf(userId)}, null, null, null)) {
                    if (!cursor.moveToFirst()) {
                        return PROFILE_UPDATE_FAILED;
                    }
                    storedHash = cursor.getString(0);
                    salt = cursor.isNull(1) ? "" : cursor.getString(1);
                }
                if (!PasswordUtils.matches(oldPassword, salt, storedHash)) {
                    return PROFILE_UPDATE_OLD_PASSWORD_INCORRECT;
                }
                String newSalt = PasswordUtils.newSalt();
                values.put("salt", newSalt);
                values.put("password", PasswordUtils.hash(newPassword, newSalt));
            }

            if (db.update("users", values, "id=?", new String[]{String.valueOf(userId)}) <= 0) {
                return PROFILE_UPDATE_FAILED;
            }
            db.setTransactionSuccessful();
            return PROFILE_UPDATE_OK;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * @return true if the current password matched and was updated.
     */
    @WorkerThread
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
    @WorkerThread
    public boolean deleteUser(long userId) {
        List<Habit> habits = getAllHabits(userId);
        String avatarUri = getAvatarUri(userId);
        for (Habit habit : habits) {
            ReminderReceiver.cancel(context, habit.id);
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Remove check records belonging to this user's habits first.
            db.execSQL("DELETE FROM check_records WHERE habit_id IN " +
                    "(SELECT id FROM habits WHERE user_id=?)", new Object[]{userId});
            db.delete("habits", "user_id=?", new String[]{String.valueOf(userId)});
            int removed = db.delete("users", "id=?", new String[]{String.valueOf(userId)});
            db.setTransactionSuccessful();
            if (removed > 0) {
                for (Habit habit : habits) {
                    deleteImageFile(context, habit.imageUri);
                }
                if (!TextUtils.isEmpty(avatarUri)) {
                    ImageUtils.deleteOwnedImage(context, avatarUri);
                }
            }
            return removed > 0;
        } finally {
            db.endTransaction();
        }
    }

    @WorkerThread
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
    @WorkerThread
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

    @WorkerThread
    public int getTotalRecordCount(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM check_records r JOIN habits h ON r.habit_id=h.id " +
                "WHERE h.user_id=?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(userId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @WorkerThread
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
     * Distinct checked-habit count per day within [fromInclusive, toExclusive), keyed by
     * start-of-day timestamp. Used by the calendar month grid to avoid one query per cell.
     */
    @WorkerThread
    public Map<Long, Integer> getCheckedHabitCountsByDay(long userId, long fromInclusive, long toExclusive) {
        Map<Long, Set<Long>> distinctHabits = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT r.checked_at, r.habit_id " +
                "FROM check_records r JOIN habits h ON r.habit_id=h.id " +
                "WHERE h.user_id=? AND r.checked_at>=? AND r.checked_at<?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(userId),
                String.valueOf(fromInclusive), String.valueOf(toExclusive)})) {
            while (cursor.moveToNext()) {
                long day = DateUtils.startOfDay(cursor.getLong(0));
                long habitId = cursor.getLong(1);
                Set<Long> ids = distinctHabits.get(day);
                if (ids == null) {
                    ids = new HashSet<>();
                    distinctHabits.put(day, ids);
                }
                ids.add(habitId);
            }
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : distinctHabits.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    /**
     * Number of check-in records per day for the user within [fromInclusive, toExclusive),
     * keyed by start-of-day timestamp. Days with no check-ins are simply absent from the map.
     * One query backs the whole heatmap rather than one query per day.
     */
    @WorkerThread
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
    @WorkerThread
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
    @WorkerThread
    public List<Achievement> getAchievements(long userId) {
        List<Habit> habits = getAllHabits(userId);
        int totalRecords = getTotalRecordCount(userId);
        int bestStreak = 0;
        Set<String> categories = new HashSet<>();
        boolean anyGoalReached = false;
        int habitsWithGoal = 0;

        for (Habit habit : habits) {
            bestStreak = Math.max(bestStreak, habit.streakDays);
            categories.add(habit.category);
            if (habit.hasGoal()) {
                habitsWithGoal++;
            }
            if (habit.goalReached()) {
                anyGoalReached = true;
            }
        }

        ArrayList<Achievement> list = new ArrayList<>();

        // 初级成就
        list.add(new Achievement("初次打卡", "完成第一次打卡", totalRecords >= 1));
        list.add(new Achievement("初创事件", "创建第一个打卡事件", habits.size() >= 1));
        list.add(new Achievement("连续三天", "连续打卡达到 3 天", bestStreak >= 3));

        // 中级成就
        list.add(new Achievement("坚持一周", "连续打卡达到 7 天", bestStreak >= 7));
        list.add(new Achievement("多样化", "创建至少 3 个打卡事件", habits.size() >= 3));
        list.add(new Achievement("多面手", "创建至少 3 个不同分类的事件", categories.size() >= 3));
        list.add(new Achievement("五十次", "累计打卡满 50 次", totalRecords >= 50));

        // 高级成就
        list.add(new Achievement("坚持一月", "连续打卡达到 30 天", bestStreak >= 30));
        list.add(new Achievement("百次达成", "累计打卡满 100 次", totalRecords >= 100));
        list.add(new Achievement("目标达成", "完成一个事件设定的坚持目标", anyGoalReached));
        list.add(new Achievement("事件达人", "创建至少 5 个打卡事件", habits.size() >= 5));

        // 专家级成就
        list.add(new Achievement("坚持百日", "连续打卡达到 100 天", bestStreak >= 100));
        list.add(new Achievement("五百次", "累计打卡满 500 次", totalRecords >= 500));
        list.add(new Achievement("全能选手", "创建至少 5 个不同分类的事件", categories.size() >= 5));
        list.add(new Achievement("目标规划师", "为至少 3 个事件设定坚持目标", habitsWithGoal >= 3));

        // 大师级成就
        list.add(new Achievement("坚持一年", "连续打卡达到 365 天", bestStreak >= 365));
        list.add(new Achievement("千次里程碑", "累计打卡满 1000 次", totalRecords >= 1000));
        list.add(new Achievement("事件大师", "创建至少 10 个打卡事件", habits.size() >= 10));

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

    /**
     * Restore database from a JSON backup file created by {@link #backupDatabaseToJson}.
     * This will REPLACE all existing data with the backup content.
     *
     * @param jsonFilePath absolute path to the backup JSON file
     * @return true if restore succeeded, false otherwise
     */
    @WorkerThread
    public boolean restoreFromJson(String jsonFilePath) {
        try {
            Logger.i("Starting database restore from: " + jsonFilePath);

            // Read and parse JSON file
            File backupFile = new File(jsonFilePath);
            if (!backupFile.exists()) {
                Logger.e("Backup file not found: " + jsonFilePath);
                return false;
            }

            StringBuilder jsonBuilder = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(backupFile),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }

            JSONObject backup = new JSONObject(jsonBuilder.toString());
            int backupVersion = backup.optInt("backup_version", 0);
            if (backupVersion != 1) {
                Logger.e("Unsupported backup version: " + backupVersion);
                return false;
            }

            JSONArray users = backup.getJSONArray("users");
            JSONArray habits = backup.getJSONArray("habits");
            JSONArray records = backup.getJSONArray("check_records");
            List<Habit> oldReminderHabits = getHabitsWithReminder();

            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                // Clear existing data
                db.delete("check_records", null, null);
                db.delete("habits", null, null);
                db.delete("users", null, null);

                // Restore users
                for (int i = 0; i < users.length(); i++) {
                    JSONObject user = users.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("id", user.getLong("id"));
                    values.put("username", user.getString("username"));
                    values.put("password", user.getString("password"));
                    if (user.has("salt") && !user.isNull("salt")) {
                        values.put("salt", user.getString("salt"));
                    }
                    if (user.has("display_name") && !user.isNull("display_name")) {
                        values.put("display_name", user.getString("display_name"));
                    }
                    if (user.has("avatar_uri") && !user.isNull("avatar_uri")) {
                        values.put("avatar_uri", user.getString("avatar_uri"));
                    }
                    db.insert("users", null, values);
                }

                // Restore habits
                for (int i = 0; i < habits.length(); i++) {
                    JSONObject habit = habits.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("id", habit.getLong("id"));
                    values.put("user_id", habit.getLong("user_id"));
                    values.put("title", habit.getString("title"));
                    values.put("content", habit.getString("content"));
                    values.put("time_text", habit.getString("time_text"));
                    if (habit.has("image_uri") && !habit.isNull("image_uri")) {
                        values.put("image_uri", habit.getString("image_uri"));
                    }
                    values.put("category", habit.getString("category"));
                    if (habit.has("reminder_time") && !habit.isNull("reminder_time")) {
                        values.put("reminder_time", habit.getString("reminder_time"));
                    }
                    values.put("check_count", habit.getInt("check_count"));
                    values.put("last_check_at", habit.getLong("last_check_at"));
                    values.put("created_at", habit.getLong("created_at"));
                    if (habit.has("frequency_type")) {
                        values.put("frequency_type", habit.getInt("frequency_type"));
                    }
                    if (habit.has("frequency_days") && !habit.isNull("frequency_days")) {
                        values.put("frequency_days", habit.getString("frequency_days"));
                    }
                    if (habit.has("frequency_count")) {
                        values.put("frequency_count", habit.getInt("frequency_count"));
                    }
                    if (habit.has("target_days")) {
                        values.put("target_days", habit.getInt("target_days"));
                    }
                    if (habit.has("sort_order")) {
                        values.put("sort_order", habit.getInt("sort_order"));
                    }
                    db.insert("habits", null, values);
                }

                // Restore check records
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("id", record.getLong("id"));
                    values.put("habit_id", record.getLong("habit_id"));
                    if (record.has("note") && !record.isNull("note")) {
                        values.put("note", record.getString("note"));
                    }
                    values.put("checked_at", record.getLong("checked_at"));
                    db.insert("check_records", null, values);
                }

                db.setTransactionSuccessful();
                Logger.i("Database restore completed successfully");
                Logger.i("Restored: " + users.length() + " users, " +
                        habits.length() + " habits, " + records.length() + " records");
            } finally {
                db.endTransaction();
            }

            for (Habit habit : oldReminderHabits) {
                ReminderReceiver.cancel(context, habit.id);
            }
            for (Habit habit : getHabitsWithReminder()) {
                ReminderReceiver.schedule(context, habit);
            }
            return true;

        } catch (Exception e) {
            Logger.e("Database restore failed", e);
            return false;
        }
    }

    /**
     * Read a habit row into a {@link Habit}, querying its check-ins and latest note individually.
     * Used for single-habit reads ({@link #getHabit}); list reads use {@link #readHabitBase} plus a
     * single batched pass (see {@link #queryHabits}) to avoid a per-row query storm.
     */
    private Habit readHabit(Cursor cursor) {
        Habit habit = readHabitBase(cursor);
        fillComputed(habit, getCheckedDays(habit.id), getLastNote(habit.id));
        return habit;
    }

    /**
     * Build a {@link Habit} from the row's stored columns only, leaving the derived fields
     * (streak, week count, total days, last note) at their zero/empty defaults for a caller to
     * fill via {@link #fillComputed}.
     */
    private Habit readHabitBase(Cursor cursor) {
        return new Habit(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("content")),
                cursor.getString(cursor.getColumnIndexOrThrow("time_text")),
                cursor.getString(cursor.getColumnIndexOrThrow("image_uri")),
                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getString(cursor.getColumnIndexOrThrow("reminder_time")),
                cursor.getInt(cursor.getColumnIndexOrThrow("check_count")),
                cursor.getLong(cursor.getColumnIndexOrThrow("last_check_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                0,
                "",
                getInt(cursor, "frequency_type", Habit.FREQ_DAILY),
                getString(cursor, "frequency_days", ""),
                getInt(cursor, "frequency_count", 0),
                getInt(cursor, "target_days", 0),
                0,
                0
        );
    }

    /** Fill the runtime-computed fields (streak/week/total/last note) from a precomputed day set. */
    private void fillComputed(Habit habit, Set<Long> checkedDays, String lastNote) {
        int[] streakWeekTotal = computeStreakWeekTotal(checkedDays, habit.frequencyType,
                habit.frequencyDays, habit.frequencyCount);
        habit.streakDays = streakWeekTotal[0];
        habit.weekCheckCount = streakWeekTotal[1];
        habit.totalDays = streakWeekTotal[2];
        habit.lastNote = lastNote;
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
    private int[] computeStreakWeekTotal(Set<Long> days, int frequencyType, String frequencyDays,
                                         int frequencyCount) {
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
        StringBuilder builder = new StringBuilder("本周打卡看板\n");
        long today = DateUtils.startOfDay(System.currentTimeMillis());
        int total = 0;
        int activeDays = 0;
        for (int i = 6; i >= 0; i--) {
            long day = today - i * DateUtils.DAY_MS;
            int count = getCheckedHabitCountForDay(day, userId);
            total += count;
            if (count > 0) {
                activeDays++;
            }
            builder.append(DateUtils.formatMonthDay(day))
                    .append("：")
                    .append(count)
                    .append(" 个事件\n");
        }
        builder.append("本周合计：").append(total).append(" 个事件，活跃 ")
                .append(activeDays).append(" 天");
        return builder.toString().trim();
    }

    public String buildWeekCompare(long userId) {
        long thisWeek = DateUtils.startOfWeek(System.currentTimeMillis());
        long lastWeek = thisWeek - 7 * DateUtils.DAY_MS;
        int thisCount = countRecordsInRange(userId, thisWeek, thisWeek + 7 * DateUtils.DAY_MS);
        int lastCount = countRecordsInRange(userId, lastWeek, thisWeek);
        int thisActive = countActiveDaysInRange(userId, thisWeek, thisWeek + 7 * DateUtils.DAY_MS);
        int lastActive = countActiveDaysInRange(userId, lastWeek, thisWeek);
        return "周统计对比\n"
                + "本周打卡记录：" + thisCount + " 次，活跃 " + thisActive + " 天\n"
                + "上周打卡记录：" + lastCount + " 次，活跃 " + lastActive + " 天\n"
                + "变化：" + signedDiff(thisCount - lastCount) + " 次";
    }

    public String buildMonthSummary(long userId) {
        long monthStart = DateUtils.startOfMonth(System.currentTimeMillis());
        long nextMonth = DateUtils.addMonths(monthStart, 1);
        Map<Long, Integer> counts = getDailyCheckCounts(userId, monthStart, nextMonth);
        int totalRecords = 0;
        int activeDays = 0;
        int bestDay = 0;
        long cursor = monthStart;
        while (cursor < nextMonth) {
            Integer count = counts.get(cursor);
            if (count != null && count > 0) {
                totalRecords += count;
                activeDays++;
                bestDay = Math.max(bestDay, count);
            }
            cursor += DateUtils.DAY_MS;
        }
        int daysInMonth = DateUtils.daysInMonth(monthStart);
        double activeRate = daysInMonth == 0 ? 0 : activeDays * 100.0 / daysInMonth;
        return String.format(Locale.CHINA,
                "本月打卡看板（%s）\n打卡记录：%d 次\n活跃天数：%d / %d 天\n活跃率：%.1f%%\n单日最高：%d 次",
                DateUtils.formatYearMonth(monthStart), totalRecords, activeDays, daysInMonth,
                activeRate, bestDay);
    }

    public String buildMonthCompare(long userId) {
        long thisMonth = DateUtils.startOfMonth(System.currentTimeMillis());
        long nextMonth = DateUtils.addMonths(thisMonth, 1);
        long lastMonth = DateUtils.addMonths(thisMonth, -1);
        int thisCount = countRecordsInRange(userId, thisMonth, nextMonth);
        int lastCount = countRecordsInRange(userId, lastMonth, thisMonth);
        int thisActive = countActiveDaysInRange(userId, thisMonth, nextMonth);
        int lastActive = countActiveDaysInRange(userId, lastMonth, thisMonth);
        return "月统计对比\n"
                + "本月打卡记录：" + thisCount + " 次，活跃 " + thisActive + " 天\n"
                + "上月打卡记录：" + lastCount + " 次，活跃 " + lastActive + " 天\n"
                + "变化：" + signedDiff(thisCount - lastCount) + " 次";
    }

    private int countRecordsInRange(long userId, long fromInclusive, long toExclusive) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM check_records r JOIN habits h ON r.habit_id=h.id " +
                "WHERE h.user_id=? AND r.checked_at>=? AND r.checked_at<?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(userId),
                String.valueOf(fromInclusive), String.valueOf(toExclusive)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private int countActiveDaysInRange(long userId, long fromInclusive, long toExclusive) {
        return getDailyCheckCounts(userId, fromInclusive, toExclusive).size();
    }

    private String signedDiff(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
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
