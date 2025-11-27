package com.example.aiassistantcoder;

public class ProjectFile {
    public String path;    // e.g. "src/main.py" or just "main.py"
    public String content; // whole file text

    // Firestore needs a no-arg constructor
    public ProjectFile() {
    }

    public ProjectFile(String path, String content) {
        this.path = path;
        this.content = content;
    }
}
