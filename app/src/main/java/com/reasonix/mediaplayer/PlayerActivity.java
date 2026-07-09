package com.reasonix.mediaplayer;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * 播放界面 - 支持视频和音频播放，视频支持全屏切换
 */
@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private LinearLayout audioOverlay;
    private TextView audioTitle;
    private SeekBar audioSeekBar;
    private TextView audioCurrentTime;
    private TextView audioTotalTime;
    private ImageButton btnPlayPause;
    private View btnRewind;
    private View btnForward;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ImageButton btnBack;
    private ImageButton btnFullscreen;

    private ExoPlayer player;
    private boolean isAudioMode = false;
    private boolean isFullScreen = false;
    private boolean isPlaying = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable seekBarUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                long position = player.getCurrentPosition();
                long duration = player.getDuration();
                audioSeekBar.setProgress((int) position);
                audioCurrentTime.setText(MediaFileItem.formatDuration(position));
                audioTotalTime.setText(MediaFileItem.formatDuration(duration));
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        initViews();
        initPlayer();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        audioOverlay = findViewById(R.id.audioOverlay);
        audioTitle = findViewById(R.id.audioTitle);
        audioSeekBar = findViewById(R.id.audioSeekBar);
        audioCurrentTime = findViewById(R.id.audioCurrentTime);
        audioTotalTime = findViewById(R.id.audioTotalTime);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnRewind = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnBack = findViewById(R.id.btnBack);
        btnFullscreen = findViewById(R.id.btnFullscreen);

        btnBack.setOnClickListener(v -> finish());

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> seekRelative(-10000));
        btnForward.setOnClickListener(v -> seekRelative(10000));

        btnPrev.setOnClickListener(v -> {
            if (player != null) {
                player.seekToPreviousMediaItem();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (player != null) {
                player.seekToNextMediaItem();
            }
        });

        // 全屏切换按钮
        btnFullscreen.setOnClickListener(v -> toggleFullScreen());

        audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    player.seekTo(progress);
                    audioCurrentTime.setText(MediaFileItem.formatDuration(progress));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    long duration = player.getDuration();
                    audioSeekBar.setMax((int) duration);
                    audioTotalTime.setText(MediaFileItem.formatDuration(duration));
                    handler.post(seekBarUpdateRunnable);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                isPlaying = playing;
                updatePlayPauseButton();
                if (playing) {
                    handler.post(seekBarUpdateRunnable);
                } else {
                    handler.removeCallbacks(seekBarUpdateRunnable);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                String msg;
                int type = error.errorCode;
                if (type == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                        || type == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    msg = "文件无法访问，请检查文件是否存在";
                } else {
                    msg = "播放出错: " + error.getMessage();
                }
                audioTitle.setText(msg);
                Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        Uri mediaUri = null;
        String title = "";
        int mediaType = MediaFileItem.TYPE_VIDEO;

        if (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_SEND.equals(intent.getAction())) {
            mediaUri = intent.getData();
            if (mediaUri == null && Intent.ACTION_SEND.equals(intent.getAction())) {
                mediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (mediaUri != null) {
                String mimeType = intent.getType();
                if (mimeType != null && mimeType.startsWith("audio")) {
                    mediaType = MediaFileItem.TYPE_AUDIO;
                }
            }
        }

        if (mediaUri == null && intent.hasExtra("media_uri")) {
            mediaUri = Uri.parse(intent.getStringExtra("media_uri"));
            title = intent.getStringExtra("media_title");
            mediaType = intent.getIntExtra("media_type", MediaFileItem.TYPE_VIDEO);
        }

        if (mediaUri == null) {
            finish();
            return;
        }

        isAudioMode = (mediaType == MediaFileItem.TYPE_AUDIO);

        if (isAudioMode) {
            playerView.setVisibility(View.GONE);
            audioOverlay.setVisibility(View.VISIBLE);
            btnFullscreen.setVisibility(View.GONE);
            audioTitle.setText(title.isEmpty() ? mediaUri.getLastPathSegment() : title);
        } else {
            playerView.setVisibility(View.VISIBLE);
            audioOverlay.setVisibility(View.GONE);
            btnFullscreen.setVisibility(View.VISIBLE);
        }

        androidx.media3.common.MediaItem exoMediaItem = androidx.media3.common.MediaItem.fromUri(mediaUri);
        player.setMediaItem(exoMediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    // ==================== 全屏切换 ====================

    private void toggleFullScreen() {
        if (isAudioMode) return;

        isFullScreen = !isFullScreen;

        if (isFullScreen) {
            // 进入全屏：横屏 + 沉浸模式
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            hideSystemUI();
            btnFullscreen.setImageResource(android.R.drawable.ic_menu_crop);
        } else {
            // 退出全屏：竖屏 + 恢复系统栏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            showSystemUI();
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen);
        }
    }

    // ==================== 工具方法 ====================

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void seekRelative(long millis) {
        if (player == null) return;
        long newPos = player.getCurrentPosition() + millis;
        newPos = Math.max(0, Math.min(newPos, player.getDuration()));
        player.seekTo(newPos);
    }

    private void updatePlayPauseButton() {
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            player.pause();
        }
        handler.removeCallbacks(seekBarUpdateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(seekBarUpdateRunnable);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
