package com.example.aiassistantcoder;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.app.Activity.RESULT_OK;

public class HomeFragment extends Fragment {

    private EditText searchBar;
    private ImageView imageInput;
    private Button submitButton;
    private ProgressBar loadingIndicator;
    private ImageView imagePreview;
    private RelativeLayout imagePreviewContainer;
    private Bitmap selectedImageBitmap;

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        selectedImageBitmap = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), imageUri);
                        imagePreview.setImageBitmap(selectedImageBitmap);
                        imagePreviewContainer.setVisibility(View.VISIBLE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

        submitButton.setOnClickListener(v -> {
            String searchText = searchBar.getText().toString();
            if (searchText.isEmpty() && selectedImageBitmap == null) {
                Toast.makeText(getContext(), "Please enter some text or select an image", Toast.LENGTH_SHORT).show();
                return;
            }

            loadingIndicator.setVisibility(View.VISIBLE);

            GenerativeModel gm = new GenerativeModel("gemini-2.5-flash", BuildConfig.GEMINI_API_KEY);
            GenerativeModelFutures model = GenerativeModelFutures.from(gm);

            Content.Builder contentBuilder = new Content.Builder();
            if (selectedImageBitmap != null) {
                contentBuilder.addImage(selectedImageBitmap);
            }

            String systemPrompt =
                    "You are a coding-only assistant. You accept text and/or image inputs.\n\n" +
                            "Scope\n" +
                            "- If the text is not about programming or software, reply exactly:\n" +
                            "> I'm not suitable to answer this. If you have any programming question I may help you.\n" +
                            "- If the text is about programming, proceed normally.\n\n" +
                            "Images\n" +
                            "- If an image contains code, UI mockups, diagrams, schemas, or dev-relevant content, interpret it for programming purposes and continue.\n" +
                            "- If an image is not code but could inspire an app or feature, and the user hasn’t clearly stated what to do, ask them to choose one of these options (do not invent extra):\n" +
                            "  1) \"Generate app idea + feature list from this image\"\n" +
                            "  2) \"Draft a minimal architecture/tech stack for an app inspired by this image\"\n" +
                            "  3) \"Produce starter code (scaffold) for a simple app related to this image\"\n" +
                            "  4) \"Extract any visible text and turn it into structured data\"\n" +
                            "  5) \"Describe the UI components and output frontend code (React/Flutter) approximating it\"\n" +
                            "- If they do state a clear coding task for the image, skip the options and do it.\n\n" +
                            "Style & Output\n" +
                            "- Keep answers concise, actionable, and code-first.\n" +
                            "- When providing code, include a brief run/usage note.\n" +
                            "- Prefer modern, idiomatic patterns. If there are tradeoffs, list them briefly.\n" +
                            "- If more info is needed, ask at most one targeted question at the end.\n\n" +
                            "Safety & Refusals\n" +
                            "- Stay within programming topics. For anything out of scope, use the exact refusal line above.\n" +
                            "- Don’t output or execute unsafe code without warnings and safer alternatives.\n\n" +
                            "Response Format\n" +
                            "Intent: one line describing what you’re doing\n" +
                            "Plan: 2–5 bullet steps\n" +
                            "Answer: the code/commands or options list\n" +
                            "Next: one short, targeted follow-up (or “None”)\n";

            String finalPrompt = systemPrompt + " this is what the user gave " + "\n\n" + searchText;
            contentBuilder.addText(finalPrompt);
            Content content = contentBuilder.build();

            selectedImageBitmap = null;
            imagePreviewContainer.setVisibility(View.GONE);

            Executor executor = Executors.newSingleThreadExecutor();
            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String responseText = result.getText();

                    Project newProject = new Project(searchText);
                    newProject.addMessage(new Message(searchText, "user"));
                    newProject.addMessage(new Message(responseText, "model"));
                    ProjectRepository.getInstance().addProject(newProject);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            loadingIndicator.setVisibility(View.GONE);
                            Intent intent = new Intent(getActivity(), ResponseActivity.class);
                            intent.putExtra("projectTitle", newProject.getTitle());
                            startActivity(intent);
                        });
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            loadingIndicator.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }, executor);
        });

        return view;
    }
}
