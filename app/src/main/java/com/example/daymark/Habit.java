package com.example.daymark;

import android.text.TextUtils;

import java.util.LinkedHashSet;
import java.util.Set;

public class Habit {
    /** Check in every day. */
    public static final int FREQ_DAILY = 0;
    /** Check in on specific weekdays (see {@link #frequencyDays}). */
    public static final int FREQ_WEEKLY_DAYS = 1;
    /** Check in a target number of times per week (see {@link #frequencyCount}). */
    public static final int FREQ_WEEKLY_COUNT = 2;

    private static final String[] WEEKDAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};

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

    public int frequencyType;
    /** Comma-separated ISO weekday numbers (1=Mon..7=Sun); only used for {@link #FREQ_WEEKLY_DAYS}. */
    public String frequencyDays;
    /** Target check-ins per week; only used for {@link #FREQ_WEEKLY_COUNT}. */
    public int frequencyCount;
    /** Target number of distinct days to check in; 0 means no goal set. */
    public int targetDays;

    /** Distinct days checked in this calendar week (Mon-based); filled by the DB layer. */
    public int weekCheckCount;
    /** Total distinct days ever checked in; filled by the DB layer and used for goal progress. */
    public int totalDays;

    public Habit(long id, String title, String content, String timeText, String imageUri,
                 String category, String reminderTime, int checkCount, long lastCheckAt,
                 long createdAt, int streakDays, String lastNote, int frequencyType,
                 String frequencyDays, int frequencyCount, int targetDays, int weekCheckCount,
                 int totalDays) {
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
        this.frequencyType = frequencyType;
        this.frequencyDays = frequencyDays == null ? "" : frequencyDays;
        this.frequencyCount = frequencyCount;
        this.targetDays = targetDays;
        this.weekCheckCount = weekCheckCount;
        this.totalDays = totalDays;
    }

    public boolean isCheckedToday() {
        return DateUtils.isToday(lastCheckAt);
    }

    /**
     * Whether this habit is scheduled for today and still needs a check-in.
     * Daily habits are always due; weekday habits only on listed days; weekly-count
     * habits are due until the weekly target is met.
     */
    public boolean isDueToday() {
        if (isCheckedToday()) {
            return false;
        }
        switch (frequencyType) {
            case FREQ_WEEKLY_DAYS:
                return parseDays().contains(DateUtils.isoDayOfWeek(System.currentTimeMillis()));
            case FREQ_WEEKLY_COUNT:
                return weekCheckCount < Math.max(1, frequencyCount);
            case FREQ_DAILY:
            default:
                return true;
        }
    }

    /** True when today is part of this habit's schedule, regardless of check-in state. */
    public boolean isScheduledToday() {
        if (frequencyType == FREQ_WEEKLY_DAYS) {
            return parseDays().contains(DateUtils.isoDayOfWeek(System.currentTimeMillis()));
        }
        return true;
    }

    public Set<Integer> parseDays() {
        Set<Integer> days = new LinkedHashSet<>();
        if (TextUtils.isEmpty(frequencyDays)) {
            return days;
        }
        for (String part : frequencyDays.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    days.add(Integer.parseInt(trimmed));
                } catch (NumberFormatException ignored) {
                    // Skip malformed entries; the rest of the schedule still applies.
                }
            }
        }
        return days;
    }

    /** Short human-readable description of the frequency, e.g. "每天" or "每周一/三/五". */
    public String frequencyLabel() {
        switch (frequencyType) {
            case FREQ_WEEKLY_DAYS:
                Set<Integer> days = parseDays();
                if (days.isEmpty()) {
                    return "每周";
                }
                StringBuilder builder = new StringBuilder("每周");
                boolean first = true;
                for (int day : days) {
                    if (day >= 1 && day <= 7) {
                        if (!first) {
                            builder.append('/');
                        }
                        builder.append(WEEKDAY_NAMES[day - 1]);
                        first = false;
                    }
                }
                return builder.toString();
            case FREQ_WEEKLY_COUNT:
                return "每周 " + Math.max(1, frequencyCount) + " 次";
            case FREQ_DAILY:
            default:
                return "每天";
        }
    }

    /** Streak phrasing whose unit matches the frequency (天 for day-based, 周 for weekly-count). */
    public String streakLabel() {
        if (frequencyType == FREQ_WEEKLY_COUNT) {
            return "连续 " + streakDays + " 周";
        }
        return "连续 " + streakDays + " 天";
    }

    public boolean hasGoal() {
        return targetDays > 0;
    }

    /** Goal progress as a 0-100 percentage based on total distinct days checked in. */
    public int goalProgress() {
        if (!hasGoal()) {
            return 0;
        }
        int percent = (int) Math.round(totalDays * 100.0 / targetDays);
        return Math.min(100, Math.max(0, percent));
    }

    public boolean goalReached() {
        return hasGoal() && totalDays >= targetDays;
    }
}
