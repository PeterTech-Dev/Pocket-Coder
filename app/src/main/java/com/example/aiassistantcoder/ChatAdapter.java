package com.example.aiassistantcoder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.image.ImagesPlugin;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<Message> messages;
    private final Markwon markwon;

    public ChatAdapter(List<Message> messages, Context context) {
        this.messages = messages;
        this.markwon = Markwon.builder(context)
                .usePlugin(ImagesPlugin.create())
                .build();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.bind(message, markwon);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final ImageButton copyButton;
        private final LinearLayout messageRoot;
        private final RelativeLayout bubbleLayout;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            copyButton = itemView.findViewById(R.id.copy_button_bubble);
            messageRoot = itemView.findViewById(R.id.message_root);
            bubbleLayout = itemView.findViewById(R.id.bubble_layout);
        }

        public void bind(Message message, Markwon markwon) {
            if (message.getRole().equals("user")) {
                messageText.setText(message.getText());
                messageRoot.setGravity(Gravity.END);
                bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_user);
                copyButton.setVisibility(View.GONE);
            } else {
                markwon.setMarkdown(messageText, message.getText());
                messageRoot.setGravity(Gravity.START);
                bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_ai);
                copyButton.setVisibility(View.VISIBLE);
            }

            copyButton.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Copied Text", message.getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(v.getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
