package com.example.daymark;

/**
 * Per-category check-in summary used on the profile page: how many habits fall in this
 * category and how many check-in records they have accumulated in total.
 */
public class CategoryStat {
    public final String category;
    public final int habitCount;
    public final int checkCount;

    public CategoryStat(String category, int habitCount, int checkCount) {
        this.category = category;
        this.habitCount = habitCount;
        this.checkCount = checkCount;
    }
}
