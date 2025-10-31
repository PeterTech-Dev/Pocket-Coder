package com.example.aiassistantcoder;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatFragment extends Fragment {

    // --- UI ---
    private EditText chatInput;
    private ProgressBar loadingIndicator;
    private ImageView imagePreview;
    private RelativeLayout imagePreviewContainer;
    private RecyclerView chatRecyclerView;

    // --- App state ---
    private Project currentProject;
    private ChatAdapter chatAdapter;
    private Bitmap selectedImageBitmap;
    private Uri selectedImageUri;

    // --- HTTP / JSON ---
    private final OkHttpClient http = new OkHttpClient();
    private final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Model/endpoint
    private static final String MODEL = "gemini-flash-latest";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";

    // Bus to the editor
    private AiUpdateViewModel aiBus;

    // Image picker
    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    try {
                        selectedImageBitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), selectedImageUri);
                        imagePreview.setImageBitmap(selectedImageBitmap);
                        imagePreviewContainer.setVisibility(View.VISIBLE);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Error loading image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        chatInput = view.findViewById(R.id.chat_input);
        loadingIndicator = view.findViewById(R.id.loading_indicator_response);
        imagePreview = view.findViewById(R.id.image_preview);
        imagePreviewContainer = view.findViewById(R.id.image_preview_container);
        chatRecyclerView = view.findViewById(R.id.chat_recycler_view);

        aiBus = new ViewModelProvider(requireActivity()).get(AiUpdateViewModel.class);

        ImageButton sendButton = view.findViewById(R.id.send_button);
        ImageButton imageInputButton = view.findViewById(R.id.image_input_button);
        ImageButton removeImageButton = view.findViewById(R.id.remove_image_button);

        if (currentProject == null) {
            Toast.makeText(getContext(), "Project not found", Toast.LENGTH_SHORT).show();
            requireActivity().finish();
            return view;
        }

        setupChatRecyclerView();

        imageInputButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImage.launch(intent);
        });

        removeImageButton.setOnClickListener(v -> {
            selectedImageBitmap = null;
            selectedImageUri = null;
            imagePreviewContainer.setVisibility(View.GONE);
        });

        sendButton.setOnClickListener(v -> {
            String newText = chatInput.getText().toString().trim();
            if (newText.isEmpty() && selectedImageBitmap == null) return;
            sendMessage(newText);
        });

        return view;
    }

    private void setupChatRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatAdapter = new ChatAdapter(currentProject.getMessages(), getContext());
        chatRecyclerView.setAdapter(chatAdapter);
        if (chatAdapter.getItemCount() > 0) {
            chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void sendMessage(String messageText) {
        loadingIndicator.setVisibility(View.VISIBLE);
        chatInput.setEnabled(false);

        // DISPLAY (prettified if JSON is embedded) vs RAW (for API)
        String displayUserText = formatJsonInsideText(messageText);

        // add user message to project history & UI
        Message userMessage = new Message(displayUserText, "user");
        if (selectedImageUri != null) userMessage.setImageUri(selectedImageUri.toString());
        currentProject.addMessage(userMessage);
        chatAdapter.notifyItemInserted(currentProject.getMessages().size() - 1);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        chatInput.setText("");

        // Build REST payload
        GenerateContentRequest req = new GenerateContentRequest();
        req.contents = new ArrayList<>();

        // --- NEW: strong, consistent system instruction to force JSON schema ---
        req.systemInstruction = contentOf("system", partText(
                "You are a coding assistant.\n" +
                        "Always respond ONLY with strict JSON using this schema:\n" +
                        "{\n" +
                        "  \"code\": string,          // the primary code answer or empty string\n" +
                        "  \"language\": string,      // e.g., \"python\", \"javascript\", \"java\", may be empty\n" +
                        "  \"runtime\": string,       // runtime/versions/libraries or empty\n" +
                        "  \"notes\": string          // short plain-text hints or empty\n" +
                        "}\n" +
                        "Do not include markdown, backticks, or any text outside the JSON object."
        ));

        // Guardrail (kept, but lighter; main control is systemInstruction)
        req.contents.add(contentOf("user",
                partText("If prompt is not about programming, reply with an empty JSON having empty strings for all fields.")));

        // prior messages (strip potential fences for cleaner context)
        for (Message m : currentProject.getMessages()) {
            Content c = new Content();
            c.role = m.getRole();
            c.parts = new ArrayList<>();
            if (m.getText() != null && !m.getText().isEmpty()) {
                c.parts.add(partText(stripMarkdownFenceIfAny(m.getText())));
            }
            req.contents.add(c);
        }

        // optional image
        if (selectedImageBitmap != null) {
            Content imgTurn = new Content();
            imgTurn.role = "user";
            imgTurn.parts = new ArrayList<>();
            imgTurn.parts.add(partInlineImage(selectedImageBitmap, "image/png"));
            req.contents.add(imgTurn);
        }

        // clear preview after packaging
        selectedImageBitmap = null;
        selectedImageUri = null;
        imagePreviewContainer.setVisibility(View.GONE);

        // include current code (raw) for context
        if (!messageText.isEmpty()) {
            String fullPrompt = messageText;
            if (currentProject.getCode() != null && !currentProject.getCode().isEmpty()) {
                fullPrompt += "\n\nUser-edited code:\n" + currentProject.getCode();
            }
            req.contents.add(contentOf("user", partText(fullPrompt)));
        }

        // HTTP call
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String reply = callGemini(req);
                if (reply == null) reply = "(no response)";

                // Pretty-print for DISPLAY only (chat bubble)
                String displayReply = formatJsonInsideText(reply);

                // add model message to history (display version)
                Message aiMessage = new Message(displayReply, "model");
                currentProject.addMessage(aiMessage);

                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    ProjectRepository.getInstance().saveProjectToFirestore(currentProject, new ProjectRepository.ProjectSaveCallback() {
                        @Override public void onSaved(String projectId) { }
                        @Override public void onError(Exception e) { }
                    });
                }

                // --- NEW: parse strict JSON first, fallback to code fence ---
                CodePayload payload = parseReplyToPayload(reply);
                final String codeToApply = (payload.code != null && !payload.code.isEmpty())
                        ? payload.code
                        : extractCode(reply); // fallback if model slipped

                final String lang   = payload.language;
                final String run    = payload.runtime;
                final String notes  = payload.notes;

                requireActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    chatInput.setEnabled(true);
                    chatAdapter.notifyItemInserted(currentProject.getMessages().size() - 1);
                    chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);

                    if (codeToApply != null && !codeToApply.isEmpty()) {
                        aiBus.publish(lang, run, notes, codeToApply);
                    }
                });

            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    chatInput.setEnabled(true);
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ---- REST call ----
    private String callGemini(GenerateContentRequest req) throws IOException {
        String bodyJson = gson.toJson(req);
        Request request = new Request.Builder()
                .url(ENDPOINT + BuildConfig.GEMINI_API_KEY)
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        try (Response resp = http.newCall(request).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + ": " + resp.message());
            String respBody = resp.body() != null ? resp.body().string() : "";
            GenerateContentResponse parsed = gson.fromJson(respBody, GenerateContentResponse.class);
            if (parsed == null || parsed.candidates == null || parsed.candidates.length == 0) return null;
            if (parsed.candidates[0].content == null || parsed.candidates[0].content.parts == null) return null;

            StringBuilder sb = new StringBuilder();
            for (Part p : parsed.candidates[0].content.parts) {
                if (p != null && p.text != null) sb.append(p.text);
            }
            return sb.toString().trim();
        }
    }

    // --- NEW: strict JSON parsing with fallback ---
    private CodePayload parseReplyToPayload(String reply) {
        CodePayload out = new CodePayload();
        if (reply == null || reply.isEmpty()) return out;

        // Try parse as whole JSON
        try {
            JsonElement el = JsonParser.parseString(reply);
            if (el != null && el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                out.code     = safeString(obj, "code");
                out.language = safeString(obj, "language");
                out.runtime  = safeString(obj, "runtime");
                out.notes    = safeString(obj, "notes");
                return out;
            }
        } catch (Exception ignore) { /* not a bare JSON object */ }

        // Try to extract the first JSON object substring from prose
        String embedded = extractFirstJsonObject(reply);
        if (embedded != null) {
            try {
                JsonObject obj = JsonParser.parseString(embedded).getAsJsonObject();
                out.code     = safeString(obj, "code");
                out.language = safeString(obj, "language");
                out.runtime  = safeString(obj, "runtime");
                out.notes    = safeString(obj, "notes");
                return out;
            } catch (Exception ignore) { /* fall through */ }
        }

        // Nothing JSON-like found; leave empty (caller will fallback to fenced code).
        return out;
    }

    private static String safeString(JsonObject obj, String key) {
        try {
            if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractFirstJsonObject(String text) {
        if (text == null) return null;
        int i = 0, n = text.length();
        while (i < n) {
            int brace = text.indexOf('{', i);
            if (brace == -1) return null;
            int end = findMatchingJsonEnd(text, brace);
            if (end == -1) return null;
            String cand = text.substring(brace, end + 1).trim();
            // quick sanity check
            if (cand.startsWith("{") && cand.endsWith("}")) return cand;
            i = brace + 1;
        }
        return null;
    }

    private String extractCode(String text) {
        if (text == null) return "";
        int start = text.indexOf("```");
        if (start == -1) return "";
        int end = text.indexOf("```", start + 3);
        if (end == -1) return "";
        // Strip optional language tag like ```html\n
        int nl = text.indexOf('\n', start + 3);
        int contentStart = (nl != -1 && nl < end) ? nl + 1 : start + 3;
        return text.substring(contentStart, end);
    }

    public void setProject(Project project) { this.currentProject = project; }

    // ---------- JSON formatting helpers (DISPLAY ONLY) ----------
    private String stripMarkdownFenceIfAny(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNl != -1 && lastFence > firstNl) {
                String inner = t.substring(firstNl + 1, lastFence);
                return inner.trim();
            }
        }
        return s;
    }

    private String formatJsonInsideText(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder out = new StringBuilder(text.length() + 128);
        int i = 0, n = text.length();

        while (i < n) {
            int brace = text.indexOf('{', i);
            int bracket = text.indexOf('[', i);
            int start = (brace == -1) ? bracket : (bracket == -1 ? brace : Math.min(brace, bracket));

            if (start == -1) { out.append(text, i, n); break; }
            out.append(text, i, start);

            int end = findMatchingJsonEnd(text, start);
            if (end == -1) { out.append(text.substring(start)); break; }

            String candidate = text.substring(start, end + 1);
            String pretty = candidate;
            try { pretty = prettifyJson(candidate); }
            catch (Exception e) {
                String fixed = trySanitizeJson(candidate);
                try { if (fixed != null) pretty = prettifyJson(fixed); }
                catch (Exception ignore) { pretty = candidate; }
            }

            out.append("\n```json\n").append(pretty).append("\n```\n");
            i = end + 1;
        }

        return out.toString().replaceAll("(?s)```\\s*\\n(\\s*[\\[{].*?)\\n```", "```json\n$1\n```");
    }

    private int findMatchingJsonEnd(String text, int start) {
        int depth = 0; boolean inString = false; char quote = 0; boolean esc = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == quote) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; quote = c; continue; }
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private String prettifyJson(String raw) {
        JsonElement el = JsonParser.parseString(raw);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(el);
    }

    private String trySanitizeJson(String s) {
        if (s == null) return null;
        String t = s.trim();
        t = t.replace('“', '"').replace('”', '"').replace('’', '\'');
        t = t.replaceAll(",(\\s*[}\\]])", "$1");
        return t;
    }

    // ---- minimal DTOs for Gemini REST ----
    static class GenerateContentRequest {
        @SerializedName("systemInstruction") Content systemInstruction; // NEW
        List<Content> contents;
    }
    static class GenerateContentResponse { Candidate[] candidates; }
    static class Candidate { Content content; @SerializedName("finishReason") String finishReason; }
    static class Content { String role; List<Part> parts; }
    static class Part { String text; @SerializedName("inline_data") InlineData inlineData; }
    static class InlineData { @SerializedName("mime_type") String mimeType; String data; }

    private static Content contentOf(String role, Part... parts) {
        Content c = new Content(); c.role = role; c.parts = new ArrayList<>();
        for (Part p : parts) c.parts.add(p); return c;
    }
    private static Part partText(String text) { Part p = new Part(); p.text = text; return p; }
    private static Part partInlineImage(Bitmap bmp, String mimeType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        Part p = new Part(); p.inlineData = new InlineData(); p.inlineData.mimeType = mimeType; p.inlineData.data = b64; return p;
    }

    // --- payload for editor bus ---
    static class CodePayload {
        String code = "";
        String language = "";
        String runtime = "";
        String notes = "";
    }
}
