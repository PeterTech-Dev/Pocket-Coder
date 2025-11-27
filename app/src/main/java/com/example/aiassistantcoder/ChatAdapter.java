package com.example.aiassistantcoder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.image.ImagesPlugin;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private static final String TAG = "ChatAdapter";

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

    // --------------------------------------------------
    // ViewHolder
    // --------------------------------------------------
    static class ChatViewHolder extends RecyclerView.ViewHolder {

        // root + bubble
        private final LinearLayout messageRoot;
        private final RelativeLayout bubbleLayout;
        private final TextView messageText;
        private final ImageView messageImage;

        // copy buttons for plain / markdown
        private final ImageButton copyButton;
        private final ImageButton copyCodeButton;

        // RUN INFO views
        private final View runInfoContainer;
        private final TextView runInfoTitle;
        private final TextView pillLanguage;
        private final TextView pillRuntime;
        private final TextView pillEntrypoint;
        private final TextView filesLabel;
        private final LinearLayout filesList;

        // parsed JSON card
        private final View parsedContainer;
        private final TextView badgeLanguage, badgeRuntime, parsedNotes, parsedCode;
        private final ImageButton btnCopyCode, btnExpandCode;
        private final View codeFade;

        // raw JSON card
        private final View jsonContainer;
        private final TextView jsonText;
        private final ImageButton btnCopyJson, btnExpandJson;

        // meta-line matcher for pretty-for-chat output
        private static final Pattern META_LINE =
                Pattern.compile("^\\*\\*(Language|Runtime|Entrypoint|File):\\*\\*\\s*(.+)$",
                        Pattern.CASE_INSENSITIVE);

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);

            messageRoot = itemView.findViewById(R.id.message_root);
            bubbleLayout = itemView.findViewById(R.id.bubble_layout);
            messageText = itemView.findViewById(R.id.message_text);
            messageImage = itemView.findViewById(R.id.message_image);

            copyButton = itemView.findViewById(R.id.copy_button_bubble);
            copyCodeButton = itemView.findViewById(R.id.copy_code_button);

            // run info
            runInfoContainer = itemView.findViewById(R.id.run_info_container);
            runInfoTitle = itemView.findViewById(R.id.run_info_title);
            pillLanguage = itemView.findViewById(R.id.pill_language);
            pillRuntime = itemView.findViewById(R.id.pill_runtime);
            pillEntrypoint = itemView.findViewById(R.id.pill_entrypoint);
            filesLabel = itemView.findViewById(R.id.run_info_files_label);
            filesList = itemView.findViewById(R.id.run_info_files_list);

            // parsed JSON card
            parsedContainer = itemView.findViewById(R.id.parsed_container);
            badgeLanguage = itemView.findViewById(R.id.badge_language);
            badgeRuntime = itemView.findViewById(R.id.badge_runtime);
            parsedNotes = itemView.findViewById(R.id.parsed_notes);
            parsedCode = itemView.findViewById(R.id.parsed_code);
            btnCopyCode = itemView.findViewById(R.id.btn_copy_code);
            btnExpandCode = itemView.findViewById(R.id.btn_expand_code);
            codeFade = itemView.findViewById(R.id.code_fade);

            // raw JSON card
            jsonContainer = itemView.findViewById(R.id.json_container);
            jsonText = itemView.findViewById(R.id.json_text);
            btnCopyJson = itemView.findViewById(R.id.btn_copy_json);
            btnExpandJson = itemView.findViewById(R.id.btn_expand_json);
        }

        void bind(Message message, Markwon markwon) {
            final boolean isUser = "user".equals(message.getRole());
            final String text = message.getText() == null ? "" : message.getText();

            Log.d(TAG, "bind(): role=" + message.getRole()
                    + " textLen=" + text.length()
                    + " hasImage=" + (message.getImageUri() != null));

            // alignment + bubble bg
            messageRoot.setGravity(isUser ? Gravity.END : Gravity.START);
            bubbleLayout.setBackgroundResource(isUser
                    ? R.drawable.bg_chat_bubble_user
                    : R.drawable.bg_chat_bubble_ai);

            // image (if present)
            if (message.getImageUri() != null) {
                messageImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(Uri.parse(message.getImageUri()))
                        .into(messageImage);
            } else {
                messageImage.setVisibility(View.GONE);
            }

            // JSON extraction (only assistant messages)
            String json = (!isUser) ? extractFirstJson(text) : null;
            if (!isUser) {
                Log.d(TAG, "bind(): extracted JSON=" + (json == null ? "null" : ("len=" + json.length())));
            }

            if (!isUser && !TextUtils.isEmpty(json)) {
                // structured payload path
                hideRunInfo();
                AiPayload payload = tryParsePayload(json);
                Log.d(TAG, "bind(): payload parsed=" + (payload != null));

                if (payload != null) {
                    showParsedCard(payload);
                } else {
                    showRawJsonCard(json);
                }

            } else {
                // normal markdown / plain text path
                parsedContainer.setVisibility(View.GONE);
                jsonContainer.setVisibility(View.GONE);
                messageText.setVisibility(View.VISIBLE);

                if (isUser) {
                    hideRunInfo();
                    Log.d(TAG, "bind(): user message, plain text");
                    messageText.setText(text);
                    copyButton.setVisibility(View.GONE);
                    copyCodeButton.setVisibility(View.GONE);
                } else {
                    Log.d(TAG, "bind(): model message, markdown path");

                    // Extract run info from top of the message (if present)
                    RunInfo runInfo = parseRunInfo(text);
                    if (runInfo != null) {
                        showRunInfo(runInfo);
                    } else {
                        hideRunInfo();
                    }

                    String display = (runInfo != null && runInfo.body != null)
                            ? runInfo.body
                            : text;

                    markwon.setMarkdown(messageText, display);

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

        // --------------------------------------------------
        // RUN INFO helpers
        // --------------------------------------------------

        /**
         * Parsed metadata from pretty-for-chat text.
         */
        static class RunInfo {
            String language;
            String runtime;
            String entrypoint;
            List<String> files = new ArrayList<>();
            String body; // markdown without the meta lines
        }

        /**
         * Parse Language/Runtime/Entrypoint/File lines and return remaining body.
         */
        private RunInfo parseRunInfo(String input) {
            if (input == null || input.isEmpty()) return null;

            String[] lines = input.split("\\r?\\n");
            RunInfo info = new RunInfo();
            StringBuilder body = new StringBuilder();
            boolean sawMeta = false;

            for (String line : lines) {
                String trimmed = line.trim();
                Matcher m = META_LINE.matcher(trimmed);
                if (m.find()) {
                    sawMeta = true;
                    String label = m.group(1).toLowerCase();
                    String value = m.group(2).trim();

                    switch (label) {
                        case "language":
                            info.language = value;
                            break;
                        case "runtime":
                            info.runtime = value;
                            break;
                        case "entrypoint":
                            info.entrypoint = value;
                            break;
                        case "file":
                            info.files.add(value);
                            break;
                    }
                } else {
                    body.append(line).append('\n');
                }
            }

            if (!sawMeta) return null;

            info.body = body.toString().trim();
            return info;
        }

        private void showRunInfo(RunInfo info) {
            runInfoContainer.setVisibility(View.VISIBLE);
            runInfoTitle.setVisibility(View.VISIBLE);

            Context ctx = itemView.getContext();

            // LANGUAGE pill
            if (!TextUtils.isEmpty(info.language)) {
                pillLanguage.setVisibility(View.VISIBLE);
                pillLanguage.setText(info.language);
                stylePill(
                        pillLanguage,
                        R.color.pillBlueBg,     // soft blue bg
                        R.color.pillBlueText    // deep blue text
                );
            } else {
                pillLanguage.setVisibility(View.GONE);
            }

// RUNTIME pill
            if (!TextUtils.isEmpty(info.runtime)) {
                pillRuntime.setVisibility(View.VISIBLE);
                pillRuntime.setText(info.runtime);
                stylePill(
                        pillRuntime,
                        R.color.pillPurpleBg,   // soft purple bg
                        R.color.pillPurpleText  // strong purple text
                );
            } else {
                pillRuntime.setVisibility(View.GONE);
            }

// ENTRYPOINT pill
            if (!TextUtils.isEmpty(info.entrypoint)) {
                pillEntrypoint.setVisibility(View.VISIBLE);
                pillEntrypoint.setText(info.entrypoint);
                stylePill(
                        pillEntrypoint,
                        R.color.pillGreenBg,    // soft green bg
                        R.color.pillGreenText   // dark green text
                );
            } else {
                pillEntrypoint.setVisibility(View.GONE);
            }

            // FILES
            filesList.removeAllViews();

            if (info.files != null && !info.files.isEmpty()) {
                filesLabel.setVisibility(View.VISIBLE);

                FileNode root = buildFileTree(info.files);
                renderTree(root, "", true, itemView.getContext());

            } else {
                filesLabel.setVisibility(View.GONE);
            }
        }

        private void renderTree(FileNode node, String prefix, boolean last, Context ctx) {
            List<FileNode> children = new ArrayList<>(node.children.values());

            // Sort: folders first, then files alphabetically
            Collections.sort(children, (a, b) -> {
                if (a.isFile && !b.isFile) return 1;
                if (!a.isFile && b.isFile) return -1;
                return a.name.compareToIgnoreCase(b.name);
            });

            int count = children.size();
            for (int i = 0; i < count; i++) {
                FileNode child = children.get(i);
                boolean isLast = (i == count - 1);

                String branch = isLast ? "└── " : "├── ";
                String line = prefix + branch + child.name + (child.isFile ? "" : "/");

                TextView tv = new TextView(ctx);
                tv.setText(line);
                tv.setTextSize(11f);
                tv.setTypeface(
                        Typeface.MONOSPACE,
                        child.isFile ? Typeface.NORMAL : Typeface.BOLD
                );

                // Folder color highlight
                tv.setTextColor(ctx.getColor(
                        child.isFile ? R.color.colorOnPrimary : R.color.colorOnPrimary
                ));

                filesList.addView(tv);

                // Recursion
                if (!child.isFile) {
                    String newPrefix = prefix + (isLast ? "    " : "│   ");
                    renderTree(child, newPrefix, isLast, ctx);
                }
            }
        }

        private FileNode buildFileTree(List<String> files) {
            FileNode root = new FileNode("", false);

            for (String path : files) {
                if (path == null || path.trim().isEmpty()) continue;

                String[] parts = path.split("/");
                FileNode current = root;

                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    boolean isFile = (i == parts.length - 1);

                    // Create child if missing
                    current.children.putIfAbsent(part,
                            new FileNode(part, isFile));

                    current = current.children.get(part);
                }
            }

            return root;
        }

        private void stylePill(TextView pill, int backgroundColorRes, int textColorRes) {
            Context ctx = itemView.getContext();
            pill.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ctx.getResources().getColor(backgroundColorRes)
                    )
            );
            pill.setTextColor(ctx.getResources().getColor(textColorRes));
        }


        private void hideRunInfo() {
            runInfoContainer.setVisibility(View.GONE);
            filesList.removeAllViews();
        }

        // --------------------------------------------------
        // UI branches for JSON payloads
        // --------------------------------------------------
        private void showParsedCard(AiPayload p) {
            Log.d(TAG, "showParsedCard(): lang=" + p.language
                    + " runtime=" + p.runtime
                    + " codeLen=" + (p.code == null ? 0 : p.code.length())
                    + " filePath=" + p.filePath);

            parsedContainer.setVisibility(View.VISIBLE);
            jsonContainer.setVisibility(View.GONE);
            messageText.setVisibility(View.GONE);
            hideRunInfo();

            String lang = safe(p.language);
            String rt = safe(p.runtime);
            String notes = safe(p.notes);
            if (notes.isEmpty()) notes = safe(p.runnerHint);

            if (!rt.isEmpty() && !lang.toLowerCase().contains(rt.toLowerCase())) {
                badgeLanguage.setText("Language: " + lang);
                badgeRuntime.setText("Runtime: " + rt);
                badgeRuntime.setVisibility(View.VISIBLE);
            } else {
                badgeLanguage.setText("Language: " + (rt.isEmpty() ? lang : (lang + " " + rt)));
                badgeRuntime.setVisibility(View.GONE);
            }

            parsedNotes.setText(notes.isEmpty() ? "No notes provided." : notes);

            String code = safe(p.code);
            parsedCode.setText(code);

            if (p.filePath != null && !p.filePath.isEmpty()) {
                String current = parsedNotes.getText().toString();
                parsedNotes.setText(current + "\n\nFile: " + normalizeFilePath("", p.filePath));
            }

            boolean tooLong = countLines(code) > 16;
            parsedCode.setMaxLines(tooLong ? 16 : Integer.MAX_VALUE);
            codeFade.setVisibility(tooLong ? View.VISIBLE : View.GONE);
            btnExpandCode.setImageResource(
                    tooLong ? R.drawable.ic_expand_more_24 : R.drawable.ic_expand_less_24);
            btnExpandCode.setContentDescription(itemView.getContext()
                    .getString(tooLong ? R.string.expand : R.string.collapse));

            btnExpandCode.setOnClickListener(v -> {
                boolean expanded = parsedCode.getMaxLines() == Integer.MAX_VALUE;
                if (expanded) {
                    parsedCode.setMaxLines(16);
                    codeFade.setVisibility(View.VISIBLE);
                    btnExpandCode.setImageResource(R.drawable.ic_expand_more_24);
                    btnExpandCode.setContentDescription(itemView.getContext()
                            .getString(R.string.expand));
                } else {
                    parsedCode.setMaxLines(Integer.MAX_VALUE);
                    codeFade.setVisibility(View.GONE);
                    btnExpandCode.setImageResource(R.drawable.ic_expand_less_24);
                    btnExpandCode.setContentDescription(itemView.getContext()
                            .getString(R.string.collapse));
                }
            });

            btnCopyCode.setOnClickListener(v ->
                    copyToClipboard(v.getContext(), code, "Code copied"));
        }

        private void showRawJsonCard(String json) {
            Log.d(TAG, "showRawJsonCard(): rawJsonLen=" + (json == null ? 0 : json.length()));

            parsedContainer.setVisibility(View.GONE);
            jsonContainer.setVisibility(View.VISIBLE);
            messageText.setVisibility(View.GONE);
            hideRunInfo();

            String pretty = prettyJson(json);
            Log.d(TAG, "showRawJsonCard(): prettyJsonLen=" + pretty.length());

            jsonText.setText(pretty);
            jsonText.setMaxLines(14);

            btnCopyJson.setOnClickListener(v ->
                    copyToClipboard(v.getContext(), pretty, "JSON copied"));

            btnExpandJson.setOnClickListener(v -> {
                boolean expanded = jsonText.getMaxLines() == Integer.MAX_VALUE;
                jsonText.setMaxLines(expanded ? 14 : Integer.MAX_VALUE);
                btnExpandJson.setImageResource(
                        expanded ? R.drawable.ic_expand_more_24 : R.drawable.ic_expand_less_24);
                btnExpandJson.setContentDescription(v.getContext()
                        .getString(expanded ? R.string.expand : R.string.collapse));
            });
        }

        private static void copyToClipboard(Context ctx, String s, String toast) {
            ClipboardManager cm =
                    (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("copied", s));
            Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show();
        }

        // --------------------------------------------------
        // JSON helpers
        // --------------------------------------------------
        private String extractFirstJson(String s) {
            if (s == null) return null;

            Log.d(TAG, "extractFirstJson(): inputLen=" + s.length());

            // fenced ```json
            int fenceStart = s.indexOf("```json");
            if (fenceStart == -1) fenceStart = s.indexOf("```JSON");
            if (fenceStart != -1) {
                int codeStart = s.indexOf('\n', fenceStart);
                int fenceEnd = s.indexOf("```", codeStart + 1);
                if (codeStart != -1 && fenceEnd != -1) {
                    String fenced = s.substring(codeStart + 1, fenceEnd).trim();
                    Log.d(TAG, "extractFirstJson(): fenced JSON len=" + fenced.length());
                    return fenced;
                }
            }

            // whole message is JSON
            String trimmed = s.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                int end = findMatchingJsonEnd(trimmed, 0);
                if (end != -1) {
                    String result = trimmed.substring(0, end + 1).trim();
                    Log.d(TAG, "extractFirstJson(): top-level JSON len=" + result.length());
                    return result;
                }
            }

            Log.d(TAG, "extractFirstJson(): no JSON detected");
            return null;
        }

        private int findMatchingJsonEnd(String text, int start) {
            int depth = 0;
            boolean inStr = false;
            char quote = 0;
            boolean esc = false;

            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inStr) {
                    if (esc) {
                        esc = false;
                    } else if (c == '\\') {
                        esc = true;
                    } else if (c == quote) {
                        inStr = false;
                    }
                    continue;
                }
                if (c == '"' || c == '\'') {
                    inStr = true;
                    quote = c;
                    continue;
                }
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            return -1;
        }

        private String prettyJson(String raw) {
            String t = raw.replace('“', '"').replace('”', '"').replace('’', '\'');
            t = t.replaceAll(",(\\s*[}\\]])", "$1");
            try {
                JsonElement el = JsonParser.parseString(t);
                Gson g = new GsonBuilder()
                        .setPrettyPrinting()
                        .disableHtmlEscaping()
                        .create();
                return g.toJson(el);
            } catch (Exception e) {
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

        private static String safe(String s) {
            return s == null ? "" : s;
        }

        private AiPayload tryParsePayload(String json) {
            try {
                String t = json.replace('“', '"').replace('”', '"').replace('’', '\'');
                t = t.replaceAll(",(\\s*[}\\]])", "$1");

                JsonElement el = JsonParser.parseString(t);
                if (!el.isJsonObject()) return null;

                JsonObject obj = el.getAsJsonObject();
                AiPayload p = new AiPayload();

                p.language = obj.has("language") ? obj.get("language").getAsString() : "";
                p.runtime = obj.has("runtime") ? obj.get("runtime").getAsString() : "";
                p.notes = obj.has("notes") ? obj.get("notes").getAsString() : "";

                if (obj.has("files") && obj.get("files").isJsonArray()
                        && obj.get("files").getAsJsonArray().size() > 0) {

                    JsonObject f0 = obj.get("files").getAsJsonArray()
                            .get(0).getAsJsonObject();

                    String path = f0.has("path") ? f0.get("path").getAsString() : "";
                    String filename = f0.has("filename") ? f0.get("filename").getAsString() : "";
                    String content = f0.has("content") ? f0.get("content").getAsString() : "";

                    p.code = content != null ? content : "";
                    p.filePath = normalizeFilePath(path, filename);

                } else {
                    p.code = obj.has("code") ? obj.get("code").getAsString() : "";
                }

                if ((p.code == null || p.code.isEmpty())
                        && (p.language == null || p.language.isEmpty())
                        && (p.runtime == null || p.runtime.isEmpty())) {
                    return null;
                }

                return p;
            } catch (Exception ignored) {
                Log.w(TAG, "tryParsePayload(): failed to parse JSON payload");
                return null;
            }
        }

        private String normalizeFilePath(String path, String filename) {
            String p = path == null ? "" : path.trim();
            String f = filename == null ? "" : filename.trim();

            // Remove leading ./ or / (repeated if needed)
            p = p.replaceAll("^([./]+)", "");
            f = f.replaceAll("^([./]+)", "");

            // Replace any double/multi slashes with a single slash
            p = p.replaceAll("/+", "/");
            f = f.replaceAll("/+", "/");

            if (p.isEmpty()) {
                return f;
            }
            return p + "/" + f;  // Always exactly one slash between
        }

        static class FileNode {
            String name;
            boolean isFile;
            LinkedHashMap<String, FileNode> children = new LinkedHashMap<>();

            FileNode(String name, boolean isFile) {
                this.name = name;
                this.isFile = isFile;
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
        @SerializedName("runnerHint")
        String runnerHint;
    }
}
