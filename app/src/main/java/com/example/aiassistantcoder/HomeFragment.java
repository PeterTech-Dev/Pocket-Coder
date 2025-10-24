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
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.app.Activity.RESULT_OK;

public class HomeFragment extends Fragment {

    private static final String MODEL = "gemini-flash-latest";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    private static final String TAG = "HomeFragment";

    private EditText searchBar;
    private ImageView imageInput;
    private Button submitButton;
    private ProgressBar loadingIndicator;
    private ImageView imagePreview;
    private RelativeLayout imagePreviewContainer;
    private Bitmap selectedImageBitmap;

    private final OkHttpClient http = new OkHttpClient();
    private final Gson gson = new Gson();
    private final Executor bg = Executors.newSingleThreadExecutor();

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
                        Toast.makeText(getContext(), "Image load failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        searchBar = view.findViewById(R.id.search_bar);
        imageInput = view.findViewById(R.id.image_input);
        submitButton = view.findViewById(R.id.submit_button);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        imagePreview = view.findViewById(R.id.image_preview_home);
        imagePreviewContainer = view.findViewById(R.id.image_preview_container_home);
        ImageButton removeImageButton = view.findViewById(R.id.remove_image_button_home);

        imageInput.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImage.launch(intent);
        });

        removeImageButton.setOnClickListener(v -> {
            selectedImageBitmap = null;
            imagePreviewContainer.setVisibility(View.GONE);
        });

        submitButton.setOnClickListener(v -> submitToGemini());

        return view;
    }

    private void submitToGemini() {
        String userText = searchBar.getText().toString().trim();
        if (userText.isEmpty() && selectedImageBitmap == null) {
            Toast.makeText(getContext(), "Please enter some text or select an image", Toast.LENGTH_SHORT).show();
            return;
        }

        loadingIndicator.setVisibility(View.VISIBLE);

        // ---- Build system instruction ----
        String systemPrompt =
                "You are a coding-only assistant.\n" +
                        "- If the input is not about programming/software, reply with exactly:\n" +
                        "\"I'm not suitable to answer this. If you have any programming question I may help you.\"\n" +
                        "- Otherwise: classify the code language & runtime, and produce runnable code.\n" +
                        "- Keep it concise, code-first. If you have comments about the code, put them as code comments.\n" +
                        "- Output must be valid JSON matching the response_schema.\n";

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

        // ---- Response schema (language/runtime/code/notes) ----
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.add("responseSchema", buildResponseSchema());

        // ---- Full request ----
        JsonObject payload = new JsonObject();
        payload.add("systemInstruction", systemInstruction);
        payload.add("contents", contents);
        payload.add("generationConfig", generationConfig);

        String url = ENDPOINT + "?key=" + BuildConfig.GEMINI_API_KEY;
        RequestBody body = RequestBody.create(
                gson.toJson(payload),
                MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder().url(url).post(body).build();

        bg.execute(() -> {
            try (Response resp = http.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    String errBody = resp.body() != null ? resp.body().string() : "";
                    Log.e(TAG, "Gemini error HTTP " + resp.code() + ": " + errBody);
                    postError("HTTP " + resp.code() + ": " + errBody);
                    return;
                }

                String json = resp.body() != null ? resp.body().string() : "";
                String modelText = extractTextFromCandidates(json);

                // Try to parse the model text as our JSON shape
                String aiCode = "";
                String aiLanguage = "";
                String aiRuntime = "";
                String aiNotes = "";

                try {
                    JsonObject obj = gson.fromJson(modelText, JsonObject.class);
                    if (obj != null && obj.has("code")) aiCode = safeString(obj, "code");
                    if (obj != null && obj.has("language")) aiLanguage = safeString(obj, "language");
                    if (obj != null && obj.has("runtime")) aiRuntime = safeString(obj, "runtime");
                    if (obj != null && obj.has("notes")) aiNotes = safeString(obj, "notes");
                } catch (JsonSyntaxException ex) {
                    Log.w(TAG, "Model returned non-JSON; passing raw text");
                    aiCode = modelText; // fall back: just dump text into editor
                }

                if (getActivity() == null) return;
                String query = searchBar.getText().toString().trim();
                String finalAiCode = aiCode;
                String finalAiLanguage = aiLanguage;
                String finalAiRuntime = aiRuntime;
                String finalAiNotes = aiNotes;
                getActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);

                    Intent intent = new Intent(getActivity(), ResponseActivity.class);
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        Project newProject = new Project(query);
                        newProject.addMessage(new Message(query, "user"));
                        newProject.addMessage(new Message(modelText, "model"));
                        ProjectRepository.getInstance().saveProjectToFirestore(newProject,
                                new ProjectRepository.ProjectSaveCallback() {
                                    @Override public void onSaved(String projectId) {
                                        intent.putExtra("projectTitle", newProject.getTitle());
                                        pushAiExtras(intent, finalAiCode, finalAiLanguage, finalAiRuntime, finalAiNotes);
                                        startActivity(intent);
                                    }
                                    @Override public void onError(Exception e) {
                                        Toast.makeText(getContext(),
                                                "Error saving project: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        intent.putExtra("query", query);
                        intent.putExtra("response", modelText);
                        pushAiExtras(intent, finalAiCode, finalAiLanguage, finalAiRuntime, finalAiNotes);
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
        intent.putExtra("ai_code", code);
        intent.putExtra("ai_language", language);
        intent.putExtra("ai_runtime", runtime);
        intent.putExtra("ai_notes", notes);
    }

    private void postError(String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Error: " + msg, Toast.LENGTH_LONG).show();
        });
    }

    private JsonObject buildResponseSchema() {
        JsonObject properties = new JsonObject();

        JsonObject language = new JsonObject(); language.addProperty("type", "string");
        JsonObject runtime  = new JsonObject(); runtime.addProperty("type", "string");
        JsonObject runnerHint = new JsonObject(); runnerHint.addProperty("type", "string");
        JsonObject code     = new JsonObject(); code.addProperty("type", "string");
        JsonObject notes    = new JsonObject(); notes.addProperty("type", "string");

        properties.add("language", language);
        properties.add("runtime", runtime);
        properties.add("runnerHint", runnerHint);
        properties.add("code", code);
        properties.add("notes", notes);

        JsonArray required = new JsonArray();
        required.add("language");
        required.add("runtime");
        required.add("code");

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
