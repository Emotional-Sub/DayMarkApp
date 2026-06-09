package com.example.daymark;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private DateUtils() {
    }

    public static long startOfDay(long time) {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** Start-of-day timestamp for the Monday of the week containing {@code time}. */
    public static long startOfWeek(long time) {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.setTimeInMillis(startOfDay(time));
        int dow = calendar.get(Calendar.DAY_OF_WEEK);
        // Calendar.SUNDAY == 1 ... SATURDAY == 7; we treat Monday as the first day.
        int daysFromMonday = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
        return calendar.getTimeInMillis();
    }

    /**
     * Day-of-week as 1=Monday ... 7=Sunday (ISO order), matching how habit
     * frequency days are stored.
     */
    public static int isoDayOfWeek(long time) {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.setTimeInMillis(time);
        int dow = calendar.get(Calendar.DAY_OF_WEEK);
        return (dow == Calendar.SUNDAY) ? 7 : dow - 1;
    }

    public static boolean isToday(long time) {
        return time > 0 && startOfDay(time) == startOfDay(System.currentTimeMillis());
    }

    public static String formatDate(long time) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(time));
    }

    public static String formatDateTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(time));
    }

    public static String formatMonthDay(long time) {
        return new SimpleDateFormat("MM-dd", Locale.CHINA).format(new Date(time));
    }
}
