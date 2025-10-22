package com.example.aiassistantcoder;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ProjectsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects);

        RecyclerView projectsRecyclerView = findViewById(R.id.projects_recycler_view);
        projectsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Project> projectList = new ArrayList<>();

        ProjectAdapter adapter = new ProjectAdapter(projectList);
        projectsRecyclerView.setAdapter(adapter);
    }
}