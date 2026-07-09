package com.reasonix.mediaplayer;

import android.content.Intent;

import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * 前台播放服务 - 支持后台音频播放和通知栏控制
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        Player player = new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
        mediaSession = new MediaSession.Builder(this, player).build();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        MediaSession session = mediaSession;
        if (session == null || session.getPlayer() == null
                || !session.getPlayer().getPlayWhenReady()) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        MediaSession session = mediaSession;
        if (session != null) {
            session.release();
        }
        mediaSession = null;
        super.onDestroy();
    }
}
