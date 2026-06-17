package com.example.daymark;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Date helpers for slicing check-in timestamps into days/weeks.
 *
 * <p>All calculations are pinned to a single fixed time zone ({@link #ZONE}) rather than the
 * device's current default. Check-in times are stored as absolute epoch millis, so if day
 * boundaries were derived from the device zone, changing the system time zone (or travelling)
 * would silently re-bucket past records — shifting streaks and the heatmap. Pinning the zone
 * keeps a given instant on the same calendar day forever. The app is China-locale throughout and
 * China observes no DST, so {@code Asia/Shanghai} is a stable UTC+8 with no daylight transitions.
 *
 * <p><b>IMPORTANT:</b> This app is designed for users in China (UTC+8). If used in other time zones,
 * "today" boundaries will be offset from local midnight. To support global users, the time zone
 * would need to be configurable per user or derived from device settings.
 */
public class DateUtils {
    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /** Fixed zone all day/week math is computed in; see the class note for why. */
    private static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    private DateUtils() {
    }

    private static Calendar calendar(long time) {
        Calendar calendar = Calendar.getInstance(ZONE, Locale.CHINA);
        calendar.setTimeInMillis(time);
        return calendar;
    }

    private static SimpleDateFormat formatter(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.CHINA);
        format.setTimeZone(ZONE);
        return format;
    }

    public static long startOfDay(long time) {
        Calendar calendar = calendar(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** Start-of-day timestamp for the Monday of the week containing {@code time}. */
    public static long startOfWeek(long time) {
        Calendar calendar = calendar(startOfDay(time));
        int dow = calendar.get(Calendar.DAY_OF_WEEK);
        // Calendar.SUNDAY == 1 ... SATURDAY == 7; we treat Monday as the first day.
        int daysFromMonday = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
        return calendar.getTimeInMillis();
    }

    public static long startOfMonth(long time) {
        Calendar calendar = calendar(startOfDay(time));
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    public static long addMonths(long time, int months) {
        Calendar calendar = calendar(time);
        calendar.add(Calendar.MONTH, months);
        return calendar.getTimeInMillis();
    }

    public static int daysInMonth(long time) {
        return calendar(time).getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /**
     * Day-of-week as 1=Monday ... 7=Sunday (ISO order), matching how habit
     * frequency days are stored.
     */
    public static int isoDayOfWeek(long time) {
        Calendar calendar = calendar(time);
        int dow = calendar.get(Calendar.DAY_OF_WEEK);
        return (dow == Calendar.SUNDAY) ? 7 : dow - 1;
    }

    public static boolean isToday(long time) {
        return time > 0 && startOfDay(time) == startOfDay(System.currentTimeMillis());
    }

    public static String formatDate(long time) {
        return formatter("yyyy-MM-dd").format(new Date(time));
    }

    public static String formatDateTime(long time) {
        return formatter("yyyy-MM-dd HH:mm").format(new Date(time));
    }

    public static String formatMonthDay(long time) {
        return formatter("MM-dd").format(new Date(time));
    }

    public static String formatYearMonth(long time) {
        return formatter("yyyy-MM").format(new Date(time));
    }

    /** Month of the given instant as 1=January ... 12=December. */
    public static int monthOfYear(long time) {
        return calendar(time).get(Calendar.MONTH) + 1;
    }
}
