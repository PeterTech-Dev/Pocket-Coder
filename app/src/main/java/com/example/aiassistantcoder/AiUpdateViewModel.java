package com.example.aiassistantcoder;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.annotation.NonNull;

public class AiUpdateViewModel extends ViewModel {

    public static class CodeUpdate {
        public final String language;
        public final String runtime;
        public final String notes;
        public final String code;
        public final long nonce; // ensures observers fire even if code text repeats

        public CodeUpdate(String language, String runtime, String notes, String code) {
            this.language = language == null ? "" : language;
            this.runtime  = runtime  == null ? "" : runtime;
            this.notes    = notes    == null ? "" : notes;
            this.code     = code     == null ? "" : code;
            this.nonce    = System.nanoTime();
        }
    }

    private final MutableLiveData<CodeUpdate> updates = new MutableLiveData<>();
    private final MutableLiveData<String> editorCode = new MutableLiveData<>("");

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
}
