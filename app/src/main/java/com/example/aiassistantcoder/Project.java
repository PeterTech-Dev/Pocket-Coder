package com.example.aiassistantcoder;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Project implements Serializable {
    private final String title;
    private final String date;
    private String codeType = "Unknown"; // Default value
    private final List<Message> conversationHistory;

    public Project(String title) {
        this.title = title;
        this.date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        this.conversationHistory = new ArrayList<>();
    }

    public String getTitle() {
        return title;
    }

    public String getDate() {
        return date;
    }

    public String getCodeType() {
        return codeType;
    }

    public void setCodeType(String codeType) {
        this.codeType = codeType;
    }

    public List<Message> getConversationHistory() {
        return conversationHistory;
    }

    public void addMessage(Message message) {
        this.conversationHistory.add(message);
    }
}
