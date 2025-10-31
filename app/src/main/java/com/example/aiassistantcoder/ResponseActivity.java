package com.example.aiassistantcoder;

import android.os.Bundle;
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

    private String aiCode;
    private String aiLanguage;
    private String aiRuntime;
    private String aiNotes;
    private Project currentProject;

    private ViewPager2 viewPager;

    // Keep refs so we can pass data / interact if needed
    private ChatFragment chatFragment;
    private CodeEditorFragment codeEditorFragment;
    private ConsoleFragment consoleFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_response);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ImageButton closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> finish());

        // Intent extras
        aiCode     = getIntent().getStringExtra("ai_code");
        aiLanguage = getIntent().getStringExtra("ai_language");
        aiRuntime  = getIntent().getStringExtra("ai_runtime");
        aiNotes    = getIntent().getStringExtra("ai_notes");

        String projectTitle = getIntent().getStringExtra("projectTitle");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentProject = ProjectRepository.getInstance().getProjectByTitle(projectTitle);
        } else {
            String initialQuery = getIntent().getStringExtra("query");
            String initialResponse = getIntent().getStringExtra("response");
            currentProject = new Project(initialQuery);
            currentProject.addMessage(new Message(initialQuery, "user"));
            currentProject.addMessage(new Message(initialResponse, "model"));
        }

        if (currentProject == null) {
            Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        // instantiate fragments once so we can set fields on them
        chatFragment = new ChatFragment();
        chatFragment.setProject(currentProject); // <-- IMPORTANT so ChatFragment can send/receive

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

        consoleFragment = new ConsoleFragment();

        // 3 tabs: Chat, Code Editor, Console
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
    }

    // Allow fragments to switch to the Console tab
    @Override
    public void goToConsoleTab() {
        if (viewPager != null) viewPager.setCurrentItem(2, true);
    }
}
