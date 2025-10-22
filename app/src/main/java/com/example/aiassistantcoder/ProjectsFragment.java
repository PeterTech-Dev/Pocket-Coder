package com.example.aiassistantcoder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProjectsFragment extends Fragment {

    private List<Project> projectList;
    private ProjectAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_projects, container, false);

        Spinner sortSpinner = view.findViewById(R.id.sort_spinner);
        RecyclerView projectsRecyclerView = view.findViewById(R.id.projects_recycler_view);
        projectsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        projectList = ProjectRepository.getInstance().getProjects();
        adapter = new ProjectAdapter(projectList);
        projectsRecyclerView.setAdapter(adapter);

        String[] sortOptions = {"Name", "Date"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, sortOptions);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedOption = (String) parent.getItemAtPosition(position);
                sortProjects(selectedOption);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void sortProjects(String criteria) {
        if ("Name".equals(criteria)) {
            Collections.sort(projectList, Comparator.comparing(Project::getTitle));
        } else { // Date
            Collections.sort(projectList, (p1, p2) -> p2.getDate().compareTo(p1.getDate()));
        }
        adapter.notifyDataSetChanged();
    }
}