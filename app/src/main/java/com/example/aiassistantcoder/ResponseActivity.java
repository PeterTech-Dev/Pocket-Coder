package com.example.aiassistantcoder;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;

public class ResponseActivity extends AppCompatActivity {

    private String aiCode;
    private String aiLanguage;
    private String aiRuntime;
    private String aiNotes;
    private Project currentProject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_response);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ImageButton closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> finish());

        // Read intent extras from HomeFragment
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

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        // Adapter now accepts AI extras to forward to CodeEditorFragment
        ViewPagerAdapter adapter = new ViewPagerAdapter(this, currentProject,
                aiCode, aiLanguage, aiRuntime, aiNotes);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 1) tab.setText("Code Editor");
                    else tab.setText("Chat");
                }
        ).attach();
    }
}
