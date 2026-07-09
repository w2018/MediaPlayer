package com.reasonix.mediaplayer;

import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 媒体文件数据模型
 */
public class MediaFileItem {

    public static final int TYPE_VIDEO = 0;
    public static final int TYPE_AUDIO = 1;

    private final long id;
    private final String title;
    private final String path;
    private final Uri uri;
    private final long duration;   // 毫秒
    private final long size;       // 字节
    private final int type;        // TYPE_VIDEO or TYPE_AUDIO
    private final String mimeType;
    private final long dateAdded;  // 秒
    private final int width;       // 像素（视频），0 表示未知
    private final int height;      // 像素（视频），0 表示未知

    public MediaFileItem(long id, String title, String path, Uri uri,
                     long duration, long size, int type, String mimeType, long dateAdded,
                     int width, int height) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.uri = uri;
        this.duration = duration;
        this.size = size;
        this.type = type;
        this.mimeType = mimeType;
        this.dateAdded = dateAdded;
        this.width = width;
        this.height = height;
    }

    /** 兼容旧构造（无分辨率） */
    public MediaFileItem(long id, String title, String path, Uri uri,
                     long duration, long size, int type, String mimeType, long dateAdded) {
        this(id, title, path, uri, duration, size, type, mimeType, dateAdded, 0, 0);
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getPath() { return path; }
    public Uri getUri() { return uri; }
    public long getDuration() { return duration; }
    public long getSize() { return size; }
    public int getType() { return type; }
    public String getMimeType() { return mimeType; }
    public long getDateAdded() { return dateAdded; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public boolean isVideo() { return type == TYPE_VIDEO; }
    public boolean isAudio() { return type == TYPE_AUDIO; }

    /** 获取文件扩展名（不含点号） */
    public String getExtension() {
        if (title == null) return "";
        int dot = title.lastIndexOf('.');
        return dot < 0 ? "" : title.substring(dot + 1).toUpperCase(Locale.getDefault());
    }

    /** 获取类型标签（如 "视频" / "音频"） */
    public String getTypeLabel() {
        return isVideo() ? "视频" : "音频";
    }

    /** 获取分辨率字符串（如 "1920×1080"），无数据返回 null */
    public String getResolutionLabel() {
        if (width > 0 && height > 0) return width + " × " + height;
        return null;
    }

    public String getFormattedDuration() {
        return formatDuration(duration);
    }

    public String getFormattedSize() {
        return formatSize(size);
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "00:00";
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    /**
     * 格式化日期为可读格式（如 "2026-07-09 14:30"）
     */
    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(dateAdded * 1000));
    }

    /**
     * 获取日期分组标签（用于分段显示）
     * 返回: "今天" / "昨天" / "本周" / "2026年6月" / "2025年12月" 等
     */
    public String getDateGroup() {
        long now = System.currentTimeMillis();
        long itemTime = dateAdded * 1000;
        long diffMs = now - itemTime;
        long diffDays = TimeUnit.MILLISECONDS.toDays(diffMs);

        if (diffDays == 0) return "今天";
        if (diffDays == 1) return "昨天";
        if (diffDays < 7) return "本周";

        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy年M月", Locale.getDefault());
        return monthFmt.format(new Date(itemTime));
    }
}
