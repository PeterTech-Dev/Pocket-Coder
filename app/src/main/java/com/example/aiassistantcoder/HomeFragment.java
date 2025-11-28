package com.example.aiassistantcoder;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;

import com.example.aiassistantcoder.ui.SnackBarApp;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeFragment extends Fragment {

    // Backend Gemini proxy endpoint
    private static final String BACKEND_BASE_URL = "https://pocketcoder-backend.onrender.com";
    private static final String GEMINI_PROXY_ENDPOINT = BACKEND_BASE_URL + "/gemini/generate";

    private static final String TAG = "HomeFragment";

    private EditText searchBar;
    private TextInputLayout inputLayout;          // NEW: reference to TextInputLayout
    private ProgressBar loadingIndicator;
    private ImageView imagePreview;
    private RelativeLayout imagePreviewContainer;
    private Bitmap selectedImageBitmap;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Executor bg = Executors.newSingleThreadExecutor();

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view == null) {
            view = new View(requireContext());
        }
        InputMethodManager imm =
                (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        selectedImageBitmap = MediaStore.Images.Media.getBitmap(
                                requireContext().getContentResolver(), imageUri);
                        imagePreview.setImageBitmap(selectedImageBitmap);
                        imagePreviewContainer.setVisibility(View.VISIBLE);
                    } catch (IOException e) {
                        Log.e(TAG, "Image load failed", e);
                        SnackBarApp.INSTANCE.show(
                                requireActivity().findViewById(android.R.id.content),
                                "Image load failed",
                                SnackBarApp.Type.ERROR
                        );

                    }
                }
            });

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: HomeFragment created");

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // ---- UI refs ----
        searchBar = view.findViewById(R.id.search_bar);
        inputLayout = view.findViewById(R.id.inputLayout);                    // ✅ correct type
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        imagePreview = view.findViewById(R.id.image_preview_home);
        imagePreviewContainer = view.findViewById(R.id.image_preview_container_home);
        ImageButton removeImageButton = view.findViewById(R.id.remove_image_button_home);

        View root = view.findViewById(R.id.home_root);
        root.setOnClickListener(v -> {
            if (searchBar != null && searchBar.hasFocus()) {
                searchBar.clearFocus();
                hideKeyboard();
            }
        });

        // ---- Use TextInputLayout end icon as "pick image" button ----
        inputLayout.setEndIconOnClickListener(v -> {
            Log.d(TAG, "End icon clicked (image input)");
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImage.launch(intent);
        });

        // ---- Remove image button ----
        removeImageButton.setOnClickListener(v -> {
            Log.d(TAG, "Remove image clicked");
            selectedImageBitmap = null;
            imagePreviewContainer.setVisibility(View.GONE);
        });

        ComposeView composeHero = view.findViewById(R.id.compose_hero);
        HomeCompose.INSTANCE.setupHero(composeHero);

        ComposeView composeButton = view.findViewById(R.id.compose_button);
        HomeCompose.INSTANCE.setupButton(composeButton, new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Submit button clicked");
                submitToGemini();
            }
        });

        view.setOnTouchListener((v, event) -> {
            if (searchBar.isFocused()) {
                searchBar.clearFocus();
                hideKeyboard();
            }
            return false;
        });

        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                searchBar.clearFocus();
                submitToGemini();     // Enter = Done = submit
                return true;          // consume
            }
            return false;
        });


        return view;
    }

    private void submitToGemini() {
        String userText = searchBar.getText().toString().trim();
        // debugger
        Log.d(TAG, "submitToGemini: user text = " + userText + ", hasImage=" + (selectedImageBitmap != null));

        if (userText.isEmpty() && selectedImageBitmap == null) {
            SnackBarApp.INSTANCE.show(
                    requireActivity().findViewById(android.R.id.content),
                    "Please enter some text or select an image",
                    SnackBarApp.Type.WARNING
            );
            return;
        }

        loadingIndicator.setVisibility(View.VISIBLE);

        String systemPrompt =
                "You are an assistant that outputs ONE JSON object and nothing else.\n" +
                        "\n" +
                        "If the user request is NOT about programming or software, reply with exactly:\n" +
                        "\"I'm not suitable to answer this. If you have any programming question I may help you.\"\n" +
                        "Do not output JSON in that case.\n" +
                        "\n" +
                        "Otherwise, output exactly this structure:\n" +
                        "{\n" +
                        "  \"language\": \"python|node|javascript|typescript|java|c|cpp|go\",\n" +
                        "  \"runtime\": \"string (e.g. python3.11, node20)\",\n" +
                        "  \"entrypoint\": \"string (e.g. main.py or src/index.js)\",\n" +
                        "  \"files\": [\n" +
                        "    {\n" +
                        "      \"path\": \"string\",\n" +
                        "      \"filename\": \"string\",\n" +
                        "      \"summary\": \"do not use more than max 80 words describing ONLY this file\",\n" +
                        "      \"content\": \"full source code of this file\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"notes\": \"short instructions for running the project\"\n" +
                        "}\n" +
                        "\n" +
                        "Rules:\n" +
                        "- Every item in \"files\" MUST contain ALL FOUR fields: path, filename, summary, content.\n" +
                        "- Do NOT split one file across multiple objects.\n" +
                        "- Use forward slashes in paths.\n" +
                        "- Do NOT add extra top-level fields.\n" +
                        "- Do NOT wrap the JSON in markdown or code fences.\n" +
                        "- If the user requests an application that requires visual output, UI, or display elements (e.g. windows, interfaces, graphics, webpages, dashboards), you MUST generate the solution using HTML by default.\n" +
                        "- However, if the user explicitly states that the project must use a specific language (even if visual), the assistant MUST generate the solution using the requested language instead.\n" +
                        "- If HTML is not requested but necessary for the requested visuals, the assistant should still choose HTML unless a different language is clearly and explicitly specified by the user.\n";

        JsonObject systemInstruction = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysPart = new JsonObject();
        sysPart.addProperty("text", systemPrompt);
        sysParts.add(sysPart);
        systemInstruction.add("parts", sysParts);

        // ---- Build user content (text + optional image) ----
        JsonArray userParts = new JsonArray();

        JsonObject userTextPart = new JsonObject();
        userTextPart.addProperty("text", userText);
        userParts.add(userTextPart);

        if (selectedImageBitmap != null) {
            String b64 = bitmapToBase64(selectedImageBitmap);
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mimeType", "image/png");
            inlineData.addProperty("data", b64);
            JsonObject imagePart = new JsonObject();
            imagePart.add("inlineData", inlineData);
            userParts.add(imagePart);
        }

        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        userContent.add("parts", userParts);

        JsonArray contents = new JsonArray();
        contents.add(userContent);

        // ---- Response schema ----
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.add("responseSchema", buildResponseSchema());

        // ---- Full request ----
        JsonObject payload = new JsonObject();
        payload.add("systemInstruction", systemInstruction);
        payload.add("contents", contents);
        payload.add("generationConfig", generationConfig);

        // We now call our backend proxy (no API key on device)
        String url = GEMINI_PROXY_ENDPOINT;

        // debugger
        Log.d(TAG, "submitToGemini: final JSON payload -> " + gson.toJson(payload));

        RequestBody body = RequestBody.create(
                gson.toJson(payload),
                MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder().url(url).post(body).build();

        bg.execute(() -> {
            try (Response resp = http.newCall(request).execute()) {

                // debugger
                Log.d(TAG, "submitToGemini: HTTP code=" + resp.code());

                if (!resp.isSuccessful()) {
                    String errBody = resp.body() != null ? resp.body().string() : "";
                    Log.e(TAG, "Gemini error HTTP " + resp.code() + ": " + errBody);

                    if (resp.code() == 503) {
                        postError("Gemini is overloaded right now. Try the same request again.");
                    } else {
                        postError("HTTP " + resp.code() + ": " + errBody);
                    }
                    return;
                }

                String json = resp.body() != null ? resp.body().string() : "";

                // debugger
                Log.d(TAG, "submitToGemini: RAW response from Gemini -> " + json);

                String modelText = extractTextFromCandidates(json);

                // debugger
                Log.d(TAG, "submitToGemini: modelText (first part text) -> " + modelText);

                String aiCode = "";
                String aiLanguage = "";
                String aiRuntime = "";
                String aiNotes = "";
                String display = modelText;   // fallback

                try {
                    JsonObject obj = gson.fromJson(modelText, JsonObject.class);
                    if (obj != null) {
                        aiLanguage = safeString(obj, "language");
                        aiRuntime = safeString(obj, "runtime");
                        aiNotes = safeString(obj, "notes");

                        StringBuilder sb = new StringBuilder();

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

                        if (obj.has("files") && obj.get("files").isJsonArray()) {
                            JsonArray filesArr = obj.getAsJsonArray("files");

                            for (int i = 0; i < filesArr.size(); i++) {
                                if (!filesArr.get(i).isJsonObject()) continue;
                                JsonObject fObj = filesArr.get(i).getAsJsonObject();

                                String path = safeString(fObj, "path");
                                String fname = safeString(fObj, "filename");
                                String summary = safeString(fObj, "summary");
                                String content = safeString(fObj, "content");

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
                        Log.d(TAG, "submitToGemini: display (pretty for chat) -> " + display);
                        Log.d(TAG, "submitToGemini: aiCode length -> " + (aiCode != null ? aiCode.length() : 0));
                    }
                } catch (JsonSyntaxException ex) {
                    // debugger
                    Log.w(TAG, "Model returned non-JSON; passing raw text");

                    aiCode = modelText;
                    display = modelText;

                    // debugger
                    Log.d(TAG, "submitToGemini: JSON parse failed, using raw modelText");
                }

                if (getActivity() == null) return;
                String query = searchBar.getText().toString().trim();
                String finalAiCode = aiCode;
                String finalAiLanguage = aiLanguage;
                String finalAiRuntime = aiRuntime;
                String finalAiNotes = aiNotes;
                String finalDisplay = display;

                getActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);

                    Log.d(TAG, "submitToGemini: launching ResponseActivity with query=" + query);

                    Intent intent = new Intent(getActivity(), ResponseActivity.class);

                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        Project newProject = new Project(query);
                        newProject.addMessage(new Message(query, "user"));
                        newProject.addMessage(new Message(finalDisplay, "model"));

                        ProjectRepository.getInstance().saveProjectToFirestore(
                                newProject,
                                new ProjectRepository.ProjectSaveCallback() {
                                    @Override
                                    public void onSaved(String projectId) {
                                        Log.d(TAG, "submitToGemini: project saved, id=" + projectId);
                                        intent.putExtra("projectTitle", newProject.getTitle());
                                        pushAiExtras(intent, finalAiCode, finalAiLanguage, finalAiRuntime, finalAiNotes);
                                        intent.putExtra("response", finalDisplay);
                                        intent.putExtra("ai_project_json", modelText);

                                        startActivity(intent);
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        Log.e(TAG, "submitToGemini: project save error", e);
                                        SnackBarApp.INSTANCE.show(
                                                requireActivity().findViewById(android.R.id.content),
                                                "Error saving project: " + e.getMessage(),
                                                SnackBarApp.Type.WARNING
                                        );
                                    }
                                });
                    } else {
                        intent.putExtra("query", query);
                        intent.putExtra("response", finalDisplay);
                        pushAiExtras(intent, finalAiCode, finalAiLanguage, finalAiRuntime, finalAiNotes);
                        intent.putExtra("ai_project_json", modelText);

                        startActivity(intent);
                    }

                    selectedImageBitmap = null;
                    imagePreviewContainer.setVisibility(View.GONE);
                });


            } catch (Exception e) {
                Log.e(TAG, "Request failure", e);
                postError(e.getMessage());
            }
        });

    }

    private void pushAiExtras(Intent intent, String code, String language, String runtime, String notes) {
        // debugger
        Log.d(TAG, "pushAiExtras: codeLen=" + (code != null ? code.length() : 0) + " lang=" + language + " rt=" + runtime);
        intent.putExtra("ai_code", code);
        intent.putExtra("ai_language", language);
        intent.putExtra("ai_runtime", runtime);
        intent.putExtra("ai_notes", notes);
    }

    private void postError(String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.GONE);
            SnackBarApp.INSTANCE.show(
                    requireActivity().findViewById(android.R.id.content),
                    "Error: " + msg,
                    SnackBarApp.Type.ERROR
            );

        });
    }

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
        files.addProperty("minItems", 1);

        properties.add("language", language);
        properties.add("runtime", runtime);
        properties.add("entrypoint", entrypoint);
        properties.add("files", files);
        properties.add("notes", notes);

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

    private String bitmapToBase64(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private String extractTextFromCandidates(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonArray candidates = root.has("candidates") ? root.getAsJsonArray("candidates") : null;
            if (candidates == null || candidates.size() == 0) return "";
            JsonObject cand0 = candidates.get(0).getAsJsonObject();
            JsonObject content = cand0.getAsJsonObject("content");
            if (content == null) return "";
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.size() == 0) return "";
            JsonObject p0 = parts.get(0).getAsJsonObject();
            return p0.has("text") ? p0.get("text").getAsString() : "";
        } catch (Exception e) {
            Log.e(TAG, "extractTextFromCandidates parse error", e);
            return "";
        }
    }

    private static String safeString(JsonObject obj, String key) {
        try {
            return obj.get(key).isJsonNull() ? "" : obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }
}
