package com.example.aiassistantcoder;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatFragment extends Fragment {

    private static final String TAG = "ChatFragment";

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
    private String latestEditorCode = "";

    // --- HTTP / JSON ---
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build();
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
        aiBus.getEditorCode().observe(getViewLifecycleOwner(), code -> {
            if (code != null) latestEditorCode = code;
        });

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

        // make user text pretty if it has JSON (this is fine for user side)
        String displayUserText = formatJsonInsideText(messageText);

        // add user message to list
        Message userMessage = new Message(displayUserText, "user");
        if (selectedImageUri != null) userMessage.setImageUri(selectedImageUri.toString());
        currentProject.addMessage(userMessage);
        chatAdapter.notifyItemInserted(currentProject.getMessages().size() - 1);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        chatInput.setText("");

        // build request for Gemini
        GenerateContentRequest req = new GenerateContentRequest();
        req.contents = new ArrayList<>();

        Log.d(TAG, "CHAT_REQ (to Gemini): " + gson.toJson(req));

        GenerationConfig gc = new GenerationConfig();
        gc.responseMimeType = "application/json";
        gc.responseSchema = buildResponseSchema();
        req.generationConfig = gc;

        // system instruction
        req.systemInstruction = contentOf("system", partText(
                "Respond strictly in this JSON structure:\n" +
                        "{ \"language\": string, \"runtime\": string, \"entrypoint\": string, \"files\": [ { \"path\": string, \"filename\": string, \"summary\": string, \"content\": string } ], \"notes\": string }"
        ));

        // last N turns to send
        List<Message> msgs = currentProject.getMessages();
        int start = Math.max(0, msgs.size() - 8);

        String editorSnapshot = (latestEditorCode != null && !latestEditorCode.isEmpty())
                ? latestEditorCode
                : (currentProject.getCode() == null ? "" : currentProject.getCode());

        for (int i = start; i < msgs.size(); i++) {
            Message m = msgs.get(i);
            if ("user".equals(m.getRole())) {
                String textToSend = stripMarkdownFenceIfAny(m.getText());
                Content c = new Content();
                c.role = "user";
                c.parts = new ArrayList<>();
                c.parts.add(partText(textToSend));
                req.contents.add(c);
            } else {
                // try to grab the raw JSON from our own previous model messages
                String json = extractFirstJsonObject(m.getText());
                if (json != null) {
                    Content c = new Content();
                    c.role = "model";
                    c.parts = new ArrayList<>();
                    c.parts.add(partText(json));
                    req.contents.add(c);
                }
            }
        }

        if (editorSnapshot != null && !editorSnapshot.isEmpty()) {
            Content editorContent = new Content();
            editorContent.role = "user";
            editorContent.parts = new ArrayList<>();
            editorContent.parts.add(partText(
                    "Current code in editor:\n```\n" + editorSnapshot + "\n```"
            ));
            req.contents.add(editorContent);
        }

        if (selectedImageBitmap != null) {
            Content imgTurn = new Content();
            imgTurn.role = "user";
            imgTurn.parts = new ArrayList<>();
            imgTurn.parts.add(partInlineImage(selectedImageBitmap, "image/png"));
            req.contents.add(imgTurn);
        }

        // clear preview
        selectedImageBitmap = null;
        selectedImageUri = null;
        imagePreviewContainer.setVisibility(View.GONE);

        // background call
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String rawResponse = callGemini(req);
                if (rawResponse == null) rawResponse = "(no response)";

// debugger
                Log.d(TAG, "CHAT_RESP (raw from Gemini): " + rawResponse);

                String modelText = extractTextFromCandidates(rawResponse);

// debugger
                Log.d(TAG, "CHAT_MODEL_TEXT (after extractTextFromCandidates): " + modelText);

                String aiCode     = "";
                String aiLanguage = "";
                String aiRuntime  = "";
                String aiNotes    = "";
                String display    = modelText;  // fallback

                try {
                    JsonObject obj = gson.fromJson(modelText, JsonObject.class);
                    if (obj != null) {
                        aiLanguage = safeString(obj, "language");
                        aiRuntime  = safeString(obj, "runtime");
                        aiNotes    = safeString(obj, "notes");

                        StringBuilder sb = new StringBuilder();

                        // header
                        if (!aiLanguage.isEmpty()) {
                            sb.append("**Language:** ").append(aiLanguage).append("\n");
                        }
                        if (!aiRuntime.isEmpty()) {
                            sb.append("**Runtime:** ").append(aiRuntime).append("\n");
                        }
                        String entrypoint = safeString(obj, "entrypoint");
                        if (!entrypoint.isEmpty()) {
                            sb.append("**Entrypoint:** ").append(entrypoint).append("\n");
                        }
                        if (!aiNotes.isEmpty()) {
                            sb.append("\n").append(aiNotes).append("\n\n");
                        }

                        // files
                        if (obj.has("files") && obj.get("files").isJsonArray()) {
                            JsonArray filesArr = obj.getAsJsonArray("files");

                            for (int i = 0; i < filesArr.size(); i++) {
                                if (!filesArr.get(i).isJsonObject()) continue;
                                JsonObject fObj = filesArr.get(i).getAsJsonObject();

                                String path    = safeString(fObj, "path");
                                String fname   = safeString(fObj, "filename");
                                String summary = safeString(fObj, "summary");
                                String content = safeString(fObj, "content");

                                // keep first file's code for editor
                                if (i == 0) {
                                    aiCode = content;
                                }

                                String fullPath;
                                if (path != null && !path.isEmpty() && !path.equals(".")) {
                                    fullPath = path + "/" + fname;
                                } else {
                                    fullPath = fname;
                                }

                                sb.append("**File:** ").append(fullPath).append("\n");
                                if (!summary.isEmpty()) {
                                    sb.append("*").append(summary).append("*\n\n");
                                }

                                if (!content.isEmpty()) {
                                    String fenceLang = aiLanguage != null ? aiLanguage.toLowerCase() : "";
                                    sb.append("```").append(fenceLang).append("\n");
                                    sb.append(content).append("\n");
                                    sb.append("```").append("\n\n");
                                }
                            }
                        }

                        display = sb.toString().trim();

                        // debugger
                        Log.d(TAG, "CHAT_DISPLAY (what we will show in chat): " + display);

                    }
                } catch (JsonSyntaxException ex) {
                    Log.w(TAG, "Model returned non-JSON; showing raw text");
                    aiCode = modelText;
                    display = modelText;

                    // debugger
                    Log.d(TAG, "CHAT_DISPLAY (non-JSON, using raw): " + display);

                }

                String finalDisplay    = display;
                String finalAiCode     = aiCode;
                String finalAiLanguage = aiLanguage;
                String finalAiRuntime  = aiRuntime;
                String finalAiNotes    = aiNotes;

                requireActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    chatInput.setEnabled(true);

                    // show the "pretty" multi-file text
                    Message aiMsg = new Message(finalDisplay, "model");

                    // debugger
                    Log.d(TAG, "CHAT_FINAL_MESSAGE (added to RecyclerView): " + finalDisplay);

                    aiMsg.setCode(finalAiCode);
                    currentProject.addMessage(aiMsg);
                    chatAdapter.notifyItemInserted(currentProject.getMessages().size() - 1);
                    chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);

                    // push to editor
                    aiBus.publish(
                            finalAiLanguage,
                            finalAiRuntime,
                            finalAiNotes,
                            finalAiCode
                    );

                    // save project
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        ProjectRepository.getInstance().saveProjectToFirestore(currentProject,
                                new ProjectRepository.ProjectSaveCallback() {
                                    @Override public void onSaved(String projectId) {}
                                    @Override public void onError(Exception e) {}
                                });
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error calling Gemini", e);
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
            String respBody = resp.body() != null ? resp.body().string() : "";

            if (!resp.isSuccessful()) {
                Log.e(TAG,
                        "Gemini error " + resp.code() + ": " + resp.message()
                                + " | body=" + respBody);
                return respBody.isEmpty()
                        ? "Gemini returned HTTP " + resp.code() + " (" + resp.message() + ")"
                        : respBody;
            }

            return respBody;
        }
    }

    private String extractTextFromCandidates(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonArray candidates = root.has("candidates") ? root.getAsJsonArray("candidates") : null;
            if (candidates == null || candidates.size() == 0) return json;
            JsonObject cand0 = candidates.get(0).getAsJsonObject();
            JsonObject content = cand0.getAsJsonObject("content");
            if (content == null) return json;
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.size() == 0) return json;
            JsonObject p0 = parts.get(0).getAsJsonObject();
            return p0.has("text") ? p0.get("text").getAsString() : json;
        } catch (Exception e) {
            Log.e(TAG, "extractTextFromCandidates parse error", e);
            return json;
        }
    }

    public void setProject(Project project) {
        this.currentProject = project;
    }

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

    // keep this for USER messages – for model messages we now build our own display
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

        String result = out.toString().replaceAll("(?s)```\\s*\\n(\\s*[\\[{].*?)\\n```", "```json\n$1\n```");
