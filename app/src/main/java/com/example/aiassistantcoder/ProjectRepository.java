package com.example.aiassistantcoder;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectRepository {

    private static final ProjectRepository instance = new ProjectRepository();

    private List<Project> projects = new ArrayList<>();

    private ProjectRepository() {
    }

    public static ProjectRepository getInstance() {
        return instance;
    }

    public List<Project> getProjects() {
        return projects;
    }

    public void addProject(Project project) {
        projects.add(0, project);
    }

    public Project getProjectByTitle(String title) {
        for (Project project : projects) {
            if (project.getTitle().equals(title)) {
                return project;
            }
        }
        return null;
    }

    public void clearProjects() {
        projects.clear();
    }
}
