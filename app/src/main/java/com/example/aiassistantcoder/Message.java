package com.example.aiassistantcoder;

import java.io.Serializable;

public class Message implements Serializable {
    private String text;
    private String role;
    private String imageUri;

    // NEW: for AI responses
    private String code = "";
    private String filePath = "";

    public Message() {
        // needed for Firestore
    }

    public Message(String text, String role) {
        this.text = text;
        this.role = role;
    }

    // ---- getters ----
    public String getText() { return text; }
    public String getRole() { return role; }
    public String getImageUri() { return imageUri; }

    public String getCode() { return code; }
    public String getFilePath() { return filePath; }

    // ---- setters ----
    public void setText(String text) { this.text = text; }
    public void setRole(String role) { this.role = role; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }

    public void setCode(String code) { this.code = code; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
