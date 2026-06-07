package com.example.blankapkgenerator;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MinimalApkGenerator {
    private static final String TEMPLATE_ASSET_NAME = "template-base-unsigned.apk";
    static final String PACKAGE_PLACEHOLDER = "com.placeholder.placeholder.placeholder.placeholder.placeholder";
    static final String LABEL_PLACEHOLDER = "APP_NAME_PLACEHOLDER_APP_NAME_PLACEHOLDER_APP_NAME_PLACEHOLDER";
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$");

    private final Context context;
    private final SigningKeyManager signingKeyManager;

    public MinimalApkGenerator(Context context) {
        this.context = context.getApplicationContext();
        this.signingKeyManager = new SigningKeyManager(this.context);
    }

    public GenerationResult generate(String packageName, String appName) throws Exception {
        if (!isValidPackageName(packageName)) {
            throw new IllegalArgumentException("包名不合法");
        }
        if (appName.isEmpty()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (packageName.getBytes(StandardCharsets.UTF_8).length
                > PACKAGE_PLACEHOLDER.getBytes(StandardCharsets.UTF_8).length) {
            throw new IllegalArgumentException("包名过长，当前模板最多支持 "
                    + PACKAGE_PLACEHOLDER.getBytes(StandardCharsets.UTF_8).length + " 字节");
        }
        if (appName.getBytes(StandardCharsets.UTF_8).length
                > LABEL_PLACEHOLDER.getBytes(StandardCharsets.UTF_8).length) {
            throw new IllegalArgumentException("名称过长，当前模板最多支持 "
                    + LABEL_PLACEHOLDER.getBytes(StandardCharsets.UTF_8).length + " 字节");
        }

        byte[] templateBytes = readAsset(TEMPLATE_ASSET_NAME);
        LinkedHashMap<String, byte[]> entries = ZipUtils.readZip(templateBytes);
        byte[] manifestBytes = entries.get("AndroidManifest.xml");
        if (manifestBytes == null) {
            throw new IllegalStateException("模板缺少 AndroidManifest.xml");
        }

        manifestBytes = BinaryXmlStringPoolPatcher.replaceUtf8String(
                manifestBytes, PACKAGE_PLACEHOLDER, packageName);
        manifestBytes = BinaryXmlStringPoolPatcher.replaceUtf8String(
                manifestBytes, LABEL_PLACEHOLDER, appName);
        entries.put("AndroidManifest.xml", manifestBytes);

        byte[] unsignedApk = ZipUtils.writeZip(entries);
        SigningKeyManager.KeyMaterial keyMaterial = signingKeyManager.getOrCreate();
        byte[] signedApk = V1ApkSigner.sign(unsignedApk, keyMaterial.privateKey, keyMaterial.certificate, "GEN");

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String fileName = sanitizeFileStem(appName) + "-"
                + sanitizeFileStem(packageName) + "-" + timestamp + ".apk";
        ApkStorage.SavedApk savedApk = ApkStorage.save(context, fileName, signedApk);
        return new GenerationResult(savedApk, signedApk.length,
                SigningKeyManager.fingerprintSha256(keyMaterial.certificate));
    }

    public String getCurrentSignerFingerprint() throws Exception {
        return SigningKeyManager.fingerprintSha256(signingKeyManager.getOrCreate().certificate);
    }

    public void rotateSigningKey() throws Exception {
        signingKeyManager.rotate();
    }

    public static boolean isValidPackageName(String packageName) {
        return PACKAGE_PATTERN.matcher(packageName).matches();
    }

    private byte[] readAsset(String name) throws Exception {
        try (InputStream inputStream = context.getAssets().open(name);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static String sanitizeFileStem(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return sanitized.isEmpty() ? "blank" : sanitized;
    }

    public static final class GenerationResult {
        public final ApkStorage.SavedApk savedApk;
        public final long fileSize;
        public final String signerFingerprint;

        GenerationResult(ApkStorage.SavedApk savedApk, long fileSize, String signerFingerprint) {
            this.savedApk = savedApk;
            this.fileSize = fileSize;
            this.signerFingerprint = signerFingerprint;
        }
    }
}
