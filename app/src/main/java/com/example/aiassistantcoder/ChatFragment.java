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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
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

    // Pick a model; 2.0-flash is a good default
    private static final String MODEL = "gemini-flash-latest";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";

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

        // add user message to project history & UI
        Message userMessage = new Message(messageText, "user");
        if (selectedImageUri != null) userMessage.setImageUri(selectedImageUri.toString());
        currentProject.addMessage(userMessage);
        chatAdapter.notifyItemInserted(currentProject.getMessages().size() - 1);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        chatInput.setText("");

        // Build REST payload: contents = prior history + this user turn
        GenerateContentRequest req = new GenerateContentRequest();
        req.contents = new ArrayList<>();

        // (Optional) guardrail/system prompt as first turn (send as 'user' for compatibility)
        req.contents.add(contentOf("user",
                partText("You are a coding-only assistant. Keep answers concise and code-first. " +
                        "If prompt is not about programming, reply exactly: " +
                        "I'm not suitable to answer this. If you have any programming question I may help you.")));

        // prior messages
        for (Message m : currentProject.getMessages()) {
            Content c = new Content();
            c.role = m.getRole(); // "user" | "model"
            c.parts = new ArrayList<>();
            if (m.getText() != null && !m.getText().isEmpty()) {
                c.parts.add(partText(m.getText()));
            }
            // if any history items had images stored as URIs, you can resolve & attach similarly
            req.contents.add(c);
        }

        // add current user turn image (if any) + text
        if (selectedImageBitmap != null) {
            // add user image part
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

        // Compose final user text turn (so image and text are separate parts/turns)
        if (!messageText.isEmpty()) {
            String fullPrompt = messageText;
            if (currentProject.getCode() != null && !currentProject.getCode().isEmpty()) {
                fullPrompt += "\n\nUser-edited code:\n" + currentProject.getCode();
            }
            req.contents.add(contentOf("user", partText(fullPrompt)));
        }

        // Fire the HTTP call on a background thread
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String reply = callGemini(req);
                if (reply == null) reply = "(no response)";

                // add model message to history
                Message aiMessage = new Message(reply, "model");
                currentProject.addMessage(aiMessage);

                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    ProjectRepository.getInstance().saveProjectToFirestore(currentProject, new ProjectRepository.ProjectSaveCallback() {
                        @Override public void onSaved(String projectId) { }
                        @Override public void onError(Exception e) { }
                    });
                }

                final String code = extractCode(reply);
                requireActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    chatInput.setEnabled(true);
                    chatAdapter.notifyItemInserted(currentProject.getMessages().size() - 1);
                    chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);

                    if (!code.isEmpty()) {
                        CodeEditorFragment editorFragment =
                                (CodeEditorFragment) getParentFragmentManager().findFragmentByTag("f1");
                        if (editorFragment != null) {
                            editorFragment.setCode(code);
                        }
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

    private String extractCode(String text) {
        if (text == null) return "";
        int start = text.indexOf("```");
        if (start == -1) return "";
        int end = text.indexOf("```", start + 3);
        if (end == -1) return "";
        return text.substring(start + 3, end);
    }

    public void setProject(Project project) {
        this.currentProject = project;
    }

    // ---- helpers to build parts/contents ----
    private static Content contentOf(String role, Part... parts) {
        Content c = new Content();
        c.role = role;
        c.parts = new ArrayList<>();
        for (Part p : parts) c.parts.add(p);
        return c;
    }

    private static Part partText(String text) {
        Part p = new Part();
        p.text = text;
        return p;
    }

    private static Part partInlineImage(Bitmap bmp, String mimeType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Encode as PNG by default
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);

        Part p = new Part();
        p.inlineData = new InlineData();
        p.inlineData.mimeType = mimeType;
        p.inlineData.data = b64;
        return p;
    }

    // ---- minimal DTOs for Gemini REST ----
    static class GenerateContentRequest {
        List<Content> contents;
    }

    static class GenerateContentResponse {
        Candidate[] candidates;
    }

    static class Candidate {
        Content content;
        @SerializedName("finishReason") String finishReason;
    }

    static class Content {
        String role;           // "user" | "model"
        List<Part> parts;      // text and/or inline_data
    }

    static class Part {
        String text;           // optional
        @SerializedName("inline_data")
        InlineData inlineData; // optional
    }

    static class InlineData {
        @SerializedName("mime_type")
        String mimeType;
        String data; // base64
    }
}
