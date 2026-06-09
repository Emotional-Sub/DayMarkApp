package com.example.daymark;

/**
 * A single milestone shown on the stats page. Achievements are derived from aggregate
 * check-in data at read time (see {@link DayMarkDbHelper#getAchievements}); there is no
 * dedicated table, so they always reflect the user's current data.
 */
public class Achievement {
    public final String title;
    public final String description;
    public final boolean unlocked;

    public Achievement(String title, String description, boolean unlocked) {
        this.title = title;
        this.description = description;
        this.unlocked = unlocked;
    }
}
