# blankapk

这个仓库现在包含两部分：

1. 一个“可安装、桌面可见、但几乎完全无功能”的极限压缩 Android APK 模板
2. 一个 Android 生成器 APK：可在手机上输入包名/名称，然后生成这种极小 APK

## 极小 APK 模板结构

- `src/AndroidManifest.xml`：单行压缩 manifest，只保留最小 launcher 壳
- `src/a/b/A.java`：最小占位代码类，用来生成 `classes.dex`
- `build.sh`：直接调用 Android SDK build-tools 打包，并用最小 EC 证书做 v1-only 签名

### 构建模板 APK

```bash
./build.sh
```

产物默认输出到：

- `out/blankapk.apk`

### 模板说明

- 这是“桌面可见 + 可安装”的极限压缩版本。
- 现在会额外带一个**极小的 `classes.dex`**，用来避免安装时报 `code is missing`。
- 为了继续压缩，签名改成 **ECDSA + v1-only**。
- `targetSdkVersion=24`，可兼顾当前 Android 15 安装门槛，同时省掉 `android:exported`。
- `minSdkVersion=24` 也是为压缩服务；如果你要更老系统兼容，可以把它调高/调低后重打，但体积会略变大。
- launcher 图标来自 manifest 声明；点击后预期仍可能失败，因为占位类不是实际 Activity 实现。

## 生成器 APK

### 关键结构

- `app/`：Android 生成器应用源码
- `template-src/`：内置模板 APK 的源码来源
- `scripts/build_template_asset.sh`：重建 `app/src/main/assets/template-base-unsigned.apk`

### 生成器能力

- 可输入**包名**
- 可输入**应用名称**
- 生成新的极小 APK
- 生成完成后会**自动保存到本地**
- 可一键**分享生成出来的 APK**
- **不会复用仓库现有签名 / debug.keystore / 之前的模板签名**

### 签名安全口径

- 生成器首次运行时，会在应用私有目录里生成一套**新的本地 ECDSA 密钥**
- 该密钥保存在 `no_backup` 私有目录，不随系统备份导出
- 生成器界面提供“**重建签名密钥**”按钮，可随时轮换
- 生成出来的 APK 都用这套**独立本地密钥**做签名，而不是复用现成签名

### 构建生成器

```bash
./gradlew assembleDebug
```

产物：

- `app/build/outputs/apk/debug/app-debug.apk`

### 当前保存 / 分享行为

- Android 10+：优先保存到系统下载目录 `Download/BlankApkGenerator/`
- 更低版本：回退到应用自己的下载目录
- 分享走 `content://` URI（`MediaStore` 或 `FileProvider`），避免直接暴露裸文件路径

## 下载 / Release

当前 GitHub Release 提供的是生成器应用的 **正式签名 release 版 APK**：

- 下载入口：<https://github.com/wangcccccct/blankapk/releases>
- APK 文件名：`blankapk-generator-v1.1-release.apk`
- 对应应用版本：`versionName 1.1` / `versionCode 2`

说明：

- 这个 APK 是 Android 生成器应用，不是生成出来的空白 APK 模板本身。
- release 版已使用正式版签名配置打包，适合替代原 debug 包分发。
