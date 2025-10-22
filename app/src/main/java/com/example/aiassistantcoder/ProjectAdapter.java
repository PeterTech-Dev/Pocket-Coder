package com.example.aiassistantcoder;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

    private List<Project> projectList;

    public ProjectAdapter(List<Project> projectList) {
        this.projectList = projectList;
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.project_item, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        Project project = projectList.get(position);
        holder.projectTitle.setText(project.getTitle());
        holder.projectDate.setText(project.getDate());
        holder.projectCodeType.setText(project.getCodeType());

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), ResponseActivity.class);
            intent.putExtra("projectTitle", project.getTitle());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return projectList.size();
    }

    public static class ProjectViewHolder extends RecyclerView.ViewHolder {
        TextView projectTitle, projectDate, projectCodeType;

        public ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            projectTitle = itemView.findViewById(R.id.project_title);
            projectDate = itemView.findViewById(R.id.project_date);
            projectCodeType = itemView.findViewById(R.id.project_code_type);
        }
    }
}