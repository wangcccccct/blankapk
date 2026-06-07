package com.example.blankapkgenerator;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public final class ApkStorage {
    private static final String MIME_TYPE = "application/vnd.android.package-archive";

    private ApkStorage() {
    }

    public static SavedApk save(Context context, String fileName, byte[] apkBytes) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE);
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/BlankApkGenerator");
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = context.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("无法在系统下载目录创建 APK 文件");
            }
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri, "w")) {
                if (outputStream == null) {
                    throw new IllegalStateException("无法打开下载目录输出流");
                }
                outputStream.write(apkBytes);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
            return new SavedApk(
                    uri,
                    "已保存到系统下载目录 /Download/BlankApkGenerator/" + fileName,
                    apkBytes.length,
                    null);
        }

        File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (outputDir == null) {
            outputDir = new File(context.getFilesDir(), "exports");
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("无法创建输出目录：" + outputDir.getAbsolutePath());
        }
        File outputFile = new File(outputDir, fileName);
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(apkBytes);
        }
        Uri shareUri = FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                outputFile);
        return new SavedApk(
                shareUri,
                "已保存到应用下载目录 " + outputFile.getAbsolutePath(),
                apkBytes.length,
                outputFile);
    }

    public static final class SavedApk {
        public final Uri shareUri;
        public final String locationDescription;
        public final long fileSize;
        public final File file;

        SavedApk(Uri shareUri, String locationDescription, long fileSize, File file) {
            this.shareUri = shareUri;
            this.locationDescription = locationDescription;
            this.fileSize = fileSize;
            this.file = file;
        }
    }
}
