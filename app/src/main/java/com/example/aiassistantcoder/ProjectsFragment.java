package com.example.aiassistantcoder;

import android.app.AlertDialog;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aiassistantcoder.ui.SnackBarApp;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProjectsFragment extends Fragment implements ProjectRepository.ProjectsListener {

    private ProjectAdapter adapter;
    private View projectsLayoutContent;
    private TextView loginPromptText;
    private RecyclerView projectsRecyclerView;
    private FloatingActionButton fabNewProject;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_projects, container, false);

        projectsLayoutContent = view.findViewById(R.id.projects_layout_content);
        loginPromptText = view.findViewById(R.id.login_prompt_text);
        projectsRecyclerView = view.findViewById(R.id.projects_recycler_view);
        fabNewProject = view.findViewById(R.id.fab_new_project);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupProjectsList(view);
        setupFab();
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
        // refresh adapter when repo notifies
        adapter = new ProjectAdapter(projects);
        projectsRecyclerView.setAdapter(adapter);
        attachSwipeHelper(); // make sure swipe works when list refreshes
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

        // attach swipe helper once initial adapter is set
        attachSwipeHelper();

        Spinner sortSpinner = view.findViewById(R.id.sort_spinner);
        String[] sortOptions = {"Name", "Date"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                sortOptions
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view,
                                       int position,
                                       long id) {
                sortProjects((String) parent.getItemAtPosition(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // Floating action button: create new project
    private void setupFab() {
        if (fabNewProject == null) return;

        fabNewProject.setOnClickListener(v -> {
            final EditText input = new EditText(requireContext());
            input.setHint("Project name");
            input.setInputType(InputType.TYPE_CLASS_TEXT);

            new AlertDialog.Builder(requireContext())
                    .setTitle("New project")
                    .setView(input)
                    .setPositiveButton("Create", (d, which) -> {
                        String title = input.getText().toString().trim();
                        if (title.isEmpty()) return;

                        ProjectRepository.getInstance()
                                .createEmptyProject(title, new ProjectRepository.ProjectSaveCallback() {
                                    @Override
                                    public void onSaved(String projectId) {

                                        View root = requireActivity().findViewById(android.R.id.content);
                                        SnackBarApp.INSTANCE.show(
                                                root,
                                                "Project created",
                                                SnackBarApp.Type.SUCCESS
                                        );
                                    }

                                    @Override
                                    public void onError(Exception e) {

                                        View root = requireActivity().findViewById(android.R.id.content);
                                        SnackBarApp.INSTANCE.show(
                                                root,
                                                "Error: " + e.getMessage(),
                                                SnackBarApp.Type.ERROR
                                        );
                                    }
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void sortProjects(String criteria) {
        List<Project> currentProjects = ProjectRepository.getInstance().getProjects();
        if ("Name".equals(criteria)) {
            Collections.sort(
                    currentProjects,
                    Comparator.comparing(Project::getTitle, String.CASE_INSENSITIVE_ORDER)
            );
        } else { // Date
            Collections.sort(
                    currentProjects,
                    (p1, p2) -> p2.getDate().compareTo(p1.getDate())
            );
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Attach swipe-to-edit/delete behavior to the RecyclerView.
     * Swipe RIGHT  -> delete (icon on left)
     * Swipe LEFT   -> rename/edit (icon on right)
     */
    private void attachSwipeHelper() {
        ItemTouchHelper.SimpleCallback callback =
                new ItemTouchHelper.SimpleCallback(
                        0,
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
                ) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
                                         int direction) {
                        int position = viewHolder.getBindingAdapterPosition();
                        if (position == RecyclerView.NO_POSITION) return;

                        Project project =
                                ProjectRepository.getInstance().getProjects().get(position);

                        if (direction == ItemTouchHelper.RIGHT) {
                            // Swipe RIGHT -> delete
                            showDeleteProjectDialog(project, position);
                        } else if (direction == ItemTouchHelper.LEFT) {
                            // Swipe LEFT -> rename/edit
                            showRenameProjectDialog(project, position);
                        }
                    }

                    @Override
                    public void onChildDraw(@NonNull Canvas c,
                                            @NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            float dX,
                                            float dY,
                                            int actionState,
                                            boolean isCurrentlyActive) {

                        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE
                                && viewHolder instanceof ProjectAdapter.ProjectViewHolder holder) {

                            View foreground = holder.foreground;
                            View bgDelete = holder.bgDelete;
                            View bgEdit = holder.bgEdit;

                            // Limit how far the row can slide
                            float maxSwipe = viewHolder.itemView.getWidth() * 0.35f;
                            float clampedDx = Math.max(-maxSwipe, Math.min(dX, maxSwipe));

                            // How wide the overlay should be (0..itemWidth * 0.35)
                            int itemWidth = viewHolder.itemView.getWidth();
                            int overlayWidth = (int) Math.min(Math.abs(clampedDx), itemWidth);

                            // Reset backgrounds before applying new width
                            bgDelete.getLayoutParams().width = 0;
                            bgEdit.getLayoutParams().width = 0;
                            bgDelete.setVisibility(View.GONE);
                            bgEdit.setVisibility(View.GONE);

                            if (clampedDx > 0) {
                                // Swipe RIGHT → red overlay from the left
                                bgDelete.getLayoutParams().width = overlayWidth;
                                bgDelete.setVisibility(View.VISIBLE);
                                bgDelete.requestLayout();
                            } else if (clampedDx < 0) {
                                // Swipe LEFT → blue overlay from the right
                                bgEdit.getLayoutParams().width = overlayWidth;
                                bgEdit.setVisibility(View.VISIBLE);
                                bgEdit.requestLayout();
                            }

                            // Move only the foreground row
                            foreground.setTranslationX(clampedDx);

                        } else {
                            super.onChildDraw(
                                    c,
                                    recyclerView,
                                    viewHolder,
                                    dX,
                                    dY,
                                    actionState,
                                    isCurrentlyActive
                            );
                        }
                    }


                    @Override
                    public void clearView(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder) {
                        super.clearView(recyclerView, viewHolder);
                        // Reset translation when gesture ends
                        if (viewHolder instanceof ProjectAdapter.ProjectViewHolder holder) {
                            holder.foreground.setTranslationX(0f);
                        }
                    }
                };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(projectsRecyclerView);
    }

    private void showDeleteProjectDialog(Project project, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete project")
                .setMessage("Are you sure you want to delete \"" + project.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Repository expects a Project, not an ID
                    ProjectRepository.getInstance().deleteProject(project);

                    // rely on realtime listener to refresh,
                    // but remove immediately for instant feedback
                    adapter.notifyItemRemoved(position);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // reset row position if user cancels
                    adapter.notifyItemChanged(position);
                })
                .show();
    }

    private void showRenameProjectDialog(Project project, int position) {
        final EditText input = new EditText(requireContext());
        input.setHint("New project name");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(project.getTitle());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireContext())
                .setTitle("Rename Project")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        SnackBarApp.INSTANCE.show(
                                requireActivity().findViewById(android.R.id.content),
                                "Title cannot be empty",
                                SnackBarApp.Type.WARNING
                        );
                        adapter.notifyItemChanged(position); // snap row back
                        return;
                    }

                    // update local object
                    project.setTitle(newTitle);
                    adapter.notifyItemChanged(position);

                    // persist to Firestore (same as before in adapter)
                    ProjectRepository.getInstance()
                            .saveProjectToFirestore(project, new ProjectRepository.ProjectSaveCallback() {
                                @Override
                                public void onSaved(String projectId) {
                                    SnackBarApp.INSTANCE.show(
                                            requireActivity().findViewById(android.R.id.content),
                                            "Project Renamed",
                                            SnackBarApp.Type.SUCCESS
                                    );

                                }

                                @Override
                                public void onError(Exception e) {
                                    SnackBarApp.INSTANCE.show(
                                            requireActivity().findViewById(android.R.id.content),
                                            "Rename failed: " + e.getMessage(),
                                            SnackBarApp.Type.ERROR
                                    );

                                }
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // snap row back with no change
                    adapter.notifyItemChanged(position);
                })
                .show();
    }
}