// debugger
        Log.d(TAG, "FORMAT_OUT (after prettify): " + result);
        return result;
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

    private static String safeString(JsonObject obj, String key) {
        try {
            if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
                return "";
            }
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
            if (cand.startsWith("{") && cand.endsWith("}")) {
                return cand;
            }
            i = brace + 1;
        }
        return null;
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

    // ---- response schema builder ----
    private JsonObject buildResponseSchema() {
        JsonObject properties = new JsonObject();

        JsonObject language = new JsonObject();
        language.addProperty("type", "string");
        language.addProperty("maxLength", 60);

        JsonObject runtime = new JsonObject();
        runtime.addProperty("type", "string");
        runtime.addProperty("maxLength", 80);

        JsonObject entrypoint = new JsonObject();
        entrypoint.addProperty("type", "string");
        entrypoint.addProperty("maxLength", 120);

        JsonObject notes = new JsonObject();
        notes.addProperty("type", "string");
        notes.addProperty("maxLength", 280);

        JsonObject fileProps = new JsonObject();
        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        path.addProperty("maxLength", 200);

        JsonObject filename = new JsonObject();
        filename.addProperty("type", "string");
        filename.addProperty("maxLength", 200);

        JsonObject summary = new JsonObject();
        summary.addProperty("type", "string");
        summary.addProperty("maxLength", 400);

        JsonObject content = new JsonObject();
        content.addProperty("type", "string");

        fileProps.add("path", path);
        fileProps.add("filename", filename);
        fileProps.add("summary", summary);
        fileProps.add("content", content);

        JsonArray fileRequired = new JsonArray();
        fileRequired.add("path");
        fileRequired.add("filename");
        fileRequired.add("summary");
        fileRequired.add("content");

        JsonObject fileObj = new JsonObject();
        fileObj.addProperty("type", "object");
        fileObj.add("properties", fileProps);
        fileObj.add("required", fileRequired);

        JsonObject files = new JsonObject();
        files.addProperty("type", "array");
        files.add("items", fileObj);
        files.addProperty("minItems", 0);

        properties.add("language", language);
        properties.add("runtime", runtime);
        properties.add("entrypoint", entrypoint);
        properties.add("notes", notes);
        properties.add("files", files);

        JsonArray required = new JsonArray();
        required.add("language");
        required.add("runtime");
        required.add("entrypoint");
        required.add("files");
        required.add("notes");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    // ---- DTOs ----
    static class GenerateContentRequest {
        @SerializedName("systemInstruction")
        Content systemInstruction;
        List<Content> contents;
        @SerializedName("generationConfig")
        GenerationConfig generationConfig;
    }

    static class GenerationConfig {
        @SerializedName("responseMimeType")
        String responseMimeType;
        @SerializedName("responseSchema")
        JsonObject responseSchema;
        @SerializedName("maxOutputTokens")
        Integer maxOutputTokens;
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
        Part p = new Part();
        p.inlineData = new InlineData();
        p.inlineData.mimeType = mimeType;
        p.inlineData.data = b64;
        return p;
    }

    // --- old payload types in case you still need them ---
    static class CodePayload {
        public String code;
        public String language;
        public String runtime;
        public String notes;
        public String entrypoint;
        public String filename;
        public String summary;
    }

    static class ProjectPayload {
        String language = "";
        String runtime = "";
        String entrypoint = "";
        List<ProjectFile> files = new ArrayList<>();
        String notes = "";
    }

    static class ProjectFile {
        String path = "";
        String filename = "";
        String summary = "";
        String content = "";
    }
}
