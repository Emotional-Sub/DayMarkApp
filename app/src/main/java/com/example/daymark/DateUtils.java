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
