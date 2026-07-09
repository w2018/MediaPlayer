# 媒体播放器 (MediaPlayer)

轻量级 Android 媒体播放器，支持视频和音频文件的浏览与播放。

## ✨ 功能特性

### 🎬 媒体播放
- **视频播放**：基于 Media3 (ExoPlayer) 引擎，支持 MP4、MKV、FLV、AVI、MOV、WebM 等主流格式
- **音频播放**：支持 MP3、AAC、WAV、OGG、FLAC、M4A 等音频格式
- **全屏切换**：视频播放支持横竖屏切换和全屏模式
- **后台播放**：通过 Foreground Service 支持后台音频播放
- **Intent 调用**：其他应用可通过 `ACTION_VIEW` / `ACTION_SEND` 调用本应用播放媒体

### 📂 文件浏览
- **分页浏览**：按「全部 / 视频 / 音频」标签页分类展示
- **日期分组**：自动按日期分组（今天 / 昨天 / 本周 / 年月）
- **视频缩略图**：异步加载视频首帧作为预览缩略图
- **文件计数**：工具栏实时显示文件数量
- **底部信息**：滚动到底部显示作者、版本、开源地址

### 🔍 智能筛选
- **格式筛选**：支持预设格式勾选 + 自定义格式输入
- **时长筛选**：滑动设置最小时长阈值（0-300 秒）
- **筛选持久化**：筛选条件自动保存，下次打开恢复

### 🛠️ 文件管理
- **文件属性**：查看文件类型、格式、MIME、大小、时长、分辨率、路径等
- **重命名**：保留扩展名的智能重命名
- **删除**：二次确认 + 系统授权的安全删除
- **分享**：通过系统分享面板发送文件

## 📸 界面预览

<!-- TODO: 添加截图 -->
<!-- ![主界面](screenshots/main.png) -->
<!-- ![播放界面](screenshots/player.png) -->

## 🏗️ 技术架构

| 类别 | 技术选型 |
|------|----------|
| **语言** | Java |
| **最低版本** | Android 8.0 (API 26) |
| **目标版本** | Android 14 (API 34) |
| **UI 框架** | Material Design 3 + ViewBinding |
| **媒体引擎** | Media3 (ExoPlayer) 1.3.1 |
| **数据源** | MediaStore API (Scoped Storage) |
| **异步处理** | AndroidX Lifecycle + 后台线程池 |
| **构建工具** | Gradle 8.11.1 + AGP 8.7.3 |

### 项目结构

```
MediaPlayer/
├── app/src/main/java/com/reasonix/mediaplayer/
│   ├── MainActivity.java        # 主界面（文件浏览 + 筛选 + 管理）
│   ├── PlayerActivity.java      # 播放界面（视频/音频 + 全屏）
│   ├── MediaFileItem.java       # 媒体文件数据模型
│   ├── MediaScanner.java        # MediaStore 异步扫描器
│   ├── FileAdapter.java         # RecyclerView 适配器（三 ViewType）
│   ├── VideoThumbnailLoader.java # 视频缩略图异步加载器
│   ├── PlaybackService.java     # 后台播放服务
│   └── MediaPlayerApp.java      # Application（通知渠道初始化）
├── app/src/main/res/
│   ├── layout/                  # 8 个布局文件
│   ├── menu/                    # 工具栏 + 上下文菜单
│   ├── drawable/                # 矢量图标 + 背景
│   ├── values/                  # 颜色、字符串、主题
│   └── values-night/            # 暗色主题
└── .github/workflows/           # CI/CD 自动构建
```

## 🚀 下载安装

### 方式一：GitHub Releases（推荐）
前往 [Releases](https://github.com/w2018/MediaPlayer/releases) 页面下载最新 APK。

### 方式二：自行构建

```bash
# 克隆项目
git clone https://github.com/w2018/MediaPlayer.git
cd MediaPlayer

# 生成 Gradle Wrapper（如缺失）
gradle wrapper --gradle-version 8.11.1

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（需要配置签名）
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/`

## ⚙️ 权限说明

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_VIDEO` | 读取视频文件（Android 13+） |
| `READ_MEDIA_AUDIO` | 读取音频文件（Android 13+） |
| `READ_EXTERNAL_STORAGE` | 读取存储（Android 12 及以下） |
| `FOREGROUND_SERVICE` | 后台音频播放 |
| `POST_NOTIFICATIONS` | 播放通知显示 |

## 📋 系统调用

其他应用可通过 Intent 调用本播放器：

```java
// 播放视频
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.setDataAndType(uri, "video/*");
intent.setPackage("com.reasonix.mediaplayer");
startActivity(intent);

// 分享文件到本播放器
Intent shareIntent = new Intent(Intent.ACTION_SEND);
shareIntent.setType("audio/*");
shareIntent.putExtra(Intent.EXTRA_STREAM, audioUri);
shareIntent.setPackage("com.reasonix.mediaplayer");
startActivity(Intent.createChooser(shareIntent, "分享音频"));
```

## 🔄 CI/CD

推送 `v*` tag 时自动触发 GitHub Actions：
1. 编译 Release APK（R8 混淆 + 资源缩减）
2. zipalign 对齐 + apksigner 签名
3. 自动创建 GitHub Release 并上传签名 APK

```bash
# 发布新版本
git tag v1.0.0
git push origin v1.0.0
```

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

## 👤 作者

**曾先生@w2018**

- GitHub: [@w2018](https://github.com/w2018)

---

> 💡 本应用仅供系统调用，用于播放视频和音频文件。
