package com.example.aiassistantcoder;

import java.io.Serializable;

public class Message implements Serializable {
    private final String text;
    private final String role;

    public Message(String text, String role) {
        this.text = text;
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public String getRole() {
        return role;
    }
}
