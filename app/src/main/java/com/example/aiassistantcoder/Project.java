package com.example.aiassistantcoder;

import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Project implements Serializable {
    private String id;
    private String title;
    private Date createdAt;
    private List<Message> messages;
    private List<String> tags;
    private String code;

    public Project() {
        // No-argument constructor required for Firestore
        this.messages = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.createdAt = new Date(); // Ensure date is never null
    }

    public Project(String title) {
        this.title = title;
        this.createdAt = new Date(); // Set current date on creation
        this.messages = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    @ServerTimestamp
    public Date getCreatedAt() { return createdAt; }
    public List<Message> getMessages() { return messages; }
    public List<String> getTags() { return tags; }
    public String getCode() { return code; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void setCode(String code) { this.code = code; }

    public void addMessage(Message message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

    public String getDate() {
        if (createdAt == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(createdAt);
    }

    // This setter is for compatibility with older versions of the app
    public void setDate(Date date) {
        this.createdAt = date;
    }
}