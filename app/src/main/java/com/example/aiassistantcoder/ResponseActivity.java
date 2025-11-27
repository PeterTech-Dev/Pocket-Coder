package com.example.aiassistantcoder;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.aiassistantcoder.ui.SnackBarApp;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;

public class ResponseActivity extends AppCompatActivity implements CodeEditorFragment.PagerNav {

    private static final String TAG = "ResponseActivity";

    private String aiCode;
    private String aiLanguage;
    private String aiRuntime;
    private String aiNotes;
    private String aiProjectJson;
    private Project currentProject;

    private ViewPager2 viewPager;

    private ChatFragment chatFragment;
    private CodeEditorFragment codeEditorFragment;
    private ConsoleFragment consoleFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_response);

        Log.d(TAG, "onCreate: ResponseActivity launched");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitleTextColor(getColor(R.color.colorOnBackground));

        ImageButton closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "Close button clicked");
            finish();
        });

        // read intent extras
        aiCode = getIntent().getStringExtra("ai_code");
        aiLanguage = getIntent().getStringExtra("ai_language");
        aiRuntime = getIntent().getStringExtra("ai_runtime");
        aiNotes = getIntent().getStringExtra("ai_notes");
        aiProjectJson = getIntent().getStringExtra("ai_project_json");

        Log.d(TAG, "Intent extras dump -> " +
                "ai_code.len=" + (aiCode != null ? aiCode.length() : 0) +
                ", ai_language=" + aiLanguage +
                ", ai_runtime=" + aiRuntime +
                ", ai_notes=" + aiNotes +
                ", projectTitle=" + getIntent().getStringExtra("projectTitle") +
                ", query=" + getIntent().getStringExtra("query") +
                ", hasJson=" + (aiProjectJson != null));

        String projectTitle = getIntent().getStringExtra("projectTitle");

        // load/create project
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentProject = ProjectRepository.getInstance().getProjectByTitle(projectTitle);
            Log.d(TAG, "Loaded project from repo (logged-in): title=" + projectTitle + ", project=" + currentProject);
        } else {
            String initialQuery = getIntent().getStringExtra("query");
            String initialResponse = getIntent().getStringExtra("response");
            currentProject = new Project(initialQuery);
            currentProject.addMessage(new Message(initialQuery, "user"));
            currentProject.addMessage(new Message(initialResponse, "model"));
            Log.d(TAG, "Created local project (not logged-in): query=" + initialQuery);
        }

        if (currentProject == null) {
            Log.e(TAG, "currentProject is null, finishing");
            View root = findViewById(android.R.id.content);
            SnackBarApp.INSTANCE.show(
                    root,
                    "Project not found",
                    SnackBarApp.Type.ERROR
            );
            finish();
            return;
        }

        viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        // Chat tab
        chatFragment = new ChatFragment();
        chatFragment.setProject(currentProject);

        // Code editor tab
        codeEditorFragment = new CodeEditorFragment();
        codeEditorFragment.setProject(currentProject);

        Bundle editorArgs = new Bundle();
        editorArgs.putString("ai_code", aiCode);
        editorArgs.putString("ai_language", aiLanguage);
        editorArgs.putString("ai_runtime", aiRuntime);
        editorArgs.putString("ai_notes", aiNotes);
        editorArgs.putString("ai_project_json", aiProjectJson);

        String live = getIntent().getStringExtra("live_base_url");
        if (live != null) editorArgs.putString("live_base_url", live);

        codeEditorFragment.setArguments(editorArgs);

        Log.d(TAG, "CodeEditorFragment args set, codeLen=" + (aiCode != null ? aiCode.length() : 0)
                + ", hasJson=" + (aiProjectJson != null));

        // Console tab
        consoleFragment = new ConsoleFragment();

        // ViewPager adapter
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return chatFragment;
                    case 1:
                        return codeEditorFragment;
                    case 2:
                    default:
                        return consoleFragment;
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) tab.setText("Chat");
            else if (position == 1) tab.setText("Code Editor");
            else tab.setText("Console");
        }).attach();

        Log.d(TAG, "ResponseActivity UI set up, initial page=Chat");
    }

    // from editor → console
    @Override
    public void goToConsoleTab() {
        if (viewPager != null) viewPager.setCurrentItem(2, true);
    }

    // optional: from editor → chat
    public void goToChatTab() {
        if (viewPager != null) viewPager.setCurrentItem(0, true);
    }
}
