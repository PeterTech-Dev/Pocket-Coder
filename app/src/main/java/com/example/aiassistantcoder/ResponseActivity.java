package com.example.aiassistantcoder;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;

public class ResponseActivity extends AppCompatActivity implements CodeEditorFragment.PagerNav {

    private static final String TAG = "ResponseActivity";

    private String aiCode;
    private String aiLanguage;
    private String aiRuntime;
    private String aiNotes;
    private Project currentProject;

    private ViewPager2 viewPager;

    private ChatFragment chatFragment;
    private CodeEditorFragment codeEditorFragment;
    private ConsoleFragment consoleFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_response);

        // debugger
        Log.d(TAG, "onCreate: ResponseActivity launched");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ImageButton closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> {
            // debugger
            Log.d(TAG, "Close button clicked");
            finish();
        });

        // debugger
        Log.d(TAG, "Intent extras dump -> " +
                "ai_code.len=" + (getIntent().getStringExtra("ai_code") != null ? getIntent().getStringExtra("ai_code").length() : 0) +
                ", ai_language=" + getIntent().getStringExtra("ai_language") +
                ", ai_runtime=" + getIntent().getStringExtra("ai_runtime") +
                ", ai_notes=" + getIntent().getStringExtra("ai_notes") +
                ", projectTitle=" + getIntent().getStringExtra("projectTitle") +
                ", query=" + getIntent().getStringExtra("query"));

        aiCode     = getIntent().getStringExtra("ai_code");
        aiLanguage = getIntent().getStringExtra("ai_language");
        aiRuntime  = getIntent().getStringExtra("ai_runtime");
        aiNotes    = getIntent().getStringExtra("ai_notes");

        String projectTitle = getIntent().getStringExtra("projectTitle");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentProject = ProjectRepository.getInstance().getProjectByTitle(projectTitle);
            // debugger
            Log.d(TAG, "Loaded project from repo (logged-in): title=" + projectTitle + ", project=" + currentProject);
        } else {
            String initialQuery = getIntent().getStringExtra("query");
            String initialResponse = getIntent().getStringExtra("response");
            currentProject = new Project(initialQuery);
            currentProject.addMessage(new Message(initialQuery, "user"));
            currentProject.addMessage(new Message(initialResponse, "model"));
            // debugger
            Log.d(TAG, "Created local project (not logged-in): query=" + initialQuery);
        }

        if (currentProject == null) {
            Log.e(TAG, "currentProject is null, finishing");
            Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        chatFragment = new ChatFragment();
        chatFragment.setProject(currentProject); // debugger
        Log.d(TAG, "ChatFragment setProject done");

        codeEditorFragment = new CodeEditorFragment();
        codeEditorFragment.setProject(currentProject);
        Bundle editorArgs = new Bundle();
        editorArgs.putString("ai_code", aiCode);
        editorArgs.putString("ai_language", aiLanguage);
        editorArgs.putString("ai_runtime", aiRuntime);
        editorArgs.putString("ai_notes", aiNotes);
        String j0 = getIntent().getStringExtra("judge0_base_url");
        String live = getIntent().getStringExtra("live_base_url");
        if (j0 != null)   editorArgs.putString("judge0_base_url", j0);
        if (live != null) editorArgs.putString("live_base_url", live);
        codeEditorFragment.setArguments(editorArgs);
        // debugger
        Log.d(TAG, "CodeEditorFragment args set, codeLen=" + (aiCode != null ? aiCode.length() : 0));

        consoleFragment = new ConsoleFragment();

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return chatFragment;
                    case 1: return codeEditorFragment;
                    case 2:
                    default: return consoleFragment;
                }
            }
            @Override public int getItemCount() { return 3; }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) tab.setText("Chat");
            else if (position == 1) tab.setText("Code Editor");
            else tab.setText("Console");
        }).attach();

        // debugger
        Log.d(TAG, "ResponseActivity UI set up, initial page=Chat");
    }

    @Override
    public void goToConsoleTab() {
        // debugger
        Log.d(TAG, "goToConsoleTab called from fragment");
        if (viewPager != null) viewPager.setCurrentItem(2, true);
    }
}
