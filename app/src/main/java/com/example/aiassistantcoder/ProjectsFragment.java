package com.example.aiassistantcoder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProjectsFragment extends Fragment implements ProjectRepository.ProjectsListener {

    private ProjectAdapter adapter;
    private View projectsLayoutContent;
    private TextView loginPromptText;
    private RecyclerView projectsRecyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_projects, container, false);

        projectsLayoutContent = view.findViewById(R.id.projects_layout_content);
        loginPromptText = view.findViewById(R.id.login_prompt_text);
        projectsRecyclerView = view.findViewById(R.id.projects_recycler_view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupProjectsList(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        ProjectRepository.getInstance().addListener(this);
        updateProjectListView();
    }

    @Override
    public void onPause() {
        super.onPause();
        ProjectRepository.getInstance().removeListener(this);
    }

    @Override
    public void onChanged(List<Project> projects) {
        adapter = new ProjectAdapter(projects);
        projectsRecyclerView.setAdapter(adapter);
    }

    private void updateProjectListView() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            loginPromptText.setVisibility(View.GONE);
            projectsLayoutContent.setVisibility(View.VISIBLE);
            ProjectRepository.getInstance().startRealtimeSync();
        } else {
            loginPromptText.setVisibility(View.VISIBLE);
            projectsLayoutContent.setVisibility(View.GONE);
            ProjectRepository.getInstance().stopRealtimeSync();
        }
    }

    private void setupProjectsList(View view) {
        projectsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ProjectAdapter(ProjectRepository.getInstance().getProjects());
        projectsRecyclerView.setAdapter(adapter);

        Spinner sortSpinner = view.findViewById(R.id.sort_spinner);
        String[] sortOptions = {"Name", "Date"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, sortOptions);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sortProjects((String) parent.getItemAtPosition(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void sortProjects(String criteria) {
        List<Project> currentProjects = ProjectRepository.getInstance().getProjects();
        if ("Name".equals(criteria)) {
            Collections.sort(currentProjects, Comparator.comparing(Project::getTitle));
        } else { // Date
            Collections.sort(currentProjects, (p1, p2) -> p2.getDate().compareTo(p1.getDate()));
        }
        adapter.notifyDataSetChanged();
    }
}