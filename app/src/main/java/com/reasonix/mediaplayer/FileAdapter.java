package com.reasonix.mediaplayer;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 媒体文件列表适配器（支持 header + item + footer）
 */
public class FileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final int TYPE_FOOTER = 2;

    private final List<Object> items = new ArrayList<>();
    private String versionName = "";
    private boolean showFooter = false;

    public interface OnItemClickListener {
        void onItemClick(MediaFileItem item, int position);
    }

    public interface OnItemMoreClickListener {
        void onItemMoreClick(MediaFileItem item, int position, View anchor);
    }

    private OnItemClickListener clickListener;
    private OnItemMoreClickListener moreClickListener;

    public void setItems(List<Object> mixedItems) {
        items.clear();
        items.addAll(mixedItems);
        notifyDataSetChanged();
    }

    public void setVersionName(String version) {
        this.versionName = version;
    }

    public void setShowFooter(boolean show) {
        this.showFooter = show;
        notifyDataSetChanged();
    }

    public boolean isShowFooter() {
        return showFooter;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemMoreClickListener(OnItemMoreClickListener listener) {
        this.moreClickListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (showFooter && position == items.size()) return TYPE_FOOTER;
        Object obj = items.get(position);
        if (obj instanceof SectionHeader) return TYPE_HEADER;
        return TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return showFooter ? items.size() + 1 : items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_section_header, parent, false));
        } else if (viewType == TYPE_FOOTER) {
            return new FooterViewHolder(inflater.inflate(R.layout.item_footer, parent, false));
        } else {
            return new ItemViewHolder(inflater.inflate(R.layout.item_media, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            SectionHeader header = (SectionHeader) items.get(position);
            ((HeaderViewHolder) holder).txtHeader.setText(header.title);

        } else if (holder instanceof FooterViewHolder) {
            FooterViewHolder fh = (FooterViewHolder) holder;
            fh.txtAuthor.setText("作者：曾先生@w2018");
            fh.txtUrl.setText("https://github.com/w2018/MediaPlayer");
            fh.txtVersion.setText("v" + versionName);

        } else if (holder instanceof ItemViewHolder) {
            MediaFileItem item = (MediaFileItem) items.get(position);
            ItemViewHolder vh = (ItemViewHolder) holder;

            vh.txtTitle.setText(item.getTitle());
            vh.txtDuration.setText(item.getFormattedDuration());
            vh.txtSize.setText(item.getFormattedSize());
            vh.txtDate.setText(item.getFormattedDate());

            if (item.isVideo()) {
                vh.txtTypeBadge.setVisibility(View.GONE);
                vh.imgThumbnail.setImageResource(android.R.drawable.ic_media_play);
                VideoThumbnailLoader.loadThumbnail(
                        vh.itemView.getContext(),
                        item.getId(),
                        new VideoThumbnailLoader.OnThumbnailLoadedListener() {
                            @Override
                            public void onThumbnailLoaded(Bitmap bitmap) {
                                vh.imgThumbnail.setImageBitmap(bitmap);
                            }
                            @Override
                            public void onThumbnailFailed() {}
                        }
                );
            } else {
                vh.txtTypeBadge.setVisibility(View.VISIBLE);
                vh.txtTypeBadge.setText("🎵");
                vh.imgThumbnail.setImageBitmap(null);
                vh.imgThumbnail.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            }

            vh.itemView.setOnClickListener(v -> {
                if (clickListener != null)
                    clickListener.onItemClick(item, vh.getAdapterPosition());
            });

            vh.btnMore.setOnClickListener(v -> {
                if (moreClickListener != null)
                    moreClickListener.onItemMoreClick(item, vh.getAdapterPosition(), v);
            });
        }
    }

    // ==================== ViewHolder ====================

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView txtHeader;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtHeader = itemView.findViewById(R.id.txtSectionHeader);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView txtTypeBadge, txtTitle, txtDuration, txtSize, txtDate;
        ImageButton btnMore;
        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            txtTypeBadge = itemView.findViewById(R.id.txtTypeBadge);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtDuration = itemView.findViewById(R.id.txtDuration);
            txtSize = itemView.findViewById(R.id.txtSize);
            txtDate = itemView.findViewById(R.id.txtDate);
            btnMore = itemView.findViewById(R.id.btnMore);
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        TextView txtAuthor, txtUrl, txtVersion;
        FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            txtAuthor = itemView.findViewById(R.id.txtFooterAuthor);
            txtUrl = itemView.findViewById(R.id.txtFooterUrl);
            txtVersion = itemView.findViewById(R.id.txtFooterVersion);
        }
    }

    // ==================== 数据模型 ====================

    public static class SectionHeader {
        public final String title;
        public SectionHeader(String title) {
            this.title = title;
        }
    }
}
