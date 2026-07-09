package com.reasonix.mediaplayer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通过 MediaStore 查询设备上的视频和音频文件
 */
public class MediaScanner {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_VIDEO = 1;
    public static final int FILTER_AUDIO = 2;

    public interface OnMediaLoadedListener {
        void onMediaLoaded(List<MediaFileItem> items);
        void onError(String message);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MediaScanner(Context context) {
        this.context = context;
    }

    /**
     * 扫描媒体文件（带格式和时长过滤）
     *
     * @param filter       类型过滤：FILTER_ALL / FILTER_VIDEO / FILTER_AUDIO
     * @param formats      允许的格式扩展名集合，null 或空则不过滤格式
     * @param minDurationMs 最小时长（毫秒），0 则不过滤时长
     * @param listener     回调
     */
    public void scan(int filter, Set<String> formats, long minDurationMs, OnMediaLoadedListener listener) {
        executor.execute(() -> {
            try {
                List<MediaFileItem> items = queryMedia(filter, formats, minDurationMs);
                new android.os.Handler(context.getMainLooper()).post(() -> listener.onMediaLoaded(items));
            } catch (Exception e) {
                new android.os.Handler(context.getMainLooper()).post(() -> listener.onError(e.getMessage()));
            }
        });
    }

    /**
     * 扫描媒体文件（无过滤，兼容旧调用）
     */
    public void scan(int filter, OnMediaLoadedListener listener) {
        scan(filter, null, 0, listener);
    }

    private List<MediaFileItem> queryMedia(int filter, Set<String> formats, long minDurationMs) {
        List<MediaFileItem> items = new ArrayList<>();

        if (filter == FILTER_ALL || filter == FILTER_VIDEO) {
            items.addAll(queryMediaStore(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaFileItem.TYPE_VIDEO,
                    formats, minDurationMs
            ));
        }

        if (filter == FILTER_ALL || filter == FILTER_AUDIO) {
            items.addAll(queryMediaStore(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    MediaFileItem.TYPE_AUDIO,
                    formats, minDurationMs
            ));
        }

        return items;
    }

    private List<MediaFileItem> queryMediaStore(Uri contentUri, int mediaType,
                                                Set<String> formats, long minDurationMs) {
        List<MediaFileItem> items = new ArrayList<>();

        String[] projection;
        if (mediaType == MediaFileItem.TYPE_VIDEO) {
            projection = new String[]{
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.WIDTH,
                    MediaStore.Video.Media.HEIGHT
            };
        } else {
            projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.DATE_ADDED
            };
        }

        String selection = null;
        if (mediaType == MediaFileItem.TYPE_AUDIO) {
            selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        }

        String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        try (Cursor cursor = context.getContentResolver().query(
                contentUri, projection, selection, null, sortOrder)) {

            if (cursor == null) return items;

            int idCol = cursor.getColumnIndexOrThrow(projection[0]);
            int nameCol = cursor.getColumnIndexOrThrow(projection[1]);
            int pathCol = cursor.getColumnIndexOrThrow(projection[2]);
            int durationCol = cursor.getColumnIndexOrThrow(projection[3]);
            int sizeCol = cursor.getColumnIndexOrThrow(projection[4]);
            int mimeCol = cursor.getColumnIndexOrThrow(projection[5]);
            int dateCol = cursor.getColumnIndexOrThrow(projection[6]);
            // width/height 用 getColumnIndex（音频可能没有这些列）
            int widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH);
            int heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                String path = cursor.getString(pathCol);
                long duration = cursor.getLong(durationCol);
                long size = cursor.getLong(sizeCol);
                String mime = cursor.getString(mimeCol);
                long dateAdded = cursor.getLong(dateCol);
                int width = widthCol >= 0 ? cursor.getInt(widthCol) : 0;
                int height = heightCol >= 0 ? cursor.getInt(heightCol) : 0;

                Uri uri = ContentUris.withAppendedId(contentUri, id);

                // 基础过滤：时长 > 1秒
                if (duration <= 1000) continue;

                // 时长过滤
                if (minDurationMs > 0 && duration < minDurationMs) continue;

                // 格式过滤
                if (formats != null && !formats.isEmpty()) {
                    String ext = getExtension(name);
                    if (!formats.contains(ext.toLowerCase())) continue;
                }

                items.add(new MediaFileItem(id, name, path, uri, duration, size, mediaType, mime, dateAdded, width, height));
            }
        }

        return items;
    }

    /**
     * 从文件名提取扩展名（不含点号）
     */
    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return "";
        return fileName.substring(dot + 1);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
