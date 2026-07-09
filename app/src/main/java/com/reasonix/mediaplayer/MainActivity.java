package com.reasonix.mediaplayer;

import android.Manifest;
import android.app.Activity;
import android.app.RecoverableSecurityException;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 主界面 - 媒体文件浏览（含筛选、删除、分享、重命名、日期分段）
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "media_player_prefs";
    private static final String KEY_FORMATS = "allowed_formats";
    private static final String KEY_MIN_DURATION = "min_duration_sec";
    private static final int REQUEST_CODE_DELETE = 1001;
    private static final int REQUEST_CODE_RENAME = 1002;

    private static final String[] PRESET_FORMATS = {
            "mp4", "mkv", "flv", "avi", "mov", "webm",
            "mp3", "aac", "wav", "ogg", "flac", "m4a"
    };

    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private TextView emptyText;
    private MaterialButton btnGrantPermission;
    private TabLayout tabLayout;

    private FileAdapter adapter;
    private MediaScanner mediaScanner;
    private int currentFilter = MediaScanner.FILTER_ALL;

    private Set<String> allowedFormats = new HashSet<>();
    private long minDurationSec = 0;
    private SharedPreferences prefs;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) { allGranted = false; break; }
                }
                if (allGranted) loadMedia();
                else showPermissionDenied();
            });

    // 暂存待删除/重命名的 URI
    private Uri pendingUri;
    private String pendingNewName;
    private int pendingRequestCode;

    private static final int REQUEST_DELETE_CONSENT = 9001;
    private static final int REQUEST_RENAME_CONSENT = 9002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadFilterPrefs();

        tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        emptyText = findViewById(R.id.emptyText);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);

        adapter = new FileAdapter();
        // 自动获取版本号
        try {
            String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            adapter.setVersionName(ver != null ? ver : "1.0.0");
        } catch (Exception e) {
            adapter.setVersionName("1.0.0");
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 滚动到底部时显示 footer
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null && lm.findLastCompletelyVisibleItemPosition() == lm.getItemCount() - 1) {
                    if (!adapter.isShowFooter()) {
                        adapter.setShowFooter(true);
                    }
                }
            }
        });

        adapter.setOnItemClickListener((item, position) -> {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("media_uri", item.getUri().toString());
            intent.putExtra("media_title", item.getTitle());
            intent.putExtra("media_type", item.getType());
            startActivity(intent);
        });

        adapter.setOnItemMoreClickListener((item, position, anchor) -> showItemContextMenu(item, anchor));

        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_all));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_video));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_audio));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentFilter = tab.getPosition();
                loadMedia();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnGrantPermission.setOnClickListener(v -> requestPermissions());
        mediaScanner = new MediaScanner(this);

        if (hasStoragePermission()) loadMedia();
        else showPermissionNeeded();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStoragePermission()) loadMedia();
    }

    // ==================== 加载媒体 + 日期分组 ====================

    private void loadMedia() {
        if (!hasStoragePermission()) { showPermissionNeeded(); return; }

        emptyView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);

        mediaScanner.scan(currentFilter, allowedFormats, minDurationSec * 1000,
                new MediaScanner.OnMediaLoadedListener() {
                    @Override
                    public void onMediaLoaded(List<MediaFileItem> items) {
                        // 构建混合列表（header + item）
                        List<Object> mixed = buildGroupedList(items);
                        adapter.setItems(mixed);

                        // 更新文件计数
                        updateFileCount(items.size());

                        if (items.isEmpty()) {
                            recyclerView.setVisibility(View.GONE);
                            emptyView.setVisibility(View.VISIBLE);
                            if (!allowedFormats.isEmpty() || minDurationSec > 0) {
                                emptyText.setText("没有匹配筛选条件的文件");
                            } else {
                                emptyText.setText(R.string.empty_media);
                            }
                            btnGrantPermission.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        recyclerView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                        emptyText.setText("加载失败: " + message);
                        btnGrantPermission.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * 将 MediaFileItem 列表按日期分组，生成混合列表
     */
    private List<Object> buildGroupedList(List<MediaFileItem> items) {
        List<Object> result = new ArrayList<>();
        String lastGroup = null;

        for (MediaFileItem item : items) {
            String group = item.getDateGroup();
            if (!group.equals(lastGroup)) {
                result.add(new FileAdapter.SectionHeader(group));
                lastGroup = group;
            }
            result.add(item);
        }

        return result;
    }

    /**
     * 更新 Toolbar 副标题显示文件计数
     */
    private void updateFileCount(int count) {
        boolean hasFilter = !allowedFormats.isEmpty() || minDurationSec > 0;
        String subtitle;
        if (hasFilter) {
            subtitle = count + " 个文件（已筛选）";
        } else {
            subtitle = count + " 个文件";
        }
        toolbar.setSubtitle(subtitle);
    }

    // ==================== 筛选对话框 ====================

    private void showFilterDialog() {
        String[] items = {"文件格式筛选", "最小时长筛选"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("筛选设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showFormatFilterDialog();
                    else showDurationFilterDialog();
                })
                .show();
    }

    /**
     * 格式筛选对话框 — 使用 ScrollView + CheckBox 实现可滚动
     */
    private void showFormatFilterDialog() {
        // 合并预置格式 + 用户自定义格式
        LinkedHashSet<String> allFormatsSet = new LinkedHashSet<>(Arrays.asList(PRESET_FORMATS));
        allFormatsSet.addAll(allowedFormats);
        String[] allFormats = allFormatsSet.toArray(new String[0]);

        // 外层 ScrollView
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        int padding = dpToPx(20);
        scrollView.setPadding(padding, dpToPx(8), padding, 0);

        // 内层 LinearLayout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // 格式 CheckBox 列表
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String fmt : allFormats) {
            CheckBox cb = new CheckBox(this);
            cb.setText(fmt);
            cb.setTextSize(15);
            cb.setChecked(allowedFormats.contains(fmt));
            cb.setPadding(dpToPx(4), dpToPx(2), 0, dpToPx(2));
            layout.addView(cb);
            checkBoxes.add(cb);
        }

        // 分隔线
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(16)));
        layout.addView(divider);

        // 自定义格式输入
        TextView customLabel = new TextView(this);
        customLabel.setText("自定义格式（如 webm）");
        customLabel.setTextSize(13);
        customLabel.setAlpha(0.6f);
        layout.addView(customLabel);

        EditText customInput = new EditText(this);
        customInput.setHint("输入格式名，如 webm");
        customInput.setTextSize(14);
        customInput.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dpToPx(4);
        layout.addView(customInput, inputParams);

        scrollView.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_format_title)
                .setView(scrollView)
                .setPositiveButton("确定", (dialog, which) -> {
                    Set<String> selected = new HashSet<>();
                    for (int i = 0; i < checkBoxes.size(); i++) {
                        if (checkBoxes.get(i).isChecked()) {
                            selected.add(allFormats[i]);
                        }
                    }
                    // 处理自定义输入
                    String custom = customInput.getText().toString().trim();
                    if (!custom.isEmpty()) {
                        if (custom.startsWith(".")) custom = custom.substring(1);
                        if (!custom.isEmpty()) selected.add(custom.toLowerCase());
                    }
                    allowedFormats = selected;
                    saveFilterPrefs();
                    loadMedia();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("清除全部", (dialog, which) -> {
                    allowedFormats.clear();
                    saveFilterPrefs();
                    loadMedia();
                })
                .show();
    }

    private void showDurationFilterDialog() {
        int currentProgress = (int) minDurationSec;
        int maxSeconds = 300;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(8));

        TextView label = new TextView(this);
        label.setTextSize(14);
        label.setText(currentProgress == 0 ? getString(R.string.filter_duration_none)
                : String.format("最小时长：%d 秒", currentProgress));

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(maxSeconds);
        seekBar.setProgress(currentProgress);
        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbParams.topMargin = dpToPx(8);
        seekBar.setLayoutParams(sbParams);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                label.setText(progress == 0 ? getString(R.string.filter_duration_none)
                        : String.format("最小时长：%d 秒", progress));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        container.addView(label);
        container.addView(seekBar);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_duration_title)
                .setView(container)
                .setPositiveButton("确定", (dialog, which) -> {
                    minDurationSec = seekBar.getProgress();
                    saveFilterPrefs();
                    loadMedia();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 列表项上下文菜单 ====================

    private void showItemContextMenu(MediaFileItem item, View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_item_context, popup.getMenu());
        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_properties) { showFileProperties(item); return true; }
            else if (id == R.id.action_rename) { showRenameDialog(item); return true; }
            else if (id == R.id.action_share) { shareFile(item); return true; }
            else if (id == R.id.action_delete) { showDeleteConfirm(item); return true; }
            return false;
        });
        popup.show();
    }

    // ==================== 文件属性 ====================

    private void showFileProperties(MediaFileItem item) {
        View view = getLayoutInflater().inflate(R.layout.dialog_file_properties, null);

        // 文件名
        ((TextView) view.findViewById(R.id.propsName)).setText(item.getTitle());

        // 类型
        setPropsRow(view, R.id.rowType, getString(R.string.props_type), item.getTypeLabel());

        // 格式
        String ext = item.getExtension();
        setPropsRow(view, R.id.rowFormat, getString(R.string.props_format),
                ext.isEmpty() ? "未知" : ext);

        // MIME
        setPropsRow(view, R.id.rowMime, getString(R.string.props_mime), item.getMimeType());

        // 大小
        setPropsRow(view, R.id.rowSize, getString(R.string.props_size), item.getFormattedSize());

        // 时长
        setPropsRow(view, R.id.rowDuration, getString(R.string.props_duration), item.getFormattedDuration());

        // 分辨率
        String res = item.getResolutionLabel();
        if (res != null) {
            setPropsRow(view, R.id.rowResolution, getString(R.string.props_resolution), res);
        } else {
            view.findViewById(R.id.rowResolution).setVisibility(View.GONE);
        }

        // 添加时间
        setPropsRow(view, R.id.rowDateAdded, getString(R.string.props_date_added), item.getFormattedDate());

        // 完整路径
        setPropsRow(view, R.id.rowPath, getString(R.string.props_path), item.getPath());

        // MediaStore ID
        setPropsRow(view, R.id.rowId, getString(R.string.props_id), String.valueOf(item.getId()));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.props_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    private void setPropsRow(View root, int rowId, String label, String value) {
        View row = root.findViewById(rowId);
        ((TextView) row.findViewById(R.id.propsLabel)).setText(label);
        ((TextView) row.findViewById(R.id.propsValue)).setText(value);
    }

    // ==================== 删除 ====================

    private void showDeleteConfirm(MediaFileItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(String.format(getString(R.string.delete_warning), item.getTitle()))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.delete_confirm, (d, w) -> deleteFile(item))
                .setNegativeButton(R.string.delete_cancel, null)
                .show();
    }

    private void deleteFile(MediaFileItem item) {
        android.util.Log.d("MediaPlayer", "deleteFile: uri=" + item.getUri());
        try {
            int deleted = getContentResolver().delete(item.getUri(), null, null);
            android.util.Log.d("MediaPlayer", "deleteFile: result=" + deleted);
            if (deleted > 0) {
                Toast.makeText(this, R.string.delete_success, Toast.LENGTH_SHORT).show();
                loadMedia();
            } else {
                Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (RecoverableSecurityException e) {
            android.util.Log.d("MediaPlayer", "deleteFile: RecoverableSecurityException");
            requestUserConsentForAction(item.getUri(), REQUEST_DELETE_CONSENT, null,
                    e.getUserAction().getActionIntent().getIntentSender());
        } catch (Exception e) {
            android.util.Log.e("MediaPlayer", "deleteFile error", e);
            Toast.makeText(this, getString(R.string.delete_failed) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestUserConsentForAction(Uri uri, int requestCode, String newName, IntentSender intentSender) {
        try {
            pendingUri = uri;
            pendingRequestCode = requestCode;
            pendingNewName = newName;
            // 使用 deprecated 但仍可工作的 startIntentSenderForResult
            startIntentSenderForResult(
                    intentSender,
                    requestCode, null, 0, 0, 0);
        } catch (Exception e) {
            android.util.Log.e("MediaPlayer", "requestUserConsent failed", e);
            Toast.makeText(this, "无法请求权限: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && pendingUri != null) {
            try {
                if (requestCode == REQUEST_DELETE_CONSENT) {
                    int deleted = getContentResolver().delete(pendingUri, null, null);
                    if (deleted > 0) {
                        Toast.makeText(this, R.string.delete_success, Toast.LENGTH_SHORT).show();
                        loadMedia();
                    } else {
                        Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show();
                    }
                } else if (requestCode == REQUEST_RENAME_CONSENT && pendingNewName != null) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, pendingNewName);
                    int updated = getContentResolver().update(pendingUri, values, null, null);
                    if (updated > 0) {
                        Toast.makeText(this, R.string.rename_success, Toast.LENGTH_SHORT).show();
                        loadMedia();
                    } else {
                        Toast.makeText(this, R.string.rename_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("MediaPlayer", "onActivityResult error", e);
                Toast.makeText(this, "操作失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            pendingUri = null;
            pendingNewName = null;
        }
    }

    // ==================== 分享 ====================

    private void shareFile(MediaFileItem item) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(item.getMimeType());
        shareIntent.putExtra(Intent.EXTRA_STREAM, item.getUri());
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_title)));
    }

    // ==================== 重命名 ====================

    private void showRenameDialog(MediaFileItem item) {
        EditText input = new EditText(this);
        input.setText(item.getTitle());
        input.setSelectAllOnFocus(true);
        input.setHint(R.string.rename_hint);
        input.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(8));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rename_title)
                .setView(input)
                .setPositiveButton(R.string.rename_confirm, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(item.getTitle())) {
                        renameFile(item, newName);
                    } else if (newName.equals(item.getTitle())) {
                        Toast.makeText(this, R.string.rename_same_name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.delete_cancel, null)
                .show();
    }

    private void renameFile(MediaFileItem item, String newName) {
        try {
            // 保留原文件扩展名
            String oldName = item.getTitle();
            int lastDot = oldName.lastIndexOf('.');
            if (lastDot > 0) {
                String ext = oldName.substring(lastDot);
                if (!newName.endsWith(ext)) {
                    newName = newName + ext;
                }
            }

            android.util.Log.d("MediaPlayer", "renameFile: uri=" + item.getUri() + ", newName=" + newName);

            android.content.ContentValues values = new android.content.ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, newName);

            int updated = getContentResolver().update(item.getUri(), values, null, null);
            android.util.Log.d("MediaPlayer", "renameFile: result=" + updated);

            if (updated > 0) {
                Toast.makeText(this, R.string.rename_success, Toast.LENGTH_SHORT).show();
                loadMedia();
            } else {
                Toast.makeText(this, R.string.rename_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (RecoverableSecurityException e) {
            android.util.Log.d("MediaPlayer", "renameFile: RecoverableSecurityException");
            requestUserConsentForAction(item.getUri(), REQUEST_RENAME_CONSENT, newName,
                    e.getUserAction().getActionIntent().getIntentSender());
        } catch (Exception e) {
            android.util.Log.e("MediaPlayer", "renameFile error", e);
            Toast.makeText(this, getString(R.string.rename_failed) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ==================== 权限 ====================

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE });
        }
    }

    private void showPermissionNeeded() {
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.no_permission);
        btnGrantPermission.setVisibility(View.VISIBLE);
    }

    private void showPermissionDenied() {
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.no_permission);
        btnGrantPermission.setVisibility(View.VISIBLE);
    }

    // ==================== 偏好持久化 ====================

    private void loadFilterPrefs() {
        String formatsStr = prefs.getString(KEY_FORMATS, "");
        minDurationSec = prefs.getLong(KEY_MIN_DURATION, 0);
        allowedFormats.clear();
        if (!formatsStr.isEmpty()) {
            for (String s : formatsStr.split(",")) {
                if (!s.trim().isEmpty()) allowedFormats.add(s.trim());
            }
        }
    }

    private void saveFilterPrefs() {
        StringBuilder sb = new StringBuilder();
        for (String f : allowedFormats) {
            if (sb.length() > 0) sb.append(",");
            sb.append(f);
        }
        prefs.edit()
                .putString(KEY_FORMATS, sb.toString())
                .putLong(KEY_MIN_DURATION, minDurationSec)
                .apply();
    }

    // ==================== 工具 ====================

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaScanner != null) mediaScanner.shutdown();
    }
}
