package com.example.blankapkgenerator;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextInputLayout packageLayout;
    private TextInputLayout nameLayout;
    private TextInputEditText packageInput;
    private TextInputEditText nameInput;
    private TextView keyInfoText;
    private TextView statusText;
    private MaterialButton generateButton;
    private MaterialButton rotateKeyButton;
    private MaterialButton shareButton;
    private MinimalApkGenerator apkGenerator;
    private ApkStorage.SavedApk lastSavedApk;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        packageLayout = findViewById(R.id.packageLayout);
        nameLayout = findViewById(R.id.nameLayout);
        packageInput = findViewById(R.id.packageInput);
        nameInput = findViewById(R.id.nameInput);
        keyInfoText = findViewById(R.id.keyInfoText);
        statusText = findViewById(R.id.statusText);
        generateButton = findViewById(R.id.generateButton);
        rotateKeyButton = findViewById(R.id.rotateKeyButton);
        shareButton = findViewById(R.id.shareButton);
        apkGenerator = new MinimalApkGenerator(this);

        packageInput.setText("com.example.generated");
        nameInput.setText("Blank");
        statusText.setText("等待生成。");

        generateButton.setOnClickListener(view -> generateApk());
        rotateKeyButton.setOnClickListener(view -> rotateSigningKey());
        shareButton.setOnClickListener(view -> shareLastApk());

        refreshKeyInfo();
    }

    private void generateApk() {
        clearInputErrors();

        String packageName = textOf(packageInput);
        String appName = textOf(nameInput);
        boolean hasError = false;

        if (!MinimalApkGenerator.isValidPackageName(packageName)) {
            packageLayout.setError("包名不合法，需类似 com.example.demo");
            hasError = true;
        }
        if (TextUtils.isEmpty(appName)) {
            nameLayout.setError("名称不能为空");
            hasError = true;
        }
        if (hasError) {
            return;
        }

        setBusy(true, "正在生成 APK…");
        shareButton.setEnabled(false);
        executor.execute(() -> {
            try {
                MinimalApkGenerator.GenerationResult result = apkGenerator.generate(packageName, appName);
                runOnUiThread(() -> {
                    lastSavedApk = result.savedApk;
                    shareButton.setEnabled(true);
                    setBusy(false, "生成完成并已保存到本地：\n"
                            + result.savedApk.locationDescription
                            + "\n大小：" + result.fileSize + " bytes"
                            + "\n签名指纹：" + result.signerFingerprint);
                    refreshKeyInfo();
                });
            } catch (IllegalArgumentException e) {
                runOnUiThread(() -> {
                    setBusy(false, e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("包名")) {
                        packageLayout.setError(e.getMessage());
                    } else if (e.getMessage() != null && e.getMessage().contains("名称")) {
                        nameLayout.setError(e.getMessage());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    lastSavedApk = null;
                    shareButton.setEnabled(false);
                    setBusy(false, "生成失败："
                            + e.getClass().getSimpleName()
                            + " - "
                            + (e.getMessage() == null ? "(无详情)" : e.getMessage()));
                });
            }
        });
    }

    private void rotateSigningKey() {
        setBusy(true, "正在重建独立签名密钥…");
        executor.execute(() -> {
            try {
                apkGenerator.rotateSigningKey();
                runOnUiThread(() -> {
                    setBusy(false, "已重建签名密钥。之后生成的 APK 都会使用新的本地密钥。");
                    refreshKeyInfo();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false, "重建密钥失败："
                        + e.getClass().getSimpleName()
                        + " - "
                        + (e.getMessage() == null ? "(无详情)" : e.getMessage())));
            }
        });
    }

    private void refreshKeyInfo() {
        executor.execute(() -> {
            try {
                String fingerprint = apkGenerator.getCurrentSignerFingerprint();
                runOnUiThread(() -> keyInfoText.setText(
                        "本地独立签名（首次使用时自动生成，不复用现有签名）\nSHA-256: " + fingerprint));
            } catch (Exception e) {
                runOnUiThread(() -> keyInfoText.setText("读取签名信息失败：" + e.getMessage()));
            }
        });
    }

    private void clearInputErrors() {
        packageLayout.setError(null);
        nameLayout.setError(null);
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void setBusy(boolean busy, String status) {
        generateButton.setEnabled(!busy);
        rotateKeyButton.setEnabled(!busy);
        if (busy) {
            shareButton.setEnabled(false);
        } else if (lastSavedApk != null) {
            shareButton.setEnabled(true);
        }
        statusText.setText(status);
    }

    private void shareLastApk() {
        if (lastSavedApk == null) {
            statusText.setText("还没有可分享的 APK，请先生成一次。");
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/vnd.android.package-archive");
        shareIntent.putExtra(Intent.EXTRA_STREAM, lastSavedApk.shareUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "分享 APK"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
