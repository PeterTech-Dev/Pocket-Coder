package com.example.aiassistantcoder;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.VH> {

    public interface OnFileClick {
        void onFileClick(OpenFile file);
    }

    public interface OnFileDelete {
        void onFileDelete(OpenFile file);
    }

    private final List<OpenFile> files;
    private final OnFileClick clickListener;
    private final OnFileDelete deleteListener;

    public FilesAdapter(List<OpenFile> files,
                        OnFileClick clickListener,
                        OnFileDelete deleteListener) {
        this.files = files;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // row container
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        int padH = (int) (16 * parent.getResources().getDisplayMetrics().density);
        int padV = (int) (10 * parent.getResources().getDisplayMetrics().density);
        row.setPadding(padH, padV, padH, padV);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // file name
        TextView tv = new TextView(parent.getContext());
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        // trash button
        ImageButton trash = new ImageButton(parent.getContext());
        trash.setImageResource(android.R.drawable.ic_menu_delete);
        trash.setBackground(null);
        trash.setColorFilter(Color.WHITE);
        int sz = (int) (32 * parent.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams trashLp = new LinearLayout.LayoutParams(sz, sz);
        trash.setLayoutParams(trashLp);

        row.addView(tv);
        row.addView(trash);

        return new VH(row, tv, trash);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        OpenFile f = files.get(position);
        holder.name.setText(f.name);

        // open on click text/row
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onFileClick(f);
        });

        // delete on trash
        holder.trash.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onFileDelete(f);
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        ImageButton trash;

        VH(@NonNull View itemView, TextView name, ImageButton trash) {
            super(itemView);
            this.name = name;
            this.trash = trash;
        }
    }
}
