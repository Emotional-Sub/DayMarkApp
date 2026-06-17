package com.example.daymark;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageUtils {
    /**
     * 创建一个临时图片文件用于拍照
     */
    public static File createImageFile(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            storageDir = new File(context.getFilesDir(), "avatar_pictures");
        }
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            throw new IOException("Failed to create avatar picture directory");
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    /**
     * Delete an app-owned image referenced by a file:// or this app's FileProvider content:// URI.
     * External gallery/document URIs are left untouched because the app does not own them.
     */
    public static boolean deleteOwnedImage(Context context, String imageUri) {
        if (context == null || TextUtils.isEmpty(imageUri)) {
            return false;
        }
        try {
            File file = resolveOwnedFile(context, Uri.parse(imageUri));
            return file != null && file.exists() && file.delete();
        } catch (Exception e) {
            return false;
        }
    }

    private static File resolveOwnedFile(Context context, Uri uri) throws IOException {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            File direct = new File(uri.getPath());
            return isOwnedFile(context, direct) ? direct : null;
        }
        if (!"content".equalsIgnoreCase(scheme)) {
            return null;
        }
        if (!TextUtils.equals(context.getPackageName() + ".fileprovider", uri.getAuthority())) {
            return null;
        }
        java.util.List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() < 2) {
            return null;
        }
        String root = segments.get(0);
        String relativePath = TextUtils.join(File.separator, segments.subList(1, segments.size()));
        File baseDir;
        if ("habit_photos".equals(root)) {
            baseDir = new File(context.getFilesDir(), "habit_photos");
        } else if ("avatar_pictures".equals(root)) {
            baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (baseDir == null) {
                baseDir = new File(context.getFilesDir(), "avatar_pictures");
            }
        } else if ("avatar_private".equals(root)) {
            baseDir = new File(context.getFilesDir(), "avatar_pictures");
        } else {
            return null;
        }
        File candidate = new File(baseDir, relativePath);
        return isWithin(baseDir, candidate) ? candidate : null;
    }

    private static boolean isOwnedFile(Context context, File file) throws IOException {
        if (file == null) {
            return false;
        }
        File filesDir = context.getFilesDir();
        if (filesDir != null && isWithin(filesDir, file)) {
            return true;
        }
        File externalRoot = context.getExternalFilesDir(null);
        return externalRoot != null && isWithin(externalRoot, file);
    }

    private static boolean isWithin(File baseDir, File child) throws IOException {
        if (baseDir == null || child == null) {
            return false;
        }
        String basePath = baseDir.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        return childPath.equals(basePath)
                || childPath.startsWith(basePath + File.separator);
    }
}
