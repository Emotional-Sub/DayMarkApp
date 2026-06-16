package com.example.daymark;

import android.util.Log;

/**
 * Centralized logging utility for DayMark. Provides consistent tagging and level control.
 * In production builds, only warnings and errors are logged; in debug builds, all levels are active.
 *
 * <p>Usage:
 * <pre>
 *   Logger.d("User logged in: " + username);
 *   Logger.e("Database upgrade failed", exception);
 * </pre>
 */
public final class Logger {
    private static final String TAG = "DayMark";

    /**
     * Set to false in production builds to disable debug/info logs.
     * Can be controlled via BuildConfig.DEBUG.
     */
    private static final boolean DEBUG = true;

    private Logger() {
    }

    /**
     * Log a debug message. Only active when DEBUG is true.
     * Use for detailed flow information during development.
     */
    public static void d(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    /**
     * Log an info message. Only active when DEBUG is true.
     * Use for general informational messages.
     */
    public static void i(String message) {
        if (DEBUG) {
            Log.i(TAG, message);
        }
    }

    /**
     * Log a warning message. Always active.
     * Use for recoverable errors or unexpected states.
     */
    public static void w(String message) {
        Log.w(TAG, message);
    }

    /**
     * Log a warning with an exception. Always active.
     */
    public static void w(String message, Throwable throwable) {
        Log.w(TAG, message, throwable);
    }

    /**
     * Log an error message. Always active.
     * Use for serious errors that affect functionality.
     */
    public static void e(String message) {
        Log.e(TAG, message);
    }

    /**
     * Log an error with an exception. Always active.
     */
    public static void e(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }

    /**
     * Log database operation start. Only active when DEBUG is true.
     */
    public static void dbStart(String operation) {
        if (DEBUG) {
            Log.d(TAG, "[DB] " + operation);
        }
    }

    /**
     * Log database operation success. Only active when DEBUG is true.
     */
    public static void dbSuccess(String operation) {
        if (DEBUG) {
            Log.d(TAG, "[DB] ✓ " + operation + " succeeded");
        }
    }

    /**
     * Log database operation failure. Always active.
     */
    public static void dbError(String operation, Throwable throwable) {
        Log.e(TAG, "[DB] ✗ " + operation + " failed", throwable);
    }

    /**
     * Log image loading failure. Always active.
     */
    public static void imageError(String uri, Throwable throwable) {
        Log.w(TAG, "[Image] Failed to load: " + uri, throwable);
    }

    /**
     * Log encryption/security operation failure. Always active.
     */
    public static void securityError(String operation, Throwable throwable) {
        Log.e(TAG, "[Security] " + operation + " failed", throwable);
    }
}
