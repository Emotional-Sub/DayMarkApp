package com.example.daymark;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "daymark_reminder";
    private static final String EXTRA_HABIT_ID = "habit_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_MESSAGE = "message";

    @Override
    public void onReceive(Context context, Intent intent) {
        long habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1);
        String title = intent.getStringExtra(EXTRA_TITLE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);

        // Verify the habit still exists before showing notification. If it was deleted
        // after the alarm was scheduled but before it fired, silently skip the notification.
        if (habitId > 0) {
            DayMarkDbHelper dbHelper = new DayMarkDbHelper(context);
            Habit habit = dbHelper.getHabit(habitId);
            if (habit == null) {
                return; // Habit was deleted; don't notify.
            }
            // Refresh title/message from the current habit data in case it was edited.
            title = habit.title;
            message = context instanceof android.content.ContextWrapper
                    ? dbHelper.buildReminderMessage(habit) : message;
        }

        if (TextUtils.isEmpty(title)) {
            title = "DayMark 打卡提醒";
        }
        if (TextUtils.isEmpty(message)) {
            message = "记得完成今天的打卡哦";
        }
        showNotification(context, habitId, title, message);
    }

    private void showNotification(Context context, long habitId, String title, String message) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "打卡提醒", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("DayMark 每日打卡提醒");
            manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, (int) habitId, openIntent, pendingIntentFlags());

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        manager.notify((int) habitId, builder.build());
    }

    /**
     * Schedule a daily reminder for the given habit. The reminder time must be in HH:mm format.
     * An empty or invalid time cancels any existing reminder instead.
     */
    public static void schedule(Context context, Habit habit) {
        if (habit == null || TextUtils.isEmpty(habit.reminderTime)) {
            cancel(context, habit == null ? -1 : habit.id);
            return;
        }
        int[] hm = parseTime(habit.reminderTime);
        if (hm == null) {
            cancel(context, habit.id);
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hm[0]);
        next.set(Calendar.MINUTE, hm[1]);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Inexact daily repeat avoids needing the exact-alarm permission on API 31+.
        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                next.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                buildPendingIntent(context, habit));
    }

    public static void cancel(Context context, long habitId) {
        if (habitId < 0) {
            return;
        }
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, (int) habitId, intent, pendingIntentFlags());
        alarmManager.cancel(pendingIntent);
    }

    private static PendingIntent buildPendingIntent(Context context, Habit habit) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(EXTRA_HABIT_ID, habit.id);
        intent.putExtra(EXTRA_TITLE, habit.title);
        intent.putExtra(EXTRA_MESSAGE, "到时间啦：" + habit.timeText);
        return PendingIntent.getBroadcast(
                context, (int) habit.id, intent, pendingIntentFlags());
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private static int[] parseTime(String text) {
        String[] parts = text.trim().split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return new int[]{hour, minute};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
