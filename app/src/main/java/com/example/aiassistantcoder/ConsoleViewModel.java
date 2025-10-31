package com.example.aiassistantcoder;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ConsoleViewModel extends ViewModel {
    private final MutableLiveData<String> logs = new MutableLiveData<>("");
    private final MutableLiveData<String> previewUrl = new MutableLiveData<>(null);

    // Outgoing commands entered in console
    private final MutableLiveData<String> commandOut = new MutableLiveData<>();

    public LiveData<String> getLogs() { return logs; }
    public LiveData<String> getPreviewUrl() { return previewUrl; }
    public LiveData<String> getCommandOut() { return commandOut; }

    public synchronized void append(String chunk) {
        String prev = logs.getValue();
        logs.postValue((prev == null ? "" : prev) + (chunk == null ? "" : chunk));
    }

    public void setPreviewUrl(String url) {
        previewUrl.postValue(url);
    }

    /** Emit a command typed by the user */
    public void sendCommand(String cmd) {
        if (cmd != null && !cmd.trim().isEmpty()) {
            commandOut.postValue(cmd);
        }
    }

    // ✅ Add this method to allow /clear command to work
    public void clearConsole() {
        logs.postValue(""); // clears the console logs
    }
}
