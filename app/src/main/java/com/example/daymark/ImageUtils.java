package com.example.daymark;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public static byte[] readImageBytes(Context context, String imageUri) throws IOException {
        if (context == null || TextUtils.isEmpty(imageUri) || imageUri.startsWith("default_")) {
            return null;
        }
        Uri uri = Uri.parse(imageUri);
        try (InputStream inputStream = openImageInputStream(context, uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    public static String restoreBackupImage(Context context, String base64Data,
                                            String preferredName, String directoryName)
            throws IOException {
        if (context == null || TextUtils.isEmpty(base64Data) || TextUtils.isEmpty(directoryName)) {
            return null;
        }
        byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
        if (data.length == 0) {
            return null;
        }

        File directory = getPrivateImageDirectory(context, directoryName);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create image restore directory");
        }

        String safeName = sanitizeFileName(preferredName);
        if (TextUtils.isEmpty(safeName)) {
            safeName = directoryName + "_" + System.currentTimeMillis() + ".jpg";
        }
        if (!safeName.contains(".")) {
            safeName += ".jpg";
        }

        File dest = uniqueFile(directory, safeName);
        try (FileOutputStream outputStream = new FileOutputStream(dest)) {
            outputStream.write(data);
        }
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", dest);
        return uri.toString();
    }

    public static String backupFileNameFromUri(String imageUri, String fallbackName) {
        if (TextUtils.isEmpty(imageUri)) {
            return fallbackName;
        }
        try {
            Uri uri = Uri.parse(imageUri);
            String lastSegment = uri.getLastPathSegment();
            String safeName = sanitizeFileName(lastSegment);
            return TextUtils.isEmpty(safeName) ? fallbackName : safeName;
        } catch (Exception e) {
            return fallbackName;
        }
    }

    private static InputStream openImageInputStream(Context context, Uri uri) throws IOException {
        if (uri == null) {
            return null;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new FileInputStream(new File(uri.getPath()));
        }
        return context.getContentResolver().openInputStream(uri);
    }

    private static File getPrivateImageDirectory(Context context, String directoryName) {
        if ("habit_photos".equals(directoryName)) {
            return new File(context.getFilesDir(), "habit_photos");
        }
        if ("avatar_pictures".equals(directoryName)) {
            return new File(context.getFilesDir(), "avatar_pictures");
        }
        return new File(context.getFilesDir(), directoryName);
    }

    private static String sanitizeFileName(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static File uniqueFile(File directory, String fileName) {
        File candidate = new File(directory, fileName);
        if (!candidate.exists()) {
            return candidate;
        }
        int dotIndex = fileName.lastIndexOf('.');
        String base = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        int index = 1;
        do {
            candidate = new File(directory, base + "_" + index + extension);
            index++;
        } while (candidate.exists());
        return candidate;
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
