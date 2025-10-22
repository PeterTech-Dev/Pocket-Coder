package com.example.aiassistantcoder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ResponseActivity extends AppCompatActivity {

    private EditText chatInput;
    private ProgressBar loadingIndicator;
    private ChatFutures chat;
    private Project currentProject;
    private ImageView imagePreview;
    private RelativeLayout imagePreviewContainer;
    private Bitmap selectedImageBitmap;
    private ChatAdapter chatAdapter;
    private RecyclerView chatRecyclerView;

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        selectedImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                        imagePreview.setImageBitmap(selectedImageBitmap);
                        imagePreviewContainer.setVisibility(View.VISIBLE);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_response);

        chatInput = findViewById(R.id.chat_input);
        loadingIndicator = findViewById(R.id.loading_indicator_response);
        imagePreview = findViewById(R.id.image_preview);
        imagePreviewContainer = findViewById(R.id.image_preview_container);
        chatRecyclerView = findViewById(R.id.chat_recycler_view);

        ImageButton sendButton = findViewById(R.id.send_button);
        ImageButton closeButton = findViewById(R.id.close_button);
        ImageButton imageInputButton = findViewById(R.id.image_input_button);
        ImageButton removeImageButton = findViewById(R.id.remove_image_button);

        String projectTitle = getIntent().getStringExtra("projectTitle");
        currentProject = ProjectRepository.getInstance().getProjectByTitle(projectTitle);

        if (currentProject == null) {
            Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupChatRecyclerView();
        initializeChat();

        closeButton.setOnClickListener(v -> finish());

        imageInputButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImage.launch(intent);
        });

        removeImageButton.setOnClickListener(v -> {
            selectedImageBitmap = null;
            imagePreviewContainer.setVisibility(View.GONE);
        });

        sendButton.setOnClickListener(v -> {
            String newText = chatInput.getText().toString();
            if (newText.isEmpty() && selectedImageBitmap == null) {
                return; // Don't send empty messages
            }
            sendMessage(newText);
        });
    }

    private void setupChatRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatAdapter = new ChatAdapter(currentProject.getConversationHistory(), this);
        chatRecyclerView.setAdapter(chatAdapter);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private void initializeChat() {
        GenerativeModel gm = new GenerativeModel("gemini-2.5-flash", BuildConfig.GEMINI_API_KEY);
        List<Content> history = new ArrayList<>();
        for (Message message : currentProject.getConversationHistory()) {
            Content.Builder contentBuilder = new Content.Builder();
            contentBuilder.addText(message.getText());
            contentBuilder.setRole(message.getRole());
            history.add(contentBuilder.build());
        }
        chat = GenerativeModelFutures.from(gm).startChat(history);
    }

    private void sendMessage(String messageText) {
        loadingIndicator.setVisibility(View.VISIBLE);
        chatInput.setEnabled(false);

        Message userMessage = new Message(messageText, "user");
        currentProject.addMessage(userMessage);
        chatAdapter.notifyItemInserted(currentProject.getConversationHistory().size() - 1);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        chatInput.setText("");

        Content.Builder contentBuilder = new Content.Builder();
        if (selectedImageBitmap != null) {
            contentBuilder.addImage(selectedImageBitmap);
        }
        contentBuilder.addText(messageText);
        Content content = contentBuilder.build();

        selectedImageBitmap = null;
        imagePreviewContainer.setVisibility(View.GONE);

        Executor executor = Executors.newSingleThreadExecutor();
        ListenableFuture<GenerateContentResponse> response = chat.sendMessage(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String aiResponse = result.getText();
                Message aiMessage = new Message(aiResponse, "model");
                currentProject.addMessage(aiMessage);
                runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    chatInput.setEnabled(true);
                    chatAdapter.notifyItemInserted(currentProject.getConversationHistory().size() - 1);
                    chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    chatInput.setEnabled(true);
                    Toast.makeText(ResponseActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }, executor);
    }
}
