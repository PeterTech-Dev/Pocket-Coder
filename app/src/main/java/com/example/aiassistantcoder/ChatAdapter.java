package com.example.aiassistantcoder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log; // <-- debugger
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.image.ImagesPlugin;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private static final String TAG = "ChatAdapter"; // <-- debugger

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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(messages.get(position), markwon);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ----------------------------------------
    // ViewHolder
    // ----------------------------------------
    static class ChatViewHolder extends RecyclerView.ViewHolder {

        // Common bubble
        private final LinearLayout messageRoot;
        private final RelativeLayout bubbleLayout;
        private final TextView messageText;
        private final ImageView messageImage;

        // Legacy copy buttons for non-JSON text (optional)
        private final ImageButton copyButton;
        private final ImageButton copyCodeButton;

        // Parsed JSON card (pretty UI)
        private final View parsedContainer;
        private final TextView badgeLanguage, badgeRuntime, parsedNotes, parsedCode;
        private final ImageButton btnCopyCode, btnExpandCode;
        private final View codeFade;

        // Raw JSON card (pretty-printed)
        private final View jsonContainer;
        private final TextView jsonText;
        private final ImageButton btnCopyJson, btnExpandJson;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);

            messageRoot   = itemView.findViewById(R.id.message_root);
            bubbleLayout  = itemView.findViewById(R.id.bubble_layout);
            messageText   = itemView.findViewById(R.id.message_text);
            messageImage  = itemView.findViewById(R.id.message_image);

            copyButton     = itemView.findViewById(R.id.copy_button_bubble);
            copyCodeButton = itemView.findViewById(R.id.copy_code_button);

            parsedContainer = itemView.findViewById(R.id.parsed_container);
            badgeLanguage   = itemView.findViewById(R.id.badge_language);
            badgeRuntime    = itemView.findViewById(R.id.badge_runtime);
            parsedNotes     = itemView.findViewById(R.id.parsed_notes);
            parsedCode      = itemView.findViewById(R.id.parsed_code);
            btnCopyCode     = itemView.findViewById(R.id.btn_copy_code);
            btnExpandCode   = itemView.findViewById(R.id.btn_expand_code);
            codeFade        = itemView.findViewById(R.id.code_fade);

            jsonContainer = itemView.findViewById(R.id.json_container);
            jsonText      = itemView.findViewById(R.id.json_text);
            btnCopyJson   = itemView.findViewById(R.id.btn_copy_json);
            btnExpandJson = itemView.findViewById(R.id.btn_expand_json);
        }

        void bind(Message message, Markwon markwon) {
            final boolean isUser = "user".equals(message.getRole());
            final String text = message.getText() == null ? "" : message.getText();

            // debugger
            Log.d(TAG, "bind(): role=" + message.getRole()
                    + " textLen=" + text.length()
                    + " hasImage=" + (message.getImageUri() != null));

            // Align/color bubble
            messageRoot.setGravity(isUser ? Gravity.END : Gravity.START);
            bubbleLayout.setBackgroundResource(isUser
                    ? R.drawable.bg_chat_bubble_user
                    : R.drawable.bg_chat_bubble_ai);

            // Image (if any)
            if (message.getImageUri() != null) {
                messageImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(Uri.parse(message.getImageUri()))
                        .into(messageImage);
            } else {
                messageImage.setVisibility(View.GONE);
            }

            // Try extract JSON from model replies only
            String json = (!isUser) ? extractFirstJson(text) : null;

            // debugger
            if (!isUser) {
                Log.d(TAG, "bind(): extracted JSON=" + (json == null ? "null" : ("len=" + json.length())));
            }

            if (!isUser && !TextUtils.isEmpty(json)) {
                // First try to parse into a structured payload (Language/Runtime/Code/Notes)
                AiPayload payload = tryParsePayload(json);

                // debugger
                Log.d(TAG, "bind(): payload parsed=" + (payload != null));

                if (payload != null) {
                    showParsedCard(payload);
                } else {
                    // If it wasn't valid JSON (or unexpected shape), show raw pretty JSON
                    showRawJsonCard(json);
                }

            } else {
                // Default markdown / plain text path
                parsedContainer.setVisibility(View.GONE);
                jsonContainer.setVisibility(View.GONE);
                messageText.setVisibility(View.VISIBLE);

                if (isUser) {
                    // debugger
                    Log.d(TAG, "bind(): user message, plain text shown");
                    messageText.setText(text);
                    copyButton.setVisibility(View.GONE);
                    copyCodeButton.setVisibility(View.GONE);
                } else {
                    // debugger
                    Log.d(TAG, "bind(): model message, markdown path");
                    markwon.setMarkdown(messageText, text);

                    copyButton.setVisibility(View.VISIBLE);
                    copyButton.setOnClickListener(v ->
                            copyToClipboard(v.getContext(), text, "Message copied"));

                    String code = extractCode(text);
                    if (!code.isEmpty()) {
                        copyCodeButton.setVisibility(View.VISIBLE);
                        copyCodeButton.setOnClickListener(v ->
                                copyToClipboard(v.getContext(), code, "Code copied"));
                    } else {
                        copyCodeButton.setVisibility(View.GONE);
                    }
                }
            }
        }

        // ----------------------------------------
        // UI branches
        // ----------------------------------------
        private void showParsedCard(AiPayload p) {
            // debugger
            Log.d(TAG, "showParsedCard(): lang=" + p.language
                    + " runtime=" + p.runtime
                    + " codeLen=" + (p.code == null ? 0 : p.code.length())
                    + " filePath=" + p.filePath);

            parsedContainer.setVisibility(View.VISIBLE);
            jsonContainer.setVisibility(View.GONE);
            messageText.setVisibility(View.GONE);

            String lang = safe(p.language);
            String rt   = safe(p.runtime);
            String notes = safe(p.notes);
            if (notes.isEmpty()) notes = safe(p.runnerHint);

            // Badge text logic
            if (!rt.isEmpty() && !lang.toLowerCase().contains(rt.toLowerCase())) {
                badgeLanguage.setText("Language: " + lang);
                badgeRuntime.setText("Runtime: " + rt);
                badgeRuntime.setVisibility(View.VISIBLE);
            } else {
                badgeLanguage.setText("Language: " + (rt.isEmpty() ? lang : (lang + " " + rt)));
                badgeRuntime.setVisibility(View.GONE);
            }

            parsedNotes.setText(notes.isEmpty() ? "No notes provided." : notes);

            // Collapsible code
            String code = safe(p.code);
            parsedCode.setText(code);

            if (p.filePath != null && !p.filePath.isEmpty()) {
                String current = parsedNotes.getText().toString();
                parsedNotes.setText(current + "\n\nFile: " + p.filePath);
            }

            boolean tooLong = countLines(code) > 16;
            parsedCode.setMaxLines(tooLong ? 16 : Integer.MAX_VALUE);
            codeFade.setVisibility(tooLong ? View.VISIBLE : View.GONE);
            btnExpandCode.setImageResource(tooLong ? R.drawable.ic_expand_more_24 : R.drawable.ic_expand_less_24);
            btnExpandCode.setContentDescription(itemView.getContext()
                    .getString(tooLong ? R.string.expand : R.string.collapse));

            btnExpandCode.setOnClickListener(v -> {
                boolean expanded = parsedCode.getMaxLines() == Integer.MAX_VALUE;
                if (expanded) {
                    parsedCode.setMaxLines(16);
                    codeFade.setVisibility(View.VISIBLE);
                    btnExpandCode.setImageResource(R.drawable.ic_expand_more_24);
                    btnExpandCode.setContentDescription(itemView.getContext().getString(R.string.expand));
                } else {
                    parsedCode.setMaxLines(Integer.MAX_VALUE);
                    codeFade.setVisibility(View.GONE);
                    btnExpandCode.setImageResource(R.drawable.ic_expand_less_24);
                    btnExpandCode.setContentDescription(itemView.getContext().getString(R.string.collapse));
                }
            });

            btnCopyCode.setOnClickListener(v ->
                    copyToClipboard(v.getContext(), code, "Code copied"));
        }

        private void showRawJsonCard(String json) {
            // debugger
            Log.d(TAG, "showRawJsonCard(): rawJsonLen=" + (json == null ? 0 : json.length()));

            parsedContainer.setVisibility(View.GONE);
            jsonContainer.setVisibility(View.VISIBLE);
            messageText.setVisibility(View.GONE);

            String pretty = prettyJson(json);

            // debugger
            Log.d(TAG, "showRawJsonCard(): prettyJsonLen=" + pretty.length());

            jsonText.setText(pretty);
            jsonText.setMaxLines(14);

            btnCopyJson.setOnClickListener(v ->
                    copyToClipboard(v.getContext(), pretty, "JSON copied"));

            btnExpandJson.setOnClickListener(v -> {
                boolean expanded = jsonText.getMaxLines() == Integer.MAX_VALUE;
                jsonText.setMaxLines(expanded ? 14 : Integer.MAX_VALUE);
                btnExpandJson.setImageResource(expanded ? R.drawable.ic_expand_more_24 : R.drawable.ic_expand_less_24);
                btnExpandJson.setContentDescription(v.getContext()
                        .getString(expanded ? R.string.expand : R.string.collapse));
            });
        }

        private static void copyToClipboard(Context ctx, String s, String toast) {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("copied", s));
            Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show();
        }

        /** Find first fenced ```json ... ``` or a balanced {…}/[…] block. */
        /** Find first real JSON: either ```json ... ``` or text that actually starts with { or [. */
        private String extractFirstJson(String s) {
            if (s == null) return null;

            Log.d(TAG, "extractFirstJson(): inputLen=" + s.length());

            // 1) explicit fenced JSON wins
            int fenceStart = s.indexOf("```json");
            if (fenceStart == -1) fenceStart = s.indexOf("```JSON");
            if (fenceStart != -1) {
                int codeStart = s.indexOf('\n', fenceStart);
                int fenceEnd = s.indexOf("```", codeStart + 1);
                if (codeStart != -1 && fenceEnd != -1) {
                    String fenced = s.substring(codeStart + 1, fenceEnd).trim();
                    Log.d(TAG, "extractFirstJson(): found fenced JSON, len=" + fenced.length());
                    return fenced;
                }
            }

            // 2) if the WHOLE message actually starts with { or [, treat as json
            String trimmed = s.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                int end = findMatchingJsonEnd(trimmed, 0);
                if (end != -1) {
                    String result = trimmed.substring(0, end + 1).trim();
                    Log.d(TAG, "extractFirstJson(): found top-level JSON, len=" + result.length());
                    return result;
                }
            }

            // 3) otherwise, do NOT try to pull JSON from the middle of markdown/code
            Log.d(TAG, "extractFirstJson(): no real JSON detected, returning null");
            return null;
        }


        private int findMatchingJsonEnd(String text, int start) {
            int depth = 0; boolean inStr = false; char quote = 0; boolean esc = false;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inStr) {
                    if (esc) esc = false;
                    else if (c == '\\') esc = true;
                    else if (c == quote) inStr = false;
                    continue;
                }
                if (c == '"' || c == '\'') { inStr = true; quote = c; continue; }
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            return -1;
        }

        private String prettyJson(String raw) {
            // normalize curly quotes & trailing commas
            String t = raw.replace('“', '"').replace('”', '"').replace('’', '\'');
            t = t.replaceAll(",(\\s*[}\\]])", "$1");
            try {
                JsonElement el = JsonParser.parseString(t);
                Gson g = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                return g.toJson(el);
            } catch (Exception e) {
                // debugger
                Log.w(TAG, "prettyJson(): failed to parse, returning raw", e);
                return raw;
            }
        }

        private String extractCode(String text) {
            if (text == null) return "";
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("```(.*?\\n)?([\\s\\S]*?)```")
                    .matcher(text);
            return m.find() ? (m.group(2) == null ? "" : m.group(2)) : "";
        }

        private static int countLines(String s) {
            if (s == null || s.isEmpty()) return 0;
            int n = 1;
            for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
            return n;
        }

        private static String safe(String s) { return s == null ? "" : s; }

        private AiPayload tryParsePayload(String json) {
            try {
                String t = json.replace('“', '"').replace('”', '"').replace('’', '\'');
                t = t.replaceAll(",(\\s*[}\\]])", "$1");

                JsonElement el = JsonParser.parseString(t);
                if (!el.isJsonObject()) return null;

                JsonObject obj = el.getAsJsonObject();
                AiPayload p = new AiPayload();

                p.language = obj.has("language") ? obj.get("language").getAsString() : "";
                p.runtime  = obj.has("runtime")  ? obj.get("runtime").getAsString()  : "";
                p.notes    = obj.has("notes")    ? obj.get("notes").getAsString()    : "";

                if (obj.has("files") && obj.get("files").isJsonArray()
                        && obj.get("files").getAsJsonArray().size() > 0) {

                    JsonObject f0 = obj.get("files").getAsJsonArray()
                            .get(0).getAsJsonObject();

                    String path     = f0.has("path")     ? f0.get("path").getAsString()     : "";
                    String filename = f0.has("filename") ? f0.get("filename").getAsString() : "";
                    String content  = f0.has("content")  ? f0.get("content").getAsString()  : "";

                    p.code = content != null ? content : "";

                    if (path != null && !path.isEmpty() && !path.equals(".")) {
                        p.filePath = path + "/" + filename;
                    } else {
                        p.filePath = filename;
                    }
                } else {
                    p.code = obj.has("code") ? obj.get("code").getAsString() : "";
                }

                if ( (p.code == null || p.code.isEmpty())
                        && (p.language == null || p.language.isEmpty())
                        && (p.runtime == null || p.runtime.isEmpty()) ) {
                    return null;
                }

                return p;
            } catch (Exception ignored) {
                // debugger
                Log.w(TAG, "tryParsePayload(): failed to parse JSON payload");
                return null;
            }
        }

    }

    // DTO for parsed AI JSON
    static class AiPayload {
        String language;
        String runtime;
        String code;
        String notes;
        String filePath;
        @SerializedName("runnerHint") String runnerHint;
    }

}
