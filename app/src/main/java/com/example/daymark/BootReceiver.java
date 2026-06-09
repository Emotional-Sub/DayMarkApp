package com.example.daymark;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Re-registers every habit's reminder after the device reboots. AlarmManager drops all alarms
 * on reboot, so without this any reminder set before a restart would silently stop firing.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        // ACTION_BOOT_COMPLETED is the normal case; the others cover quick-boot / update-replace
        // so reminders survive those too.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        DayMarkDbHelper dbHelper = new DayMarkDbHelper(context);
        List<Habit> habits = dbHelper.getHabitsWithReminder();
        for (Habit habit : habits) {
            ReminderReceiver.schedule(context, habit);
        }
    }
}
