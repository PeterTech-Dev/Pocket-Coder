package com.example.aiassistantcoder;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProjectRepository {
    private static final ProjectRepository INSTANCE = new ProjectRepository();
    public static ProjectRepository getInstance() { return INSTANCE; }

    private final List<Project> projects = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface ProjectsListener { void onChanged(List<Project> projects); }
    private final List<ProjectsListener> listeners = new ArrayList<>();
    public void addListener(ProjectsListener l){ listeners.add(l); }
    public void removeListener(ProjectsListener l){ listeners.remove(l); }
    private void notifyListeners(){ for (var l: listeners) l.onChanged(Collections.unmodifiableList(projects)); }

    public List<Project> getProjects() { return projects; }

    /** Local add (optional for anonymous use) */
    public void addProject(Project p) {
        projects.add(p);
        notifyListeners();
    }

    public Project getProjectByTitle(String title) {
        for (Project p : projects) {
            if (p.getTitle() != null && p.getTitle().equalsIgnoreCase(title)) {
                return p;
            }
        }
        return null;
    }

    /** Save to Firestore under users/{uid}/projects and keep repo in sync */
    public interface ProjectSaveCallback { void onSaved(String projectId); void onError(Exception e); }

    public void saveProjectToFirestore(Project project, ProjectSaveCallback cb) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { cb.onError(new IllegalStateException("Not signed in")); return; }

        Map<String, Object> data = new HashMap<>();
        data.put("title", project.getTitle());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("tags", project.getTags());
        data.put("code", project.getCode());

        List<Map<String, Object>> msgs = new ArrayList<>();
        for (Message m : project.getMessages()) {
            Map<String, Object> mm = new HashMap<>();
            mm.put("role", m.getRole());
            mm.put("text", m.getText());
            mm.put("imageUri", m.getImageUri());
            msgs.add(mm);
        }
        data.put("messages", msgs);

        CollectionReference projectsCollection = db.collection("users").document(u.getUid()).collection("projects");

        if (project.getId() != null) {
            projectsCollection.document(project.getId()).set(data)
                    .addOnSuccessListener(aVoid -> cb.onSaved(project.getId()))
                    .addOnFailureListener(cb::onError);
        } else {
            projectsCollection.add(data)
                    .addOnSuccessListener(ref -> {
                        project.setId(ref.getId());
                        project.setCreatedAt(new Date()); // temp; server time will overwrite on next snapshot
                        projects.add(0, project);
                        notifyListeners();
                        cb.onSaved(ref.getId());
                    })
                    .addOnFailureListener(cb::onError);
        }
    }

    public void deleteProject(Project project) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && project.getId() != null) {
            db.collection("users").document(user.getUid()).collection("projects").document(project.getId()).delete();
        }
    }

    /** Real-time list sync for ProjectsFragment */
    private ListenerRegistration liveReg;

    public void startRealtimeSync() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        if (liveReg != null) liveReg.remove();

        liveReg = db.collection("users").document(u.getUid())
                .collection("projects")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, err) -> {
                    if (err != null || snap == null) return;
                    projects.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Project p = new Project();
                        p.setId(d.getId());
                        p.setTitle(d.getString("title"));
                        Timestamp ts = d.getTimestamp("createdAt");
                        p.setCreatedAt(ts != null ? ts.toDate() : new Date());
                        p.setCode(d.getString("code"));

                        List<Map<String, Object>> msgsData = (List<Map<String, Object>>) d.get("messages");
                        if (msgsData != null) {
                            for (Map<String, Object> msgMap : msgsData) {
                                Message msg = new Message();
                                msg.setText((String) msgMap.get("text"));
                                msg.setRole((String) msgMap.get("role"));
                                msg.setImageUri((String) msgMap.get("imageUri"));
                                p.addMessage(msg);
                            }
                        }
                        projects.add(p);
                    }
                    notifyListeners();
                });
    }

    public void stopRealtimeSync() {
        if (liveReg != null) { liveReg.remove(); liveReg = null; }
    }

    public void clearProjects() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).collection("projects")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            doc.getReference().delete();
                        }
                    });
        }
        projects.clear();
        notifyListeners();
    }
}