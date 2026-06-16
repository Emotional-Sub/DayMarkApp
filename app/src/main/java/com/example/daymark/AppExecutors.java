package com.example.daymark;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Shared thread executors for background work plus a main-thread poster.
 *
 * Uses a 3-thread pool to support concurrent reads and image loading while maintaining ordered
 * writes. SQLite in WAL mode supports read-write concurrency, so multiple operations can proceed
 * in parallel without blocking each other.
 */
public final class AppExecutors {
    // Small thread pool (3 threads) for concurrent database reads and image loading.
    // SQLite write operations are still serialized by SQLite itself in WAL mode.
    private static final Executor DISK = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r);
        t.setName("DayMark-IO-" + t.getId());
        return t;
    });

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
