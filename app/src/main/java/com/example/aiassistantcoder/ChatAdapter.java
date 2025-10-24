package com.example.aiassistantcoder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        private final ImageView messageImage;
        private final ImageButton copyButton, copyCodeButton;
        private final LinearLayout messageRoot;
        private final RelativeLayout bubbleLayout;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            messageImage = itemView.findViewById(R.id.message_image);
            copyButton = itemView.findViewById(R.id.copy_button_bubble);
            copyCodeButton = itemView.findViewById(R.id.copy_code_button);
            messageRoot = itemView.findViewById(R.id.message_root);
            bubbleLayout = itemView.findViewById(R.id.bubble_layout);
        }

        public void bind(Message message, Markwon markwon) {
            if (message.getImageUri() != null) {
                messageImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(Uri.parse(message.getImageUri())).into(messageImage);
            } else {
                messageImage.setVisibility(View.GONE);
            }

            if (message.getRole().equals("user")) {
                messageText.setText(message.getText());
                messageRoot.setGravity(Gravity.END);
                bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_user);
                copyButton.setVisibility(View.GONE);
                copyCodeButton.setVisibility(View.GONE);
            } else {
                if (message.getText() != null) {
                    markwon.setMarkdown(messageText, message.getText());
                }
                messageRoot.setGravity(Gravity.START);
                bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_ai);

                copyButton.setVisibility(View.VISIBLE);
                copyButton.setOnClickListener(v -> {
                    ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Copied Text", message.getText());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(v.getContext(), "Full message copied", Toast.LENGTH_SHORT).show();
                });

                String code = extractCode(message.getText());
                if (!code.isEmpty()) {
                    copyCodeButton.setVisibility(View.VISIBLE);
                    copyCodeButton.setOnClickListener(v -> {
                        ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Copied Code", code);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(v.getContext(), "Code copied to clipboard", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    copyCodeButton.setVisibility(View.GONE);
                }
            }
        }

        private String extractCode(String text) {
            if (text == null) return "";
            Pattern pattern = Pattern.compile("```(.*?|\n)```", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return "";
        }
    }
}
