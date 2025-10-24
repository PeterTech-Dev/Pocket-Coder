package com.example.aiassistantcoder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ChatFragment extends Fragment {

    private EditText chatInput;
    private ProgressBar loadingIndicator;
    private ChatFutures chat;
    private Project currentProject;
    private ImageView imagePreview;
    private RelativeLayout imagePreviewContainer;
    private Bitmap selectedImageBitmap;
    private ChatAdapter chatAdapter;
    private RecyclerView chatRecyclerView;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    try {
                        selectedImageBitmap = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), selectedImageUri);
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

        String projectTitle = getActivity().getIntent().getStringExtra("projectTitle");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentProject = ProjectRepository.getInstance().getProjectByTitle(projectTitle);
        } else {
            String initialQuery = getActivity().getIntent().getStringExtra("query");
            String initialResponse = getActivity().getIntent().getStringExtra("response");
            currentProject = new Project(initialQuery);
            currentProject.addMessage(new Message(initialQuery, "user"));
            currentProject.addMessage(new Message(initialResponse, "model"));
        }

        if (currentProject == null) {
            Toast.makeText(getContext(), "Project not found", Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return view;
        }

        setupChatRecyclerView();
        initializeChat();

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
            String newText = chatInput.getText().toString();
            if (newText.isEmpty() && selectedImageBitmap == null) {
                return; // Don't send empty messages
            }
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
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private void initializeChat() {
        GenerativeModel gm = new GenerativeModel("gemini-2.5-flash", BuildConfig.GEMINI_API_KEY);
        List<Content> history = new ArrayList<>();
        for (Message message : currentProject.getMessages()) {
            if (message.getText() != null) {
                Content.Builder contentBuilder = new Content.Builder();
                contentBuilder.addText(message.getText());
                contentBuilder.setRole(message.getRole());
                history.add(contentBuilder.build());
            }
        }
        chat = GenerativeModelFutures.from(gm).startChat(history);
    }

    private void sendMessage(String messageText) {
        loadingIndicator.setVisibility(View.VISIBLE);
        chatInput.setEnabled(false);

        Message userMessage = new Message(messageText, "user");
        if (selectedImageUri != null) {
            userMessage.setImageUri(selectedImageUri.toString());
        }
        currentProject.addMessage(userMessage);
        chatAdapter.notifyItemInserted(currentProject.getMessages().size() - 1);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        chatInput.setText("");

        String fullPrompt = messageText;
        if (currentProject.getCode() != null && !currentProject.getCode().isEmpty()) {
            fullPrompt += "\n\nUser-edited code:\n" + currentProject.getCode();
        }

        Content.Builder contentBuilder = new Content.Builder();
        if (selectedImageBitmap != null) {
            contentBuilder.addImage(selectedImageBitmap);
        }
        contentBuilder.addText(fullPrompt);
        Content content = contentBuilder.build();

        selectedImageBitmap = null;
        selectedImageUri = null;
        imagePreviewContainer.setVisibility(View.GONE);

        Executor executor = Executors.newSingleThreadExecutor();
        ListenableFuture<GenerateContentResponse> response = chat.sendMessage(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String aiResponse = result.getText();
                Message aiMessage = new Message(aiResponse, "model");
                currentProject.addMessage(aiMessage);

                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    ProjectRepository.getInstance().saveProjectToFirestore(currentProject, new ProjectRepository.ProjectSaveCallback() {
                        @Override
                        public void onSaved(String projectId) {
                            // Auto-saved, do nothing
                        }

                        @Override
                        public void onError(Exception e) {
                            // Optional: show a toast or log the error
                        }
                    });
                }
                
                // Pass code to the editor
                ResponseActivity activity = (ResponseActivity) getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        loadingIndicator.setVisibility(View.GONE);
                        chatInput.setEnabled(true);
                        chatAdapter.notifyItemInserted(currentProject.getMessages().size() - 1);
                        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);

                        String code = extractCode(aiResponse);
                        if (!code.isEmpty()) {
                            CodeEditorFragment editorFragment = (CodeEditorFragment) getParentFragmentManager().findFragmentByTag("f1");
                            if (editorFragment != null) {
                                editorFragment.setCode(code);
                            }
                        }
                    });
                }
            }

            @Override
            public void onFailure(Throwable t) {
                getActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    chatInput.setEnabled(true);
                    Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }, executor);
    }

    private String extractCode(String text) {
        if (text == null) return "";
        int start = text.indexOf("```");
        if (start == -1) return "";
        int end = text.indexOf("```", start + 3);
        if (end == -1) return "";
        return text.substring(start + 3, end);
    }
}
