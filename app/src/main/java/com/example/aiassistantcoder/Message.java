package com.example.aiassistantcoder;

import java.io.Serializable;

public class Message implements Serializable {
    private String text;
    private String role;
    private String imageUri; // Keep this for local display

    public Message() {
        // No-argument constructor required for Firestore
    }

    public Message(String text, String role) {
        this.text = text;
        this.role = role;
    }

    // Getters
    public String getText() { return text; }
    public String getRole() { return role; }
    public String getImageUri() { return imageUri; }

    // Setters
    public void setText(String text) { this.text = text; }
    public void setRole(String role) { this.role = role; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }
}