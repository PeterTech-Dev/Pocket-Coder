package com.example.aiassistantcoder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aiassistantcoder.ui.SnackBarApp;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;

import org.eclipse.tm4e.core.registry.IThemeSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class CodeEditorFragment extends Fragment {

    // ---- UI ----
    private CodeEditor codeEditor;
    private FloatingActionButton btnRun;
    private ProgressBar progress;
    private boolean editorDarkTheme = true;


    // 2-finger swipe / UI
    private float twoFingerStartX = 0f;
    private boolean twoFingerTracking = false;
    private static final float TWO_FINGER_SWIPE_THRESHOLD = 120f; // px

    // file / tabs UI
    private View filesPanel;
    private LinearLayout filesListContainer;
    private TabLayout tabLayout;
    private ImageButton btnAddFile;
    private ImageButton btnToggleFilesPanel;
    private ImageButton btnCloseTab;

    // file model
    private final List<OpenFile> availableFiles = new ArrayList<>();
    private final Map<String, OpenFile> openTabs = new LinkedHashMap<>();
    private final Map<String, Boolean> aiManagedFiles = new HashMap<>();
    private FilesAdapter filesAdapter;

    // Diff bottom sheet
    private BottomSheetDialog diffDialog;
    private DiffAdapter diffAdapter;
    private final List<DiffLine> currentDiff = new ArrayList<>();
    private final List<PendingFileDiff> pendingFileDiffs = new ArrayList<>();
    private @Nullable String currentDiffFileId = null;
    private @Nullable TextView diffHeaderView = null;

    private static final class PendingFileDiff {
        final String fileId;
        final String newContent;
        final List<DiffLine> diff;

        PendingFileDiff(String fileId, String newContent, List<DiffLine> diff) {
            this.fileId = fileId;
            this.newContent = newContent;
            this.diff = diff;
        }
    }

    private @Nullable Runnable onAcceptAction;
    private @Nullable String queuedNewCode;

    // ---- ViewModels / Buses ----
    private ConsoleViewModel consoleVM;
    private AiUpdateViewModel aiBus;
    private @Nullable SubscriptionReceipt<ContentChangeEvent> contentSub = null;

    private @Nullable OpenFile getCurrentOpenFile() {
        if (tabLayout == null) return null;
        int idx = tabLayout.getSelectedTabPosition();
        if (idx < 0) return null;
        TabLayout.Tab tab = tabLayout.getTabAt(idx);
        if (tab == null) return null;
        Object tag = tab.getTag();
        return (tag instanceof OpenFile) ? (OpenFile) tag : null;
    }

    // ---- Exec / handlers ----
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // ---- Project / persistence ----
    private Project currentProject;
    private static final String SP_FILE = "editor_cache";

    // Debounced autosave
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private static final long SAVE_DEBOUNCE_MS = 800L;
    private final Runnable saveRunnable = () -> {
        if (getContext() == null) return;
        String src = getCode();
        if (src == null) src = "";

        if (currentProject != null) {
            currentProject.setCode(src);
            currentProject.setFiles(buildProjectFilesFromEditor());
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                ProjectRepository.getInstance().saveProjectToFirestore(
                        currentProject,
                        new ProjectRepository.ProjectSaveCallback() {
                            @Override
                            public void onSaved(String projectId) {
                                printToConsole("(saved) ✔\n");
                            }

                            @Override
                            public void onError(Exception e) {
                                printToConsole("Save error: " + e.getMessage() + "\n");
                            }
                        });
            }
        }

        // Always cache locally too
        String key = cacheKeyForCurrentProject();
        requireContext().getSharedPreferences(SP_FILE, 0)
                .edit()
                .putString(key, src)
                .apply();
    };

    // AI hints (optional)
    private String aiLang, aiRuntime, aiRunnerHint;
    private String aiEntrypoint;

    // Live runner
    private String liveBaseUrl = "https://pocketcoder-backend.onrender.com";
    private WebSocket liveSocket;
    private String liveSessionId;
    private boolean liveConnecting = false;
    private OkHttpClient ok;
    private boolean showDiffs = false;

    // Backend selection
    private enum Backend {LIVE, HTML}

    private Backend currentBackend = Backend.LIVE;

    // Back-compat
    private String pendingCode;

    // Live manager
    private LiveRunManager liveRunManager;

    // For asking the Activity to switch tabs to Console
    public interface PagerNav {
        void goToConsoleTab();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_code_editor, container, false);

        codeEditor = v.findViewById(R.id.code_editor);
        // inside onCreateView, AFTER codeEditor = v.findViewById(...)
        codeEditor.setOnTouchListener((view, event) -> {
            // 1) always tell parent (ViewPager2) not to steal this touch
            ViewParent parent = view.getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
            }

            // 2) handle 2-finger swipe for switching tabs
            int action = event.getActionMasked();

            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        twoFingerTracking = true;
                        twoFingerStartX = (event.getX(0) + event.getX(1)) / 2f;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (twoFingerTracking && event.getPointerCount() == 2) {
                        float curX = (event.getX(0) + event.getX(1)) / 2f;
                        float dx = curX - twoFingerStartX;

                        if (Math.abs(dx) > TWO_FINGER_SWIPE_THRESHOLD) {
                            if (getActivity() instanceof PagerNav nav) {
                                if (dx < 0) {
                                    // 2-finger swipe LEFT → console
                                    nav.goToConsoleTab();
                                } else {
                                    // 2-finger swipe RIGHT → chat (only your activity has this)
                                    if (getActivity() instanceof ResponseActivity) {
                                        ((ResponseActivity) getActivity()).goToChatTab();
                                    }
                                }
                            }
                            twoFingerTracking = false;
                        }
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    twoFingerTracking = false;
                    view.performClick();
                    break;
            }

            // return false so the editor still scrolls and you can select text
            return false;
        });

        btnRun = v.findViewById(R.id.btn_run);
        progress = v.findViewById(R.id.progress);
        initTextMateIfNeeded();
        applyTextMateLanguageFromAi();

        tabLayout = v.findViewById(R.id.tab_layout);
        filesPanel = v.findViewById(R.id.files_panel);
        filesListContainer = v.findViewById(R.id.files_list_container);

        btnAddFile = v.findViewById(R.id.btn_add_file);
        btnToggleFilesPanel = v.findViewById(R.id.btn_toggle_files_panel);
        btnCloseTab = v.findViewById(R.id.btn_close_tab);

        ok = new OkHttpClient();
        consoleVM = new ViewModelProvider(requireActivity()).get(ConsoleViewModel.class);

        if (filesListContainer != null) {
            renderFilesList(filesListContainer);
        }

        if (btnToggleFilesPanel != null) {
            btnToggleFilesPanel.setOnClickListener(view -> {
                if (filesPanel != null) {
                    if (filesPanel.getVisibility() == View.VISIBLE) hideFilesPanel();
                    else showFilesPanel();
                }
            });
        }

        if (btnAddFile != null) {
            btnAddFile.setOnClickListener(view -> showCreateItemChooser(null)); // root
        }

        if (btnCloseTab != null) {
            btnCloseTab.setOnClickListener(view -> closeCurrentTab());
        }

        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getTag() instanceof OpenFile f) {
                        if (codeEditor != null) codeEditor.setText(f.content);
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                    if (tab.getTag() instanceof OpenFile f) {
                        f.content = getCode();
                    }
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });
        }

        // Echo & route console commands
        consoleVM.getCommandOut().observe(getViewLifecycleOwner(), cmd -> {
            if (cmd == null) return;

            String trimmed = cmd.trim();

            if ("/restart".equalsIgnoreCase(trimmed)) {
                consoleVM.append("(live) restarting…\n");
                exec.execute(() -> {
                    stopLiveSession("user-restart");
                    startLiveSessionForCurrentCode();
                });
            } else {
                if (liveSocket != null) {
                    sendToLive(cmd + "\n");
                } else {
                    consoleVM.append("(live) not connected, starting session…\n");
                    exec.execute(() -> {
                        stopLiveSession("console-input");
                        startLiveSessionForCurrentCode();
                    });
                }
            }

            FragmentActivity a = requireActivity();
            if (a instanceof PagerNav) {
                ((PagerNav) a).goToConsoleTab();
            }
        });

        // Observe AI updates from ChatFragment
        aiBus = new ViewModelProvider(requireActivity()).get(AiUpdateViewModel.class);
        aiBus.getUpdates().observe(getViewLifecycleOwner(), update -> {
            if (update == null) return;

            aiLang = update.language;
            aiRuntime = update.runtime;
            aiRunnerHint = update.notes;
            applyTextMateLanguageFromAi();

            String meta = "";
            if (aiLang != null && !aiLang.isEmpty()) meta += aiLang;
            if (aiRuntime != null && !aiRuntime.isEmpty())
                meta += (meta.isEmpty() ? "" : " ") + "(" + aiRuntime + ")";
            if (!meta.isEmpty()) printToConsole("Detected: " + meta + "\n");
            if (aiRunnerHint != null && !aiRunnerHint.isEmpty())
                printToConsole("// Notes: " + aiRunnerHint + "\n");

            boolean showDiff = Prefs.showDiffs(requireContext());
            applyAiCode(update.code, showDiff);
        });

        aiBus.getProjectUpdates().observe(getViewLifecycleOwner(), update -> {
            if (update == null) return;
            if (update.files == null || update.files.isEmpty()) return;

            List<String> incomingIds = new ArrayList<>();

            for (AiUpdateViewModel.ProjectFile pf : update.files) {
                String displayName;
                if (pf.path != null && !pf.path.isEmpty() && !pf.path.equals(".")) {
                    displayName = pf.path + "/" + pf.filename;
                } else {
                    displayName = pf.filename;
                }
                incomingIds.add(displayName);

                if (alreadyHasFile(displayName)) {
                    if (showDiffs) {
                        String oldContent = getFileContentById(displayName);
                        String newContent = pf.content != null ? pf.content : "";

                        if (oldContent.equals(newContent)) {
                            updateOpenFileContent(displayName, newContent);
                        } else {
                            List<DiffLine> diff = DiffUtilLite.diffLines(oldContent, newContent);
                            if (diffDialog != null && diffDialog.isShowing()) {
                                pendingFileDiffs.add(new PendingFileDiff(displayName, newContent, diff));
                            } else {
                                showDiffBottomSheet(displayName, diff,
                                        () -> updateOpenFileContent(displayName, newContent));
                            }
                        }
                    } else {
                        updateOpenFileContent(displayName, pf.content);
                    }
                } else {
                    OpenFile of = new OpenFile(displayName, displayName, pf.content);
                    addAvailableFileFromOutside(of);
                }
                aiManagedFiles.put(displayName, Boolean.TRUE);
            }

            List<OpenFile> toDelete = new ArrayList<>();
            for (OpenFile existing : new ArrayList<>(availableFiles)) {
                Boolean aiOwned = aiManagedFiles.get(existing.id);
                if (aiOwned != null && aiOwned) {
                    if (!incomingIds.contains(existing.id)) {
                        toDelete.add(existing);
                    }
                }
            }
            for (OpenFile dead : toDelete) {
                if (showDiffs) {
                    List<DiffLine> delDiff = new ArrayList<>();
                    delDiff.add(new DiffLine('-', "(file will be deleted)"));

                    if (diffDialog != null && diffDialog.isShowing()) {
                        pendingFileDiffs.add(new PendingFileDiff(dead.id, null, delDiff));
                    } else {
                        showDiffBottomSheet(dead.id, delDiff, () -> {
                            aiManagedFiles.remove(dead.id);
                            deleteFileById(dead.id);
                        });
                    }
                } else {
                    aiManagedFiles.remove(dead.id);
                    deleteFile(dead);
                }
            }


            if (filesListContainer != null) {
                renderFilesList(filesListContainer);
            }

            if (update.entrypoint != null && !update.entrypoint.isEmpty()) {
                printToConsole("Entrypoint: " + update.entrypoint + "\n");
                aiEntrypoint = update.entrypoint;
            }

            this.aiLang = update.language;
            this.aiRuntime = update.runtime;
            applyTextMateLanguageFromAi();
        });

        if (pendingCode != null && !pendingCode.isEmpty()) {
            codeEditor.setText(pendingCode);
        } else {
            restoreFromProjectOrCache();
        }

        // after we restored the main code, rebuild file list from the project
        if (currentProject != null
                && currentProject.getFiles() != null
                && !currentProject.getFiles().isEmpty()) {

            availableFiles.clear();

            for (ProjectFile pf : currentProject.getFiles()) {
                OpenFile of = new OpenFile(pf.path, pf.path, pf.content);
                availableFiles.add(of);
            }

            // show in side panel
            if (filesListContainer != null) {
                renderFilesList(filesListContainer);
            }

            // open first file so there's a tab
            if (!availableFiles.isEmpty()) {
                openOrSelectFile(availableFiles.get(0));
            }
        }


        // Subscribe to editor changes
        contentSub = codeEditor.subscribeEvent(
                ContentChangeEvent.class,
                (event, publisher) -> {
                    OpenFile cur = getCurrentOpenFile();
                    if (cur != null) {
                        cur.content = getCode();
                    }
                    saveHandler.removeCallbacks(saveRunnable);
                    saveHandler.postDelayed(saveRunnable, SAVE_DEBOUNCE_MS);
                });

        main.post(() -> {
            if (aiBus != null) aiBus.publishEditorCode(getCode());
        });

        // args from parent
        Bundle args = getArguments();
        if (args != null) {
            aiLang = args.getString("ai_language");
            aiRuntime = args.getString("ai_runtime");
            aiRunnerHint = args.getString("ai_notes");
            String projectJson = args.getString("ai_project_json");
            String aiCode = args.getString("ai_code");

            if (aiCode != null && !aiCode.isEmpty()) setCode(aiCode);

            ingestAiProjectJson(projectJson);

            String liveOverride = args.getString("live_base_url");
            if (liveOverride != null && !liveOverride.trim().isEmpty())
                liveBaseUrl = liveOverride.trim();
        }

        if ((aiLang != null && !aiLang.isEmpty()) || (aiRuntime != null && !aiRuntime.isEmpty())) {
            printToConsole("Detected: " + (aiLang != null ? aiLang : "?")
                    + (aiRuntime != null && !aiRuntime.isEmpty() ? " (" + aiRuntime + ")" : "") + "\n");
        }
        if (aiRunnerHint != null && !aiRunnerHint.isEmpty()) {
            printToConsole("// Notes: " + aiRunnerHint + "\n");
        }

        // remember user's diff pref
        showDiffs = Prefs.showDiffs(requireContext());

        // Backend toggles
        setBackend(Backend.LIVE);

        // Run button
        btnRun.setOnClickListener(_v -> {
            persistCodeIfPossible();
            FragmentActivity a = requireActivity();
            if (a instanceof PagerNav) ((PagerNav) a).goToConsoleTab();

            String src = getCode();
            if (looksLikeHtml(src)) {
                // just publish the preview, don’t start live
                maybePublishHtmlPreview();
            } else {
                // always live runner
                exec.execute(() -> {
                    stopLiveSession("manual-run");
                    startLiveSessionForCurrentCode();
                });
            }
        });

        return v;
    }

    @Override
    public void onPause() {
        super.onPause();
        persistCodeIfPossible();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        saveHandler.removeCallbacks(saveRunnable);
        if (contentSub != null) {
            try {
                contentSub.unsubscribe();
            } catch (Throwable ignored) {
            }
            contentSub = null;
        }
        stopLiveSession("fragment-destroyed");
    }

    private void ingestAiProjectJson(@Nullable String projectJson) {
        if (projectJson == null || projectJson.trim().isEmpty()) return;
        try {
            org.json.JSONObject root = new org.json.JSONObject(projectJson);

            if (root.has("language")) {
                aiLang = root.optString("language", aiLang);
            }
            if (root.has("runtime")) {
                aiRuntime = root.optString("runtime", aiRuntime);
            }

            String entrypoint = root.optString("entrypoint", null);
            aiEntrypoint = entrypoint;

            if (root.has("files")) {
                org.json.JSONArray arr = root.getJSONArray("files");
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject f = arr.getJSONObject(i);
                    String path = f.optString("path", "");
                    String fname = f.optString("filename", "");
                    String content = f.optString("content", "");

                    String id;
                    if (path != null && !path.isEmpty() && !path.equals(".")) {
                        id = path + "/" + fname;
                    } else {
                        id = fname;
                    }

                    if (alreadyHasFile(id)) {
                        updateOpenFileContent(id, content);
                    } else {
                        OpenFile of = new OpenFile(id, id, content);
                        addAvailableFileFromOutside(of);
                    }

                    aiManagedFiles.put(id, Boolean.TRUE);
                }

                if (entrypoint != null && !entrypoint.isEmpty()) {
                    String ep1 = entrypoint;
                    String ep2 = "./" + entrypoint;
                    if (alreadyHasFile(ep1)) {
                        selectTabFor(ep1);
                    } else if (alreadyHasFile(ep2)) {
                        selectTabFor(ep2);
                    }
                }
            }

            applyTextMateLanguageFromAi();
        } catch (Exception e) {
            printToConsole("Project JSON parse error: " + e.getMessage() + "\n");
        }
    }

    private void showFilesPanel() {
        if (filesPanel != null) filesPanel.setVisibility(View.VISIBLE);
    }

    private void hideFilesPanel() {
        if (filesPanel != null) filesPanel.setVisibility(View.GONE);
    }

    public void addAvailableFileFromOutside(@NonNull OpenFile file) {
        availableFiles.add(file);

        if (!aiManagedFiles.containsKey(file.id)) {
            aiManagedFiles.put(file.id, Boolean.TRUE);
        }

        if (filesListContainer != null) {
            renderFilesList(filesListContainer);
        }
        openOrSelectFile(file);
    }


    private void openOrSelectFile(@NonNull OpenFile file) {
        if (openTabs.containsKey(file.id)) {
            selectTabFor(file.id);
            return;
        }

        openTabs.put(file.id, file);

        if (tabLayout != null) {
            TabLayout.Tab tab = tabLayout.newTab();
            tab.setText(file.name);
            tab.setTag(file);
            tabLayout.addTab(tab, true);
        }

        if (codeEditor != null) {
            codeEditor.setText(file.content);
        }
    }

    private void selectTabFor(@NonNull String fileId) {
        if (tabLayout == null) return;
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab t = tabLayout.getTabAt(i);
            if (t == null) continue;
            Object tag = t.getTag();
            if (tag instanceof OpenFile f) {
                if (fileId.equals(f.id)) {
                    t.select();
                    return;
                }
            }
        }
    }

    // ---------------- Project wiring & persistence ----------------
    public void setProject(@Nullable Project project) {
        this.currentProject = project;
        if (codeEditor != null && project != null) {
            // old behaviour
            String saved = project.getCode();
            if (saved != null && !saved.isEmpty()) {
                setCode(saved);
            } else {
                restoreFromCacheOnly();
            }

            if (project.getFiles() != null && !project.getFiles().isEmpty()) {
                availableFiles.clear();
                for (ProjectFile pf : project.getFiles()) {
                    OpenFile of = new OpenFile(pf.path, pf.path, pf.content);
                    availableFiles.add(of);
                }
                // open the first file so editor isn't blank
                if (!availableFiles.isEmpty()) {
                    openOrSelectFile(availableFiles.get(0));
                }
            }
        }
    }

    private void persistCodeIfPossible() {
        saveHandler.removeCallbacks(saveRunnable);
        saveRunnable.run();
    }

    private void restoreFromProjectOrCache() {
        if (currentProject != null) {
            String saved = currentProject.getCode();
            if (saved != null && !saved.isEmpty()) {
                setCode(saved);
                return;
            }
        }
        restoreFromCacheOnly();
    }

    private void restoreFromCacheOnly() {
        if (getContext() == null) return;
        String key = cacheKeyForCurrentProject();
        String cached = requireContext().getSharedPreferences(SP_FILE, 0).getString(key, null);
        if (cached != null && !cached.isEmpty()) {
            setCode(cached);
        }
    }

    private String cacheKeyForCurrentProject() {
        String id = (currentProject != null && currentProject.getId() != null)
                ? currentProject.getId()
                : "tmp";
        return "code_" + id;
    }

    // ---------- Apply AI code & optionally restart live ----------
    public void applyAiCode(@NonNull String newCode, boolean showDiff) {
        String oldCode = getCode();
        if (!showDiff) {
            setCode(newCode);
            persistCodeIfPossible();
            printToConsole("Applied AI code to editor.\n");
            if (currentBackend == Backend.LIVE) {
                exec.execute(() -> {
                    stopLiveSession("code-updated");
                    startLiveSessionForCurrentCode();
                });
            }
            return;
        }
        if (diffDialog != null && diffDialog.isShowing()) {
            queuedNewCode = newCode;
            printToConsole("New changes queued. Review the open diff first.\n");
            return;
        }
        List<DiffLine> diff = DiffUtilLite.diffLines(oldCode, newCode);
        showDiffBottomSheet(diff, () -> {
            setCode(newCode);
            persistCodeIfPossible();
            printToConsole("Applied AI code after review.\n");
            if (currentBackend == Backend.LIVE) {
                exec.execute(() -> {
                    stopLiveSession("code-updated");
                    startLiveSessionForCurrentCode();
                });
            }
        });
    }

    private void showDiffBottomSheetInternal(@NonNull List<DiffLine> diff,
                                             @NonNull Runnable onAccept) {
        if (diffDialog == null) {
            View sheet = LayoutInflater.from(requireContext())
                    .inflate(R.layout.sheet_diff_preview, null, false);

            RecyclerView rv = sheet.findViewById(R.id.diff_list);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            diffAdapter = new DiffAdapter(currentDiff);
            rv.setAdapter(diffAdapter);

            diffHeaderView = sheet.findViewById(R.id.diff_summary);

            sheet.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
                // user rejected this change
                diffDialog.dismiss();
                showNextPendingDiff();
            });

            sheet.findViewById(R.id.btn_apply).setOnClickListener(v -> {
                // run current action (update file / delete file / apply main code)
                if (onAcceptAction != null) onAcceptAction.run();

                // 1) editor-wide queued change gets priority
                if (queuedNewCode != null) {
                    String oldCode = getCode();
                    List<DiffLine> next = DiffUtilLite.diffLines(oldCode, queuedNewCode);
                    String applyCode = queuedNewCode;
                    queuedNewCode = null;
                    currentDiffFileId = "(editor)";

                    currentDiff.clear();
                    currentDiff.addAll(next);
                    onAcceptAction = () -> {
                        setCode(applyCode);
                        persistCodeIfPossible();
                    };

                    if (diffAdapter != null) diffAdapter.notifyDataSetChanged();
                    if (diffHeaderView != null) updateDiffHeader(diffHeaderView);
                    return; // keep sheet open
                }

                // 2) if we still have pending file diffs (like your game.py delete) -> load next
                if (!pendingFileDiffs.isEmpty()) {
                    PendingFileDiff next = pendingFileDiffs.remove(0);
                    bindDiffFromPending(next);
                    return; // keep sheet open
                }

                // 3) nothing else -> now we can close
                diffDialog.dismiss();
                onAcceptAction = null;
                currentDiffFileId = null;
            });

            diffDialog = new BottomSheetDialog(requireContext());
            diffDialog.setCancelable(false);
            diffDialog.setDismissWithAnimation(true);
            diffDialog.setContentView(sheet);

            diffDialog.setOnDismissListener(d -> {
                onAcceptAction = null;
                currentDiffFileId = null;
                pendingFileDiffs.clear();
            });
        }

        // update list + callback
        currentDiff.clear();
        currentDiff.addAll(diff);
        onAcceptAction = onAccept;

        if (diffAdapter != null) diffAdapter.notifyDataSetChanged();

        // make sure header shows correct file name
        if (diffHeaderView != null) {
            updateDiffHeader(diffHeaderView);
        }

        if (!diffDialog.isShowing()) {
            diffDialog.show();
        }
    }

    private void showNextPendingDiff() {
        if (pendingFileDiffs.isEmpty()) {
            onAcceptAction = null;
            currentDiffFileId = null;
            return;
        }

        PendingFileDiff next = pendingFileDiffs.remove(0);
        currentDiffFileId = next.fileId;

        if (next.newContent == null) {
            showDiffBottomSheetInternal(
                    next.diff,
                    () -> {
                        aiManagedFiles.remove(next.fileId);
                        deleteFileById(next.fileId);
                    }
            );
        } else {
            showDiffBottomSheetInternal(
                    next.diff,
                    () -> updateOpenFileContent(next.fileId, next.newContent)
            );
        }
    }

    private void deleteFileById(@NonNull String id) {
        for (OpenFile f : new ArrayList<>(availableFiles)) {
            if (id.equals(f.id)) {
                deleteFile(f);
                break;
            }
        }
    }

    // for file-specific changes (AI updated game/engine.py, etc.)
    private void showDiffBottomSheet(@NonNull String fileId,
                                     @NonNull List<DiffLine> diff,
                                     @NonNull Runnable onAccept) {
        currentDiffFileId = fileId;
        showDiffBottomSheetInternal(diff, onAccept);
    }

    // for “editor/global” changes (applyAiCode on main editor)
    private void showDiffBottomSheet(@NonNull List<DiffLine> diff,
                                     @NonNull Runnable onAccept) {
        currentDiffFileId = "(editor)";
        showDiffBottomSheetInternal(diff, onAccept);
    }

    private void updateDiffHeader(TextView summary) {
        int adds = 0, dels = 0;
        for (DiffLine d : currentDiff) {
            if (d.type == '+') adds++;
            else if (d.type == '-') dels++;
        }

        String filePart = (currentDiffFileId != null && !currentDiffFileId.isEmpty())
                ? currentDiffFileId
                : "Changes";

        summary.setText(filePart + "  ·  +" + adds + "  ·  −" + dels);
    }

    private void bindDiffFromPending(@NonNull PendingFileDiff next) {
        currentDiffFileId = next.fileId;

        // replace list
        currentDiff.clear();
        currentDiff.addAll(next.diff);

        // set what should happen when user presses Apply on THIS one
        if (next.newContent == null) {
            // it's a delete
            onAcceptAction = () -> {
                aiManagedFiles.remove(next.fileId);
                deleteFileById(next.fileId);
            };
        } else {
            onAcceptAction = () -> updateOpenFileContent(next.fileId, next.newContent);
        }

        if (diffAdapter != null) diffAdapter.notifyDataSetChanged();
        if (diffHeaderView != null) updateDiffHeader(diffHeaderView);
    }


    // -------- tiny diff + adapter --------
    static final class DiffLine {
        final char type;
        final String text;

        DiffLine(char t, String s) {
            type = t;
            text = s;
        }
    }

    static final class DiffUtilLite {
        static List<DiffLine> diffLines(String a, String b) {
            String[] A = a.split("\n", -1);
            String[] B = b.split("\n", -1);
            int n = A.length, m = B.length;
            int[][] dp = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--)
                for (int j = m - 1; j >= 0; j--)
                    dp[i][j] = A[i].equals(B[j]) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);

            List<DiffLine> out = new ArrayList<>();
            int i = 0, j = 0;
            while (i < n && j < m) {
                if (A[i].equals(B[j])) {
                    out.add(new DiffLine(' ', A[i]));
                    i++;
                    j++;
                } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                    out.add(new DiffLine('-', A[i++]));
                } else {
                    out.add(new DiffLine('+', B[j++]));
                }
            }
            while (i < n) out.add(new DiffLine('-', A[i++]));
            while (j < m) out.add(new DiffLine('+', B[j++]));
            return out;
        }
    }

    static final class DiffAdapter extends RecyclerView.Adapter<DiffVH> {
        private final List<DiffLine> lines;

        DiffAdapter(List<DiffLine> l) {
            lines = l;
        }

        @NonNull
        @Override
        public DiffVH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View row = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_diff_line, p, false);
            return new DiffVH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull DiffVH h, int i) {
            h.bind(lines.get(i));
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }
    }

    static final class DiffVH extends RecyclerView.ViewHolder {
        private final TextView t;

        DiffVH(View v) {
            super(v);
            t = v.findViewById(R.id.diff_line_text);
        }

        void bind(DiffLine l) {
            t.setText((l.type == ' ' ? "  " : (l.type + " ")) + l.text);
            int bg, fg;
            if (l.type == '+') {
                bg = 0x1928A745;
                fg = 0xFF28A745;
            } else if (l.type == '-') {
                bg = 0x19D32F2F;
                fg = 0xFFD32F2F;
            } else {
                bg = 0x00000000;
                fg = 0xFFB0B0B0;
            }
            t.setBackgroundColor(bg);
            t.setTextColor(fg);
        }
    }

    // ---------- basic editor helpers ----------
    public void setCode(@Nullable String code) {
        pendingCode = code;
        if (codeEditor != null && code != null) {
            codeEditor.setText(looksLikeHtmlDoc(code) ? beautifyHtml(code) : code);
        }
        saveHandler.removeCallbacks(saveRunnable);
        saveHandler.postDelayed(saveRunnable, SAVE_DEBOUNCE_MS);
    }

    @NonNull
    public String getCode() {
        return codeEditor != null && codeEditor.getText() != null
                ? codeEditor.getText().toString()
                : (pendingCode != null ? pendingCode : "");
    }

    public void setAiHints(@Nullable String language, @Nullable String runtime, @Nullable String notes) {
        this.aiLang = language;
        this.aiRuntime = runtime;
        this.aiRunnerHint = notes;
    }

    // ---------- Backend selection ----------
    private void setBackend(Backend b) {
        currentBackend = b;
    }

    // ---------- HTML preview ----------
    private boolean looksLikeHtml(String src) {
        String s = src.toLowerCase();
        return s.contains("<!doctype html") || s.contains("<html") || s.contains("<head") || s.contains("<body");
    }

    private void maybePublishHtmlPreview() {
        String src = getCode();
        if (!looksLikeHtml(src)) return;

        exec.execute(() -> {
            try {
                JSONObject res = httpPostJson(liveBaseUrl + "/projects", new JSONObject());
                String projectId = res.optString("projectId", null);
                if (projectId == null) throw new RuntimeException("No projectId");

                JSONObject fileReq = new JSONObject();
                fileReq.put("path", "index.html");
                fileReq.put("content", src);
                httpPutJson(liveBaseUrl + "/projects/" + projectId + "/files", fileReq);

                String preview = liveBaseUrl + "/preview/" + projectId + "/index.html";
                consoleVM.setPreviewUrl(preview);
                printToConsole("🌐 Preview: " + preview + "\n");
            } catch (Exception e) {
                printToConsole("HTML preview error: " + e.getMessage() + "\n");
            }
        });
    }

    // ---------- Live session helpers ----------
    private void connectLiveWebSocket(String wsUrl) {
        Request req = new Request.Builder().url(wsUrl).build();
        liveSocket = ok.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                printToConsole("Live connected.\n");
                liveConnecting = false;
                setRunning(false);
                FragmentActivity a = requireActivity();
                if (a instanceof PagerNav) ((PagerNav) a).goToConsoleTab();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                printToConsole(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                printToConsole(bytes.utf8());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                printToConsole("\n⏹ Live closing (" + code + "): " + reason + "\n");
                webSocket.close(1000, null);
                liveSocket = null;
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response r) {
                printToConsole("\n✖ Live socket error: " + t.getMessage() + "\n");
                liveSocket = null;
            }
        });
    }

    private void initLiveManagerIfNeeded() {
        if (liveRunManager != null) return;
        liveRunManager = new LiveRunManager(
                liveBaseUrl,
                exec,
                new LiveRunManager.Listener() {
                    @Override
                    public void log(String msg) {
                        main.post(() -> printToConsole(msg));
                    }

                    @Override
                    public void onSessionReady(String wsUrl, String sessionId) {
                        liveSessionId = sessionId;
                        main.post(() -> {
                            connectLiveWebSocket(wsUrl);
                            liveConnecting = false;
                            setRunning(false);
                        });
                    }

                    @Override
                    public void onStopped() {
                        // optional
                    }

                    @Override
                    public void onError(String msg) {
                        main.post(() -> {
                            printToConsole(msg + "\n");
                            liveConnecting = false;
                            setRunning(false);
                        });
                    }
                }
        );
    }

    private void startLiveSessionForCurrentCode() {
        setBackend(Backend.LIVE);

        if (liveConnecting) {
            printToConsole("Live is already connecting…\n");
            return;
        }
        if (liveSocket != null || liveSessionId != null) {
            printToConsole("↻ Restarting live session to apply current code…\n");
            stopLiveSession("restart");
        }

        setRunning(true);
        liveConnecting = true;
        printToConsole("⏵ Starting live session…\n");

        initLiveManagerIfNeeded();

        // 1) collect all editor files
        JSONArray filesJson = buildEditorFilesJson();

        // 2) pick language
        String language = (aiLang != null && !aiLang.isEmpty()) ? aiLang : "python";

        // 3) pick entrypoint
        String entry = aiEntrypoint;
        if (entry == null || entry.trim().isEmpty()) {
            entry = guessEntrypointFromEditorFiles(filesJson);
        }

        // If AI said a file that doesn't exist anymore, ask user
        if (entry != null && !editorHasPath(filesJson, entry)) {
            liveConnecting = false;
            setRunning(false);
            main.post(() -> showEntrypointPicker(filesJson, language));
            return;
        }

        // 4) start live run with all files
        liveRunManager.startLiveBulk(
                filesJson,
                language,
                entry != null ? entry : "main.py",
                true,
                true
        );
    }

    private void stopLiveSession(@Nullable String reason) {
        try {
            if (liveSocket != null) {
                liveSocket.close(1000, reason == null ? "closed" : reason);
            }
        } catch (Throwable ignore) {
        }
        liveSocket = null;

        initLiveManagerIfNeeded();
        liveRunManager.stopSession(liveSessionId);
        liveSessionId = null;
        liveConnecting = false;
    }

    private JSONArray buildEditorFilesJson() {
        JSONArray arr = new JSONArray();
        for (OpenFile f : availableFiles) {
            try {
                JSONObject o = new JSONObject();
                o.put("path", f.id != null ? f.id : f.name);
                o.put("content", f.content != null ? f.content : "");
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    private boolean editorHasPath(JSONArray files, String path) {
        if (path == null) return false;
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) continue;
            String p = f.optString("path", "");
            if (p.equals(path) || p.equals("./" + path) || ("./" + p).equals(path)) {
                return true;
            }
        }
        return false;
    }

    private String guessEntrypointFromEditorFiles(JSONArray files) {
        String first = null;
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) continue;
            String p = f.optString("path", "");
            if (first == null) first = p;
            if ("main.py".equals(p) || "./main.py".equals(p)) {
                return p.replace("./", "");
            }
        }
        return first;
    }

    private void showEntrypointPicker(JSONArray files, String language) {
        if (getContext() == null) return;

        List<String> names = new ArrayList<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) continue;
            names.add(f.optString("path", "file" + i));
        }

        CharSequence[] items = names.toArray(new CharSequence[0]);

        new AlertDialog.Builder(requireContext())
                .setTitle("Select file to run")
                .setItems(items, (d, which) -> {
                    String chosen = names.get(which);
                    initLiveManagerIfNeeded();
                    liveRunManager.startLiveBulk(
                            buildEditorFilesJson(),
                            language,
                            chosen,
                            true,
                            true
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void printToConsole(@NonNull String msg) {
        if (consoleVM != null) {
            consoleVM.append(msg);
        }
    }

    private void setRunning(boolean running) {
        if (progress == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            progress.setVisibility(running ? View.VISIBLE : View.GONE);
        } else {
            main.post(() -> {
                if (progress != null) {
                    progress.setVisibility(running ? View.VISIBLE : View.GONE);
                }
            });
        }
    }

// ==================== FILE SYSTEM / FILE TREE ====================

    // ---- File tree model for files panel ----
    private static class FileNode {
        final String name;
        final boolean isFile;
        final String fullPath;              // e.g. "src/main.py"
        final List<FileNode> children = new ArrayList<>();

        FileNode(@NonNull String name, boolean isFile, @NonNull String fullPath) {
            this.name = name;
            this.isFile = isFile;
            this.fullPath = fullPath;
        }
    }

    // Folder expansion state: "path" -> expanded?
    private final Map<String, Boolean> folderExpansion = new HashMap<>();
    private FileNode fileTreeRoot = new FileNode("", false, "");

    // ---------------------- Tabs ----------------------
    private void closeCurrentTab() {
        if (tabLayout == null) return;

        int idx = tabLayout.getSelectedTabPosition();
        if (idx == -1) return;

        TabLayout.Tab tab = tabLayout.getTabAt(idx);
        if (tab == null) return;

        Object tag = tab.getTag();
        if (tag instanceof OpenFile f) {
            openTabs.remove(f.id);
        }

        tabLayout.removeTabAt(idx);

        if (tabLayout.getTabCount() > 0) {
            int newIndex = Math.max(0, idx - 1);
            TabLayout.Tab newTab = tabLayout.getTabAt(newIndex);
            if (newTab != null) {
                newTab.select();
                Object t = newTab.getTag();
                if (t instanceof OpenFile && codeEditor != null) {
                    codeEditor.setText(((OpenFile) t).content);
                }
            }
        } else {
            if (codeEditor != null) {
                codeEditor.setText("");
            }
        }
    }

    // ---------------------- Tree render entrypoint ----------------------
    private void renderFilesList(@NonNull LinearLayout container) {
        if (getContext() == null) return;

        // Build tree from current files
        rebuildFileTree();

        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (FileNode child : fileTreeRoot.children) {
            renderFileNodeRow(container, inflater, child, 0);
        }
    }

    // ---------------------- Build tree from availableFiles ----------------------
    private void rebuildFileTree() {
        fileTreeRoot = new FileNode("", false, "");

        for (OpenFile f : availableFiles) {
            if (f.id == null || f.id.trim().isEmpty()) continue;

            String normPath = normalizePath(f.id);
            String[] parts = normPath.split("/");

            FileNode current = fileTreeRoot;
            StringBuilder pathBuilder = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) continue;

                boolean isFile = (i == parts.length - 1);

                if (pathBuilder.length() > 0) {
                    pathBuilder.append("/");
                }
                pathBuilder.append(part);
                String fullPath = pathBuilder.toString();

                FileNode child = null;
                for (FileNode c : current.children) {
                    if (c.name.equals(part) && c.isFile == isFile) {
                        child = c;
                        break;
                    }
                }

                if (child == null) {
                    child = new FileNode(part, isFile, fullPath);
                    current.children.add(child);
                }

                current = child;
            }
        }

        sortFileTree(fileTreeRoot);
    }

    private void sortFileTree(@NonNull FileNode node) {
        Collections.sort(node.children, (a, b) -> {
            if (!a.isFile && b.isFile) return -1;   // folders first
            if (a.isFile && !b.isFile) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        for (FileNode c : node.children) {
            sortFileTree(c);
        }
    }

    private void showFolderPicker(boolean isFile) {
        if (getContext() == null) return;

        List<String> folders = collectFolderPaths();   // includes "(root)"
        CharSequence[] labels = new CharSequence[folders.size()];

        for (int i = 0; i < folders.size(); i++) {
            String p = folders.get(i);
            labels[i] = "(root)".equals(p) ? "⟂ Project root" : p;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Choose folder")
                .setItems(labels, (dialog, which) -> {
                    String chosen = folders.get(which);
                    String parentFolder =
                            "(root)".equals(chosen) ? null : chosen; // null = top level

                    if (isFile) {
                        showCreateFileDialog(parentFolder);
                    } else {
                        showCreateFolderFlow(parentFolder);
                    }
                })
                .show();
    }


    private void showCreateItemChooser(@Nullable String parentFolder) {
        if (getContext() == null) return;

        String[] options = {"New file", "New folder"};

        new AlertDialog.Builder(requireContext())
                .setTitle("Create")
                .setItems(options, (dialog, which) -> {
                    boolean isFile = (which == 0);

                    if (parentFolder != null && !parentFolder.isEmpty()) {
                        // Called from a folder "+" → we already know the parent
                        if (isFile) {
                            showCreateFileDialog(parentFolder);
                        } else {
                            showCreateFolderFlow(parentFolder);
                        }
                    } else {
                        // Called from top "+" → ask which folder (or root) first
                        showFolderPicker(isFile);
                    }
                })
                .show();
    }

    private void showCreateFolderFlow(@Nullable String parentFolder) {
        if (getContext() == null) return;

        final EditText input = new EditText(getContext());
        input.setHint("Folder name (e.g. src or logic)");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle("New folder")
                .setView(input)
                .setPositiveButton("Next", (d, w) -> {
                    String folder = input.getText().toString().trim();
                    if (folder.isEmpty()) return;

                    String fullFolder = (parentFolder == null || parentFolder.isEmpty())
                            ? folder
                            : parentFolder + "/" + folder;

                    // After creating a folder, immediately ask for the first file inside it
                    showCreateFileDialog(fullFolder);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private List<String> collectFolderPaths() {
        List<String> result = new ArrayList<>();
        result.add("(root)");

        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();

        for (OpenFile f : availableFiles) {
            if (f.id == null) continue;
            String norm = normalizePath(f.id);
            String[] parts = norm.split("/");

            StringBuilder pathBuilder = new StringBuilder();
            // all segments except the last (last is file name)
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) continue;

                if (pathBuilder.length() > 0) pathBuilder.append("/");
                pathBuilder.append(part);
                seen.add(pathBuilder.toString());
            }
        }

        result.addAll(seen);
        return result;
    }


    // ---------------------- Render single node (folder or file) ----------------------
    private void renderFileNodeRow(@NonNull LinearLayout parent,
                                   @NonNull LayoutInflater inflater,
                                   @NonNull FileNode node,
                                   int depth) {

        View row = inflater.inflate(R.layout.item_file_node, parent, false);

        View rowRoot = row.findViewById(R.id.row_root);
        ImageView iconExpand = row.findViewById(R.id.icon_expand);
        ImageView iconType = row.findViewById(R.id.icon_type);
        TextView textName = row.findViewById(R.id.text_name);
        ImageButton btnAddChild = row.findViewById(R.id.btn_add_child);
        ImageButton btnDelete = row.findViewById(R.id.btn_delete);

        // Indent based on depth
        int basePaddingStart = dp(4);
        int indent = dp(14) * depth;
        rowRoot.setPadding(
                basePaddingStart + indent,
                rowRoot.getPaddingTop(),
                rowRoot.getPaddingRight(),
                rowRoot.getPaddingBottom()
        );

        textName.setText(node.name);

        if (!node.isFile) {
            // ---------- FOLDER ----------
            iconType.setImageResource(R.drawable.ic_folder_24);

            btnAddChild.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.VISIBLE);

            boolean expanded = folderExpansion.getOrDefault(node.fullPath, Boolean.TRUE);
            folderExpansion.put(node.fullPath, expanded);

            iconExpand.setVisibility(View.VISIBLE);
            iconExpand.setImageResource(
                    expanded ? R.drawable.ic_expand_less_24 : R.drawable.ic_expand_more_24
            );

            // expand / collapse
            View.OnClickListener toggle = v -> {
                boolean cur = folderExpansion.getOrDefault(node.fullPath, Boolean.TRUE);
                folderExpansion.put(node.fullPath, !cur);
                if (filesListContainer != null) renderFilesList(filesListContainer);
            };
            rowRoot.setOnClickListener(toggle);
            iconExpand.setOnClickListener(toggle);

            // + : create file/folder inside this folder
            btnAddChild.setOnClickListener(v -> showCreateItemChooser(node.fullPath));

            // delete folder (and files inside)
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete folder?")
                        .setMessage("Delete " + node.fullPath + " and all its files?")
                        .setPositiveButton("Delete", (d, w) -> {
                            String folderNorm = normalizePath(node.fullPath);
                            String prefix = folderNorm + "/";

                            // collect & delete all files in this folder
                            List<OpenFile> toRemove = new ArrayList<>();
                            for (OpenFile f : new ArrayList<>(availableFiles)) {
                                String idNorm = normalizePath(f.id);
                                if (idNorm.equals(folderNorm) || idNorm.startsWith(prefix)) {
                                    toRemove.add(f);
                                }
                            }
                            for (OpenFile f : toRemove) {
                                deleteFile(f);
                            }
                            if (filesListContainer != null) renderFilesList(filesListContainer);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            parent.addView(row);

            if (expanded) {
                for (FileNode child : node.children) {
                    renderFileNodeRow(parent, inflater, child, depth + 1);
                }
            }
        } else {
            // ---------- FILE ----------
            iconExpand.setVisibility(View.INVISIBLE);
            iconType.setImageDrawable(null);         // no icon for file
            btnAddChild.setVisibility(View.GONE);    // IMPORTANT
            btnDelete.setVisibility(View.VISIBLE);

            rowRoot.setOnClickListener(v -> {
                OpenFile of = findOpenFileByPath(node.fullPath);
                if (of != null) {
                    openOrSelectFile(of);
                    hideFilesPanel();
                }
            });

            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete file?")
                        .setMessage("Delete " + node.fullPath + "?")
                        .setPositiveButton("Delete", (d, w) -> {
                            OpenFile of = findOpenFileByPath(node.fullPath);
                            if (of != null) deleteFile(of);
                            if (filesListContainer != null) renderFilesList(filesListContainer);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            parent.addView(row);
        }
    }

    // ---------------------- Helpers ----------------------
    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Nullable
    private OpenFile findOpenFileByPath(@NonNull String path) {
        String norm = normalizePath(path);
        for (OpenFile f : availableFiles) {
            if (normalizePath(f.id).equals(norm)) {
                return f;
            }
        }
        return null;
    }

    private void deleteFile(@NonNull OpenFile file) {
        int idx = availableFiles.indexOf(file);
        if (idx >= 0) {
            availableFiles.remove(idx);
        }

        boolean wasCurrent = false;

        if (tabLayout != null) {
            int selected = tabLayout.getSelectedTabPosition();
            if (selected >= 0) {
                TabLayout.Tab sel = tabLayout.getTabAt(selected);
                if (sel != null && sel.getTag() instanceof OpenFile selFile) {
                    wasCurrent = file.id.equals(selFile.id);
                }
            }

            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab t = tabLayout.getTabAt(i);
                if (t != null && t.getTag() instanceof OpenFile of) {
                    if (file.id.equals(of.id)) {
                        tabLayout.removeTabAt(i);
                        break;
                    }
                }
            }
        }

        openTabs.remove(file.id);

        if (wasCurrent) {
            if (tabLayout != null && tabLayout.getTabCount() > 0) {
                TabLayout.Tab newTab = tabLayout.getTabAt(0);
                if (newTab != null) {
                    newTab.select();
                    Object tag = newTab.getTag();
                    if (tag instanceof OpenFile && codeEditor != null) {
                        codeEditor.setText(((OpenFile) tag).content);
                    }
                }
            } else if (codeEditor != null) {
                codeEditor.setText("");
            }
        }

        if (filesListContainer != null) {
            renderFilesList(filesListContainer);
        }
    }


    // ---------- small utils ----------
    private String b64(String s) {
        return android.util.Base64.encodeToString(
                (s == null ? "" : s).getBytes(StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP
        );
    }

    private String b64DecodeOrEmpty(String s) {
        if (s == null || s.isEmpty() || "null".equalsIgnoreCase(s)) return "";
        try {
            byte[] bytes = android.util.Base64.decode(s, android.util.Base64.DEFAULT);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    public void toggleEditorTheme() {
        editorDarkTheme = !editorDarkTheme;
        String themeName = editorDarkTheme ? "dark" : "light";
        applyTextMateTheme(themeName);
    }

    // ---------- TextMate init + apply from AI ----------
    private void initTextMateIfNeeded() {
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                    new AssetsFileResolver(requireContext().getAssets())
            );

            GrammarRegistry.getInstance().loadGrammars("tm/languages.json");

            SharedPreferences prefs =
                    requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
            boolean dark = prefs.getBoolean("dark_theme", false);

            String themeName = dark ? "dark" : "light";
            String themePath = "themes/" + themeName + ".json";

            ThemeRegistry themeRegistry = ThemeRegistry.getInstance();

            ThemeModel model = new ThemeModel(
                    IThemeSource.fromInputStream(
                            FileProviderRegistry.getInstance().tryGetInputStream(themePath),
                            themePath,
                            null
                    ),
                    themeName
            );

            themeRegistry.loadTheme(model);
            themeRegistry.setTheme(themeName);

            codeEditor.setColorScheme(TextMateColorScheme.create(themeRegistry));
            codeEditor.setEditorLanguage(TextMateLanguage.create("source.python", true));

        } catch (Throwable t) {
            t.printStackTrace();
            printToConsole("TextMate init failed: " + t.getMessage() + "\n");
        }
    }

    private void applyTextMateTheme(@NonNull String themeName) {
        try {
            ThemeRegistry themeRegistry = ThemeRegistry.getInstance();
            String themePath = "themes/" + themeName + ".json";

            ThemeModel model = new ThemeModel(
                    IThemeSource.fromInputStream(
                            FileProviderRegistry.getInstance().tryGetInputStream(themePath),
                            themePath,
                            null
                    ),
                    themeName
            );

            themeRegistry.loadTheme(model);
            themeRegistry.setTheme(themeName);

            if (codeEditor != null) {
                codeEditor.setColorScheme(TextMateColorScheme.create(themeRegistry));
            }
        } catch (Throwable t) {
            t.printStackTrace();
            printToConsole("Theme apply failed: " + t.getMessage() + "\n");
        }
    }

    private void applyTextMateLanguageFromAi() {
        if (codeEditor == null) return;

        String scope;
        if (aiLang == null || aiLang.trim().isEmpty()) {
            scope = "source.js";
        } else {
            String lang = aiLang.trim().toLowerCase();
            if (lang.contains("python") || lang.equals("py")) {
                scope = "source.python";
            } else if (lang.contains("typescript") || lang.equals("ts")) {
                scope = "source.ts";
            } else if (lang.contains("javascript") || lang.equals("js") || lang.contains("node")) {
                if (lang.contains("react") || lang.contains("jsx")) {
                    scope = "source.jsx";
                } else {
                    scope = "source.js";
                }
            } else if (lang.contains("html")) {
                scope = "text.html.basic";
            } else if (lang.contains("css")) {
                scope = "source.css";
            } else if (lang.contains("java")) {
                scope = "source.java";
            } else if (lang.contains("kotlin")) {
                scope = "source.kotlin";
            } else if (lang.contains("php")) {
                scope = "source.php";
            } else if (lang.contains("c#") || lang.contains("csharp")) {
                scope = "source.cs";
            } else if (lang.contains("c++") || lang.contains("cpp")) {
                scope = "source.cpp";
            } else {
                scope = "source.js";
            }
        }

        try {
            codeEditor.setEditorLanguage(TextMateLanguage.create(scope, true));
        } catch (Throwable t) {
            printToConsole("TM language apply failed: " + t.getMessage() + "\n");
        }
    }

    private String optStatusDesc(JSONObject res) {
        try {
            if (res.has("status")) {
                JSONObject st = res.getJSONObject("status");
                return st.optString("description", "?");
            }
        } catch (Exception ignored) {
        }
        return res.optString("status", "?");
    }

    private String extractPromptForCompletion() {
        if (codeEditor == null || codeEditor.getText() == null) return "";
        final String all = codeEditor.getText().toString();
        int caret = codeEditor.getCursor() != null ? codeEditor.getCursor().getLeft() : 0;
        if (caret <= 0 || all.isEmpty()) return "";
        if (caret > all.length()) caret = all.length();

        int idx = caret;
        for (int k = 0; k < 20 && idx > 0; k++) {
            int next = all.lastIndexOf('\n', idx - 1);
            if (next < 0) {
                idx = 0;
                break;
            }
            idx = next;
        }
        int from = (idx <= 0) ? 0 : (idx + 1);
        if (from > caret) from = caret;
        if (from < 0) from = 0;
        return all.substring(from, caret);
    }

    private boolean alreadyHasFile(@NonNull String id) {
        String norm = normalizePath(id);
        for (OpenFile f : availableFiles) {
            if (normalizePath(f.id).equals(norm)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String p) {
        if (p == null) return "";

        // Trim and normalize separators
        p = p.trim().replace("\\", "/");

        // Remove leading "./", "../", or "/" (can appear multiple times)
        while (p.startsWith("./") || p.startsWith(".\\") || p.startsWith("../")) {
            p = p.substring(p.indexOf('/') + 1); // skip first segment
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }

        // Collapse multiple slashes into one
        p = p.replaceAll("/+", "/");

        // Remove trailing slash (so "css/" -> "css")
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }

        // If you really want case-insensitive comparisons, keep this:
        return p.toLowerCase(Locale.ROOT);
    }


    private void updateOpenFileContent(@NonNull String id, @NonNull String newContent) {
        for (OpenFile f : availableFiles) {
            if (id.equals(f.id)) {
                f.content = newContent;
                break;
            }
        }
        if (openTabs.containsKey(id)) {
            OpenFile f = openTabs.get(id);
            if (f != null) {
                f.content = newContent;
                if (tabLayout != null) {
                    TabLayout.Tab tab = tabLayout.getTabAt(tabLayout.getSelectedTabPosition());
                    if (tab != null && tab.getTag() instanceof OpenFile cur) {
                        if (id.equals(cur.id) && codeEditor != null) {
                            codeEditor.setText(newContent);
                        }
                    }
                }
            }
        }
    }

    private void showCreateFileDialog() {
        showCreateFileDialog(null);
    }

    private void showCreateFileDialog(@Nullable String parentFolder) {
        if (getContext() == null) return;

        final EditText input = new EditText(getContext());
        input.setHint("File name (e.g. main.py)");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle("New file")
                .setView(input)
                .setPositiveButton("Create", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;

                    String id = (parentFolder == null || parentFolder.isEmpty())
                            ? name
                            : parentFolder + "/" + name;

                    if (alreadyHasFile(id)) {
                        printToConsole("File \"" + id + "\" already exists.\n");
                        SnackBarApp.INSTANCE.show(
                                requireActivity().findViewById(android.R.id.content),
                                "A file with that name already exists",
                                SnackBarApp.Type.ERROR
                        );
                        return;
                    }

                    OpenFile of = new OpenFile(id, id, "");
                    aiManagedFiles.put(id, Boolean.FALSE);
                    addAvailableFileFromOutside(of);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private String currentLanguageKey() {
        String l = (aiLang == null ? "" : aiLang.trim().toLowerCase());
        if (l.contains("java") && !l.contains("javascript")) return "java";
        if (l.contains("kotlin")) return "kotlin";
        if (l.contains("python") || l.equals("py")) return "python";
        if (l.contains("c#") || l.contains("csharp")) return "csharp";
        if (l.contains("c++") || l.contains("cpp")) return "cpp";
        if (l.contains("javascript") || l.equals("js") || l.contains("node")) return "javascript";
        if (l.contains("html")) return "html";
        if (l.contains("css")) return "css";
        if (l.contains("php")) return "php";
        String src = getCode().trim().toLowerCase();
        if (src.startsWith("<!doctype") || src.contains("<html")) return "html";
        return "javascript";
    }

    private String renderSnippet(String insertText) {
        String s = insertText;
        s = s.replaceAll("\\$\\{\\d+:([^}]+)\\}", "$1");
        s = s.replaceAll("\\$\\d+", "");
        return s;
    }

    // ---------- HTML detection + beautify ----------
    private boolean looksLikeHtmlDoc(String s) {
        if (s == null) return false;
        String t = s.toLowerCase();
        return t.contains("<!doctype") || t.contains("<html") || t.contains("<head") || t.contains("<body");
    }

    private String beautifyHtml(String raw) {
        if (raw == null) return "";
        try {
            Class<?> jsoupC = Class.forName("org.jsoup.Jsoup");
            Method parse = jsoupC.getMethod("parse", String.class);
            Object doc = parse.invoke(null, raw);

            Class<?> docCls = Class.forName("org.jsoup.nodes.Document");
            Method outputSettingsGetter = docCls.getMethod("outputSettings");
            Object os = outputSettingsGetter.invoke(doc);

            Class<?> osCls = Class.forName("org.jsoup.nodes.Document$OutputSettings");
            Method prettyPrint = osCls.getMethod("prettyPrint", boolean.class);
            Method indentAmount = osCls.getMethod("indentAmount", int.class);
            Method outline = osCls.getMethod("outline", boolean.class);
            prettyPrint.invoke(os, true);
            indentAmount.invoke(os, 2);
            outline.invoke(os, false);

            Method outerHtml = docCls.getMethod("outerHtml");
            return (String) outerHtml.invoke(doc);
        } catch (Throwable ignore) {
            return naiveHtmlFormat(raw);
        }
    }

    private String naiveHtmlFormat(String raw) {
        String s = raw.replaceAll(">(\\s*)<", ">\n<").trim();
        String[] lines = s.split("\n");
        StringBuilder out = new StringBuilder();
        int indent = 0;
        final String IND = "  ";
        for (String ln : lines) {
            String line = ln.trim();
            boolean isClosing = line.matches("^</[^>]+>\\s*$");
            boolean isSelf =
                    line.matches("(?i)^<[^>]+/>\\s*$") ||
                            line.matches("(?i)^<!(--|DOCTYPE).*") ||
                            line.matches("(?i)^<(br|hr|img|meta|link|input|source|area|base|col|embed|param|track)\\b.*");
            if (isClosing) indent = Math.max(0, indent - 1);
            for (int i = 0; i < indent; i++) out.append(IND);
            out.append(line).append('\n');
            boolean isOpening =
                    line.matches("(?i)^<[^/!][^>]*>\\s*$") &&
                            !isSelf &&
                            !line.matches("(?i)^<(script|style)\\b.*");
            if (isOpening) indent++;
        }
        return out.toString();
    }

    // ---------- tiny utility watcher ----------
    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
        }
    }

    // ---------- added helpers ----------

    /**
     * send plain text to live container
     */
    private void sendToLive(@NonNull String text) {
        if (liveSocket != null) {
            liveSocket.send(text);
        } else {
            printToConsole("(live not connected)\n");
        }
    }

    /**
     * get current content of a file by id
     */
    private String getFileContentById(@NonNull String id) {
        for (OpenFile f : availableFiles) {
            if (id.equals(f.id)) return f.content != null ? f.content : "";
        }
        return "";
    }

    private List<ProjectFile> buildProjectFilesFromEditor() {
        List<ProjectFile> out = new ArrayList<>();
        for (OpenFile f : availableFiles) {
            // f.id is your full path/name
            out.add(new ProjectFile(f.id, f.content != null ? f.content : ""));
        }
        return out;
    }

    // ---------- tiny HTTP helpers ----------
    private JSONObject httpPostJson(String urlStr, JSONObject json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        writer.write(json.toString());
        writer.flush();
        writer.close();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new RuntimeException("POST " + urlStr + " -> " + code + ": " + body);
        }
        return new JSONObject(body);
    }

    private JSONObject httpPutJson(String urlStr, JSONObject json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        writer.write(json.toString());
        writer.flush();
        writer.close();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new RuntimeException("PUT " + urlStr + " -> " + code + ": " + body);
        }
        return new JSONObject(body);
    }

    private JSONArray httpGetJsonArray(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.connect();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new RuntimeException("GET " + urlStr + " -> " + code + ": " + body);
        }
        return new JSONArray(body);
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        br.close();
        return sb.toString();
    }
}
