package com.example.aiassistantcoder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

import io.github.rosemoe.sora.widget.CodeEditor;

public class CodeEditorFragment extends Fragment {

    private CodeEditor codeEditor;
    private Project currentProject;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_code_editor, container, false);
        codeEditor = view.findViewById(R.id.code_editor);

        String projectTitle = getActivity().getIntent().getStringExtra("projectTitle");
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentProject = ProjectRepository.getInstance().getProjectByTitle(projectTitle);
        }

        if (currentProject != null && currentProject.getCode() != null) {
            codeEditor.setText(currentProject.getCode());
        }

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentProject != null && codeEditor != null) {
            currentProject.setCode(codeEditor.getText().toString());
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                ProjectRepository.getInstance().saveProjectToFirestore(currentProject, new ProjectRepository.ProjectSaveCallback() {
                    @Override
                    public void onSaved(String projectId) {
                        // Auto-saved, do nothing
                    }

                    @Override
                    public void onError(Exception e) {
                        // Optional: show a toast or log the error
                    }
                });
            }
        }
    }

    public void setCode(String code) {
        if (codeEditor != null) {
            codeEditor.setText(code);
        }
    }
}