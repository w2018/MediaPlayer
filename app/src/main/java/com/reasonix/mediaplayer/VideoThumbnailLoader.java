package com.reasonix.mediaplayer;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步加载视频缩略图
 */
public class VideoThumbnailLoader {

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public interface OnThumbnailLoadedListener {
        void onThumbnailLoaded(Bitmap bitmap);
        void onThumbnailFailed();
    }

    /**
     * 异步加载指定视频的缩略图
     */
    public static void loadThumbnail(Context context, long videoId, OnThumbnailLoadedListener listener) {
        executor.execute(() -> {
            try {
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId);
                Bitmap thumb = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29+ 使用 loadThumbnail
                    thumb = context.getContentResolver().loadThumbnail(uri, new Size(320, 320), null);
                } else {
                    // 低版本使用 MediaStore.Video.Thumbnails
                    thumb = MediaStore.Video.Thumbnails.getThumbnail(
                            context.getContentResolver(),
                            videoId,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null);
                }

                if (thumb != null && !thumb.isRecycled()) {
                    Bitmap finalThumb = thumb;
                    new android.os.Handler(context.getMainLooper()).post(() -> listener.onThumbnailLoaded(finalThumb));
                } else {
                    new android.os.Handler(context.getMainLooper()).post(listener::onThumbnailFailed);
                }
            } catch (Exception e) {
                new android.os.Handler(context.getMainLooper()).post(listener::onThumbnailFailed);
            }
        });
    }
}
