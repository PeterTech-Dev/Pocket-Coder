package com.example.aiassistantcoder;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {

    private final Project project;
    private final String aiCode;
    private final String aiLanguage;
    private final String aiRuntime;
    private final String aiNotes;

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                            Project project,
                            String aiCode,
                            String aiLanguage,
                            String aiRuntime,
                            String aiNotes) {
        super(fragmentActivity);
        this.project = project;
        this.aiCode = aiCode;
        this.aiLanguage = aiLanguage;
        this.aiRuntime = aiRuntime;
        this.aiNotes = aiNotes;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 1) {
            // Code Editor tab
            CodeEditorFragment codeEditorFragment = new CodeEditorFragment();
            codeEditorFragment.setProject(project);

            // Pass AI data to fragment
            Bundle args = new Bundle();
            args.putString("ai_code", aiCode);
            args.putString("ai_language", aiLanguage);
            args.putString("ai_runtime", aiRuntime);
            args.putString("ai_notes", aiNotes);
            codeEditorFragment.setArguments(args);

            return codeEditorFragment;
        } else {
            // Chat tab
            ChatFragment chatFragment = new ChatFragment();
            chatFragment.setProject(project);
            return chatFragment;
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Chat + Code Editor
    }
}
