package com.example.aiassistantcoder;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public class AiUpdateViewModel extends ViewModel {

    // ===== existing single-code update =====
    public static class CodeUpdate {
        public final String language;
        public final String runtime;
        public final String notes;
        public final String code;
        public final List<OpenFile> files;  // always non-null
        public final long nonce;

        public CodeUpdate(String language, String runtime, String notes, String code) {
            this.language = language == null ? "" : language;
            this.runtime = runtime == null ? "" : runtime;
            this.notes = notes == null ? "" : notes;
            this.code = code == null ? "" : code;
            this.files = new ArrayList<>();   // <-- fix
            this.nonce = System.nanoTime();
        }
    }

    // ===== NEW: multi-file project support =====
    public static class ProjectFile {
        public final String path;
        public final String filename;
        public final String summary;
        public final String content;

        public ProjectFile(String path,
                           String filename,
                           String summary,
                           String content) {
            this.path = path == null ? "" : path;
            this.filename = filename == null ? "" : filename;
            this.summary = summary == null ? "" : summary;
            this.content = content == null ? "" : content;
        }
    }

    public static class ProjectUpdate {
        public final String language;
        public final String runtime;
        public final String entrypoint;
        public final List<ProjectFile> files;
        public final String notes;

        public ProjectUpdate(String language,
                             String runtime,
                             String entrypoint,
                             List<ProjectFile> files,
                             String notes) {
            this.language = language == null ? "" : language;
            this.runtime = runtime == null ? "" : runtime;
            this.entrypoint = entrypoint == null ? "" : entrypoint;
            this.files = files == null ? new ArrayList<>() : files;
            this.notes = notes == null ? "" : notes;
        }
    }

    // existing
    private final MutableLiveData<CodeUpdate> updates = new MutableLiveData<>();
    private final MutableLiveData<String> editorCode = new MutableLiveData<>("");

    // NEW
    private final MutableLiveData<ProjectUpdate> projectUpdates = new MutableLiveData<>();

    // --- existing methods ---
    public LiveData<CodeUpdate> getUpdates() {
        return updates;
    }

    public void publish(String language, String runtime, String notes, String code) {
        updates.setValue(new CodeUpdate(language, runtime, notes, code));
    }

    public void publishEditorCode(@NonNull String code) {
        editorCode.postValue(code);
    }

    public LiveData<String> getEditorCode() {
        return editorCode;
    }

    // --- NEW methods for project ---
    public LiveData<ProjectUpdate> getProjectUpdates() {
        return projectUpdates;
    }

    public void publishProject(@NonNull ProjectUpdate update) {
        projectUpdates.setValue(update);
    }
}
