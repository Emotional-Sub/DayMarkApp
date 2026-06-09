package com.example.daymark;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * One shared single-thread background executor plus a main-thread poster, so database work can be
 * moved off the UI thread without each Activity spinning up its own thread. A single background
 * thread is enough here (the DB is the only contended resource and SQLite serializes writes anyway)
 * and keeps results ordered: tasks run in submit order.
 */
public final class AppExecutors {
    private static final Executor DISK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Posts to the main thread; wraps the main-looper Handler as an Executor. */
    private static final Executor MAIN_EXECUTOR = MAIN::post;

    private AppExecutors() {
    }

    /** The shared background executor for database/disk work. */
    public static Executor io() {
        return DISK;
    }

    /** An executor that posts to the main (UI) thread. */
    public static Executor main() {
        return MAIN_EXECUTOR;
    }
}
