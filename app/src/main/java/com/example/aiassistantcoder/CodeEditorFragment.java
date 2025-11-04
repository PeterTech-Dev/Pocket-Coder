package com.example.aiassistantcoder;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.KeyEvent;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import org.eclipse.tm4e.core.registry.IThemeSource;

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
    private Chip toggleAuto, toggleJudge0, toggleLive;

    // Diff bottom sheet
    private BottomSheetDialog diffDialog;
    private DiffAdapter diffAdapter;
    private final List<DiffLine> currentDiff = new ArrayList<>();
    private @Nullable Runnable onAcceptAction;
    private @Nullable String queuedNewCode;

    // ---- ViewModels / Buses ----
    private ConsoleViewModel consoleVM;
    private AiUpdateViewModel aiBus;
    private @Nullable SubscriptionReceipt<ContentChangeEvent> contentSub = null;

    // ---- Exec / handlers ----
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // =================== AUTOCOMPLETE (LOCAL / STUB) ===================
    private static final long AC_IDLE_MS = 300L;
    private final Handler acHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> acSuggestions = new ArrayList<>();
    private String acLastPrompt = "";
    private LinearLayout inlineAcContainer;
    private TextView inlineAcText;
    private @Nullable String inlineProposal;
    private final Runnable acDebounced = () -> {
        String prompt = extractPromptForCompletion();
        if (prompt == null || prompt.isEmpty()) { hideInlineSuggestion();; return; }
        if (prompt.equals(acLastPrompt)) return;
        acLastPrompt = prompt;
        fetchLocalSuggestions(prompt); // replace with Gemini call later
    };
    private final Map<String, List<Snippet>> snippetsByLang = new HashMap<>();
    private boolean snippetsLoaded = false;
    private static final class Snippet {
        final String trigger;
        final String insertText;
        Snippet(String t, String i) { trigger = t; insertText = i; }
    }
    private int lastSnippetTriggerLen = 0;


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
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                ProjectRepository.getInstance().saveProjectToFirestore(
                        currentProject,
                        new ProjectRepository.ProjectSaveCallback() {
                            @Override public void onSaved(String projectId) {
                                printToConsole("(saved) ✔\n");
                            }
                            @Override public void onError(Exception e) {
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

    // Judge0
    private String judge0BaseUrl = "http://10.0.2.2:2358";
    private final Map<String, Integer> languageMap = new HashMap<>();
    private String stdinText = "";

    // Live runner
    private String liveBaseUrl = "http://10.0.2.2:8080";
    private OkHttpClient ok;
    private WebSocket liveSocket;
    private String liveSessionId;
    private boolean liveConnecting = false;

    // Backend selection
    private enum Backend { AUTO, JUDGE0, LIVE }
    private Backend currentBackend = Backend.AUTO;

    // Back-compat
    private String pendingCode;

    // For asking the Activity to switch tabs to Console
    public interface PagerNav { void goToConsoleTab(); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_code_editor, container, false);

        codeEditor = v.findViewById(R.id.code_editor);
        btnRun     = v.findViewById(R.id.btn_run);
        progress   = v.findViewById(R.id.progress);
        initTextMateIfNeeded();
        applyTextMateLanguageFromAi();

        toggleAuto   = v.findViewById(R.id.toggle_auto);
        toggleJudge0 = v.findViewById(R.id.toggle_judge0);
        toggleLive   = v.findViewById(R.id.toggle_live);

        ok = new OkHttpClient();
        consoleVM = new ViewModelProvider(requireActivity()).get(ConsoleViewModel.class);

        // Echo & route console commands
        consoleVM.getCommandOut().observe(getViewLifecycleOwner(), cmd -> {
            if (cmd == null) return;

            if ("/restart".equalsIgnoreCase(cmd.trim())) {
                consoleVM.append("(live) restarting…\n");
                exec.execute(() -> {
                    stopLiveSession("user-restart");
                    startLiveSessionForCurrentCode();
                });
                return;
            }

            switch (currentBackend) {
                case LIVE:
                    sendToLive(cmd + "\n");
                    break;
                case JUDGE0:
                case AUTO:
                default:
                    stdinText = (stdinText == null || stdinText.isEmpty()) ? cmd : (stdinText + "\n" + cmd);
                    printToConsole("(stdin updated; re-running via Judge0)\n");
                    runViaJudge0(stdinText);
                    break;
            }
            FragmentActivity a = requireActivity();
            if (a instanceof PagerNav) ((PagerNav) a).goToConsoleTab();
        });

        // Observe AI updates from ChatFragment
        aiBus = new ViewModelProvider(requireActivity()).get(AiUpdateViewModel.class);
        aiBus.getUpdates().observe(getViewLifecycleOwner(), update -> {
            if (update == null) return;

            aiLang       = update.language;
            aiRuntime    = update.runtime;
            aiRunnerHint = update.notes;
            applyTextMateLanguageFromAi();

            String meta = "";
            if (aiLang != null && !aiLang.isEmpty()) meta += aiLang;
            if (aiRuntime != null && !aiRuntime.isEmpty()) meta += (meta.isEmpty() ? "" : " ") + "(" + aiRuntime + ")";
            if (!meta.isEmpty()) printToConsole("Detected: " + meta + "\n");
            if (aiRunnerHint != null && !aiRunnerHint.isEmpty()) printToConsole("// Notes: " + aiRunnerHint + "\n");

            boolean showDiff = Prefs.showDiffs(requireContext());
            applyAiCode(update.code, showDiff);   // persists + auto-restarts LIVE if needed
        });

        // Restore text (order: pendingCode > Project > SharedPreferences)
        if (pendingCode != null && !pendingCode.isEmpty()) {
            codeEditor.setText(pendingCode);
        } else {
            restoreFromProjectOrCache();
        }

        // Subscribe to Sora editor change events for debounced autosave + AI bus snapshot
        contentSub = codeEditor.subscribeEvent(
                ContentChangeEvent.class,
                (event, publisher) -> {
                    saveHandler.removeCallbacks(saveRunnable);
                    saveHandler.postDelayed(saveRunnable, SAVE_DEBOUNCE_MS);
                    if (aiBus != null) aiBus.publishEditorCode(getCode());
                    acHandler.removeCallbacks(acDebounced);
                    acHandler.postDelayed(acDebounced, AC_IDLE_MS);
                });

        codeEditor.setOnKeyListener((view1, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;

            boolean isTab   = (keyCode == KeyEvent.KEYCODE_TAB);
            boolean isEnter = (keyCode == KeyEvent.KEYCODE_ENTER);

            if ((isTab || isEnter)
                    && inlineAcContainer != null
                    && inlineAcContainer.getVisibility() == View.VISIBLE
                    && inlineProposal != null
                    && !inlineProposal.isEmpty()) {

                int line = codeEditor.getCursor().getLeftLine();
                int col  = codeEditor.getCursor().getLeftColumn();
                String proposal = inlineProposal;

                if (lastSnippetTriggerLen > 0) {
                    int startCol = col - lastSnippetTriggerLen;
                    if (startCol < 0) startCol = 0;
                    codeEditor.getText().delete(line, startCol, line, col);
                    col = startCol;
                }

                String toInsert = isEnter ? (proposal + "\n") : proposal;
                codeEditor.getText().insert(line, col, toInsert);

                hideInlineSuggestion();
                // reset for next time
                lastSnippetTriggerLen = 0;
                return true;
            }

            return false;
        });




        inlineAcContainer = v.findViewById(R.id.inline_ac_container);
        inlineAcText      = v.findViewById(R.id.inline_ac_text);
        main.post(() -> { if (aiBus != null) aiBus.publishEditorCode(getCode()); });
        ensureSnippetsLoaded();

        Bundle args = getArguments();
        if (args != null) {
            aiLang       = args.getString("ai_language");
            aiRuntime    = args.getString("ai_runtime");
            aiRunnerHint = args.getString("ai_notes");
            String aiCode = args.getString("ai_code");
            if (aiCode != null && !aiCode.isEmpty()) setCode(aiCode);

            String j0Override = args.getString("judge0_base_url");
            if (j0Override != null && !j0Override.trim().isEmpty()) judge0BaseUrl = j0Override.trim();

            String liveOverride = args.getString("live_base_url");
            if (liveOverride != null && !liveOverride.trim().isEmpty()) liveBaseUrl = liveOverride.trim();

            String stdinArg = args.getString("stdin");
            if (stdinArg != null) stdinText = stdinArg;
        }

        if ((aiLang != null && !aiLang.isEmpty()) || (aiRuntime != null && !aiRuntime.isEmpty())) {
            printToConsole("Detected: " + (aiLang != null ? aiLang : "?")
                    + (aiRuntime != null && !aiRuntime.isEmpty() ? " (" + aiRuntime + ")" : "") + "\n");
        }
        if (aiRunnerHint != null && !aiRunnerHint.isEmpty()) {
            printToConsole("// Notes: " + aiRunnerHint + "\n");
        }

        // Backend toggles
        toggleAuto.setOnClickListener(_v -> setBackend(Backend.AUTO));
        toggleJudge0.setOnClickListener(_v -> setBackend(Backend.JUDGE0));
        toggleLive.setOnClickListener(_v -> setBackend(Backend.LIVE));
        setBackend(Backend.AUTO);

        // Run button
        btnRun.setOnClickListener(_v -> {
            persistCodeIfPossible(); // ensure latest is saved before run
            maybePublishHtmlPreview();
            FragmentActivity a = requireActivity();
            if (a instanceof PagerNav) ((PagerNav) a).goToConsoleTab();

            switch (currentBackend) {
                case LIVE:
                    exec.execute(() -> {
                        stopLiveSession("manual-run");
                        startLiveSessionForCurrentCode();
                    });
                    break;
                case JUDGE0:
                    showStdinDialog();
                    break;
                case AUTO:
                default:
                    runAuto();
                    break;
            }
        });

        // Preload Judge0 languages
        exec.execute(this::ensureLanguagesLoaded);

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
        acHandler.removeCallbacks(acDebounced);
        hideInlineSuggestion();
        if (contentSub != null) {
            try {
                contentSub.unsubscribe();
            } catch (Throwable ignored) {
            }
            contentSub = null;
        }
        stopLiveSession("fragment-destroyed");
    }

    // ---------------- Project wiring & persistence ----------------

    /** Call this from the host Activity once you have the current Project. */
    public void setProject(@Nullable Project project) {
        this.currentProject = project;
        if (codeEditor != null && project != null) {
            String saved = project.getCode();
            if (saved != null && !saved.isEmpty()) {
                setCode(saved);
            } else {
                restoreFromCacheOnly();
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

    private void showDiffBottomSheet(List<DiffLine> diff, Runnable onAccept) {
        if (diffDialog == null) {
            View sheet = LayoutInflater.from(requireContext())
                    .inflate(R.layout.sheet_diff_preview, null, false);

            RecyclerView rv = sheet.findViewById(R.id.diff_list);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            diffAdapter = new DiffAdapter(currentDiff);
            rv.setAdapter(diffAdapter);

            TextView summary = sheet.findViewById(R.id.diff_summary);

            sheet.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
                diffDialog.dismiss();
                queuedNewCode = null;
                onAcceptAction = null;
            });
            sheet.findViewById(R.id.btn_apply).setOnClickListener(v -> {
                if (onAcceptAction != null) onAcceptAction.run();
                diffDialog.dismiss();
                if (queuedNewCode != null) {
                    String oldCode = getCode();
                    List<DiffLine> next = DiffUtilLite.diffLines(oldCode, queuedNewCode);
                    String applyCode = queuedNewCode;
                    queuedNewCode = null;
                    showDiffBottomSheet(next, () -> {
                        setCode(applyCode);
                        persistCodeIfPossible();
                    });
                }
            });

            diffDialog = new BottomSheetDialog(requireContext());
            diffDialog.setCancelable(false);
            diffDialog.setDismissWithAnimation(true);
            diffDialog.setContentView(sheet);

            diffDialog.setOnShowListener(dlg -> updateDiffHeader(summary));
        }

        currentDiff.clear();
        currentDiff.addAll(diff);
        onAcceptAction = onAccept;
        if (diffAdapter != null) diffAdapter.notifyDataSetChanged();

        View header = diffDialog.getDelegate().findViewById(R.id.diff_summary);
        if (header instanceof TextView) updateDiffHeader((TextView) header);

        if (!diffDialog.isShowing()) diffDialog.show();
    }

    private void updateDiffHeader(TextView summary) {
        int adds = 0, dels = 0;
        for (DiffLine d : currentDiff) { if (d.type == '+') adds++; else if (d.type == '-') dels++; }
        summary.setText("+" + adds + " · −" + dels);
    }

    // -------- tiny diff + adapter --------
    static final class DiffLine {
        final char type; // ' ' unchanged, '+' add, '-' del
        final String text;
        DiffLine(char t, String s){ type=t; text=s; }
    }

    static final class DiffUtilLite {
        static List<DiffLine> diffLines(String a, String b) {
            String[] A = a.split("\n", -1);
            String[] B = b.split("\n", -1);
            int n = A.length, m = B.length;
            int[][] dp = new int[n+1][m+1];
            for (int i=n-1;i>=0;i--) for (int j=m-1;j>=0;j--)
                dp[i][j] = A[i].equals(B[j]) ? dp[i+1][j+1]+1 : Math.max(dp[i+1][j], dp[i][j+1]);

            List<DiffLine> out = new ArrayList<>();
            int i=0,j=0;
            while(i<n && j<m){
                if (A[i].equals(B[j])) { out.add(new DiffLine(' ', A[i])); i++; j++; }
                else if (dp[i+1][j] >= dp[i][j+1]) { out.add(new DiffLine('-', A[i++])); }
                else { out.add(new DiffLine('+', B[j++])); }
            }
            while(i<n) out.add(new DiffLine('-', A[i++]));
            while(j<m) out.add(new DiffLine('+', B[j++]));
            return out;
        }
    }

    static final class DiffAdapter extends RecyclerView.Adapter<DiffVH> {
        private final List<DiffLine> lines;
        DiffAdapter(List<DiffLine> l){ lines = l; }
        @NonNull @Override public DiffVH onCreateViewHolder(@NonNull ViewGroup p, int v){
            View row = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_diff_line, p, false);
            return new DiffVH(row);
        }
        @Override public void onBindViewHolder(@NonNull DiffVH h, int i){ h.bind(lines.get(i)); }
        @Override public int getItemCount(){ return lines.size(); }
    }

    static final class DiffVH extends RecyclerView.ViewHolder {
        private final TextView t;
        DiffVH(View v){ super(v); t = v.findViewById(R.id.diff_line_text); }
        void bind(DiffLine l){
            t.setText((l.type==' ' ? "  " : (l.type+" ")) + l.text);
            int bg, fg;
            if (l.type=='+'){ bg=0x1928A745; fg=0xFF28A745; }
            else if (l.type=='-'){ bg=0x19D32F2F; fg=0xFFD32F2F; }
            else { bg=0x00000000; fg=0xFFB0B0B0; }
            t.setBackgroundColor(bg);
            t.setTextColor(fg);
        }
    }

    // ---------- Back-compat helpers ----------
    public void setCode(@Nullable String code) {
        pendingCode = code;
        if (codeEditor != null && code != null) {
            codeEditor.setText(looksLikeHtmlDoc(code) ? beautifyHtml(code) : code);
        }
        // keep Project + cache in sync (debounced)
        saveHandler.removeCallbacks(saveRunnable);
        saveHandler.postDelayed(saveRunnable, SAVE_DEBOUNCE_MS);
    }

    @NonNull public String getCode() {
        return codeEditor != null && codeEditor.getText() != null
                ? codeEditor.getText().toString()
                : (pendingCode != null ? pendingCode : "");
    }

    public void setAiHints(@Nullable String language, @Nullable String runtime, @Nullable String notes) {
        this.aiLang = language; this.aiRuntime = runtime; this.aiRunnerHint = notes;
    }

    // ---------- Backend selection ----------
    private void setBackend(Backend b) {
        currentBackend = b;
        toggleAuto.setChecked(b == Backend.AUTO);
        toggleJudge0.setChecked(b == Backend.JUDGE0);
        toggleLive.setChecked(b == Backend.LIVE);
    }

    private Backend autoChooseBackend() {
        String src = getCode();
        String l   = (aiLang == null ? "" : aiLang.toLowerCase());
        boolean looksInteractive =
                src.matches("(?s).*\\binput\\s*\\(.*")
                        || src.matches("(?s).*\\bscanf\\s*\\(.*")
                        || src.contains("process.stdin")
                        || src.contains("readline.createInterface")
                        || src.contains("Scanner(System.in)")
                        || src.toLowerCase().contains("enter your choice")
                        || src.matches("(?s).*(while\\s*\\(true\\)|for\\s*\\(;;\\)).*(input|stdin|scanf|readline|Scanner).*");
        if (looksInteractive) return Backend.LIVE;
        if (l.contains("typescript") || l.contains("dart") || l.contains("kotlin")) return Backend.LIVE;
        return Backend.JUDGE0;
    }

    private void runAuto() {
        Backend chosen = autoChooseBackend();
        if (chosen == Backend.LIVE) {
            setBackend(Backend.LIVE);
            exec.execute(() -> {
                stopLiveSession("auto-live");
                startLiveSessionForCurrentCode();
            });
            return;
        }
        setBackend(Backend.JUDGE0);
        runViaJudge0WithFallback();
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

    // ---------- Judge0 ----------
    private void showStdinDialog() {
        if (getContext() == null) { runViaJudge0(""); return; }

        final EditText input = new EditText(getContext());
        input.setText(stdinText);
        input.setMinLines(3);
        input.setMaxLines(10);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle("Program Input (stdin)")
                .setMessage("Enter the input your program should read (sent as-is).")
                .setView(input)
                .setPositiveButton("Run", (d, which) -> {
                    stdinText = input.getText().toString();
                    runViaJudge0(stdinText);
                })
                .setNeutralButton("Run without input", (d, which) -> runViaJudge0(""))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runViaJudge0WithFallback() {
        setRunning(true);
        printToConsole("⏵ Submitting to Judge0 (auto)…\n");
        exec.execute(() -> {
            try {
                ensureLanguagesLoaded();
                final String code = getCode();
                final String langHint = aiLang == null ? "" : aiLang.toLowerCase();

                int languageId = pickLanguageId(langHint);

                JSONObject payload = new JSONObject();
                payload.put("language_id", languageId);
                payload.put("source_code", b64(code));
                if (stdinText != null && !stdinText.isEmpty()) payload.put("stdin", b64(stdinText));

                String endpoint = judge0BaseUrl + "/submissions?base64_encoded=true&wait=true";
                JSONObject res = httpPostJson(endpoint, payload);

                emitJudge0Result(res);
            } catch (Exception e) {
                final String msg = e.getMessage();
                main.post(() -> {
                    printToConsole("\n⚠ Judge0 failed (auto): " + msg + "\n→ Switching to Live…\n");
                    setBackend(Backend.LIVE);
                    stopLiveSession("j0-fallback");
                    startLiveSessionForCurrentCode();
                });
            } finally {
                main.post(() -> setRunning(false));
            }
        });
    }

    private void runViaJudge0(String stdin) {
        final String code = getCode();
        final String langHint = aiLang == null ? "" : aiLang.toLowerCase();

        setRunning(true);
        printToConsole("⏵ Submitting to Judge0…\n");

        exec.execute(() -> {
            try {
                ensureLanguagesLoaded();
                int languageId = pickLanguageId(langHint);

                JSONObject payload = new JSONObject();
                payload.put("language_id", languageId);
                payload.put("source_code", b64(code));
                if (stdin != null && !stdin.isEmpty()) payload.put("stdin", b64(stdin));

                String endpoint = judge0BaseUrl + "/submissions?base64_encoded=true&wait=true";
                JSONObject res = httpPostJson(endpoint, payload);

                emitJudge0Result(res);
            } catch (Exception e) {
                printToConsole("\nError: " + e.getMessage() + "\n");
            } finally {
                setRunning(false);
            }
        });
    }

    private void emitJudge0Result(JSONObject res) throws Exception {
        String statusDesc = optStatusDesc(res);
        String stdout  = b64DecodeOrEmpty(res.optString("stdout", null));
        String stderr  = b64DecodeOrEmpty(res.optString("stderr", null));
        String compile = b64DecodeOrEmpty(res.optString("compile_output", null));
        String message = b64DecodeOrEmpty(res.optString("message", null));
        String time    = res.optString("time", null);
        int memory     = res.optInt("memory", -1);

        StringBuilder out = new StringBuilder();
        out.append("\nStatus: ").append(statusDesc).append("\n");
        if (!compile.isEmpty()) out.append("\n[compile]\n").append(compile).append("\n");
        if (!stdout.isEmpty())  out.append("\n[stdout]\n").append(stdout).append("\n");
        if (!stderr.isEmpty())  out.append("\n[stderr]\n").append(stderr).append("\n");
        if (!message.isEmpty()) out.append("\n[message]\n").append(message).append("\n");
        if (time != null || memory >= 0) {
            out.append("\n[metrics]\n");
            if (time != null) out.append("time: ").append(time).append("s\n");
            if (memory >= 0)  out.append("memory: ").append(memory).append(" KB\n");
        }
        printToConsole(out.toString());

        FragmentActivity a = requireActivity();
        if (a instanceof PagerNav) ((PagerNav) a).goToConsoleTab();
    }

    private void ensureLanguagesLoaded() {
        if (!languageMap.isEmpty()) return;
        try {
            JSONArray arr = httpGetJsonArray(judge0BaseUrl + "/languages");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.optInt("id", -1);
                String name = o.optString("name", "");
                if (id >= 0 && !name.isEmpty()) languageMap.put(name.toLowerCase(), id);
            }
            printToConsole("Loaded " + languageMap.size() + " languages.\n");
        } catch (Exception e) {
            printToConsole("Could not load /languages: " + e.getMessage() + "\n");
        }
    }

    private int pickLanguageId(String hint) {
        if (!languageMap.isEmpty() && hint != null && !hint.isEmpty()) {
            Integer exact = languageMap.get(hint);
            if (exact != null) return exact;
            for (Map.Entry<String, Integer> e : languageMap.entrySet()) {
                if (e.getKey().contains(hint)) return e.getValue();
            }
        }
        if (hint.contains("javascript") || hint.contains("node") || hint.contains("js")) return 63;
        if (hint.contains("python")) return 71;
        return 71;
    }

    // ---------- Live session helpers ----------
    private void startLiveSessionForCurrentCode() {
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
        printToConsole("⏵ Starting live session (python main.py)…\n");

        exec.execute(() -> {
            try {
                String projectId = createProject();
                String code = getCode();
                uploadFile(projectId, "main.py", code);

                if (!verifyFileExists(projectId, "main.py")) {
                    printToConsole("(upload verify failed; retrying once)\n");
                    uploadFile(projectId, "main.py", code);
                    if (!verifyFileExists(projectId, "main.py")) {
                        throw new RuntimeException("main.py did not appear in project after upload");
                    }
                }

                JSONObject payload = new JSONObject();
                payload.put("language", "python");
                payload.put("cmd", new JSONArray().put("python").put("main.py"));
                payload.put("projectId", projectId);
                payload.put("readOnlyFs", true);

                JSONObject res = httpPostJson(liveBaseUrl + "/session", payload);
                liveSessionId = res.optString("id", null);
                String wsPath = res.optString("ws", null);
                if (liveSessionId == null || wsPath == null)
                    throw new RuntimeException("Bad /session response");

                String wsUrl = (liveBaseUrl.startsWith("https://")
                        ? liveBaseUrl.replaceFirst("^https://", "wss://")
                        : liveBaseUrl.replaceFirst("^http://", "ws://")) + wsPath;

                main.post(() -> connectLiveWebSocket(wsUrl));
            } catch (Exception e) {
                final String msg = e.getMessage();
                main.post(() -> {
                    printToConsole("Live session error: " + msg + "\n");
                    liveConnecting = false;
                    setRunning(false);
                });
            }
        });
    }

    private void connectLiveWebSocket(String wsUrl) {
        Request req = new Request.Builder().url(wsUrl).build();
        liveSocket = ok.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                printToConsole("Live connected.\n");
                liveConnecting = false;
                setRunning(false);
                FragmentActivity a = requireActivity();
                if (a instanceof PagerNav) ((PagerNav) a).goToConsoleTab();
            }
            @Override public void onMessage(WebSocket webSocket, String text) { printToConsole(text); }
            @Override public void onMessage(WebSocket webSocket, ByteString bytes) { printToConsole(bytes.utf8()); }
            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                printToConsole("\n⏹ Live closing (" + code + "): " + reason + "\n");
                webSocket.close(1000, null);
                liveSocket = null;
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response r) {
                printToConsole("\n✖ Live socket error: " + t.getMessage() + "\n");
                liveSocket = null;
            }
        });
    }

    /** Gracefully stop/cleanup the live session (socket + server). */
    private void stopLiveSession(@Nullable String reason) {
        try {
            if (liveSocket != null) {
                try { liveSocket.close(1000, reason == null ? "closed" : reason); } catch (Throwable ignore) {}
            }
            liveSocket = null;
            if (liveSessionId != null) {
                try { httpDelete(liveBaseUrl + "/session/" + liveSessionId); } catch (Exception ignore) {}
            }
        } finally {
            liveSessionId = null;
            liveConnecting = false;
        }
    }

    private void sendToLive(String data) {
        if (liveSocket != null) {
            liveSocket.send(data);
        } else {
            printToConsole("No live session. Starting one…\n");
            setBackend(Backend.LIVE);
            startLiveSessionForCurrentCode();
            main.postDelayed(() -> {
                if (liveSocket != null) liveSocket.send(data);
            }, 600);
        }
    }

    // Project/file helpers for live
    private String createProject() throws Exception {
        JSONObject res = httpPostJson(liveBaseUrl + "/projects", new JSONObject());
        String id = res.optString("projectId", null);
        if (id == null) throw new RuntimeException("No projectId");
        return id;
    }
    private void uploadFile(@NonNull String projectId, @NonNull String relPath, @NonNull String content) throws Exception {
        JSONObject body = new JSONObject();
        body.put("path", relPath);
        body.put("content", content);
        httpPutJson(liveBaseUrl + "/projects/" + projectId + "/files", body);
    }
    private boolean verifyFileExists(@NonNull String projectId, @NonNull String relPath) {
        try {
            JSONObject obj = httpGetJsonObject(liveBaseUrl + "/projects/" + projectId + "/files?path=" + java.net.URLEncoder.encode(relPath, "UTF-8"));
            String c = obj.optString("content", null);
            if (c == null) return false;
            printToConsole("Uploaded " + relPath + " (" + c.length() + " bytes)\n");
            return true;
        } catch (Exception e) {
            printToConsole("verifyFileExists: " + e.getMessage() + "\n");
            return false;
        }
    }

    // ---------- HTTP utils ----------
    private JSONObject httpGetJsonObject(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.connect();
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();
        if (code < 200 || code >= 300) throw new RuntimeException("GET " + urlStr + " -> " + code + ": " + body);
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
        if (code < 200 || code >= 300) throw new RuntimeException("GET " + urlStr + " -> " + code + ": " + body);
        return new JSONArray(body);
    }
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
        if (code < 200 || code >= 300) throw new RuntimeException("POST " + urlStr + " -> " + code + ": " + body);
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
        if (code < 200 || code >= 300) throw new RuntimeException("PUT " + urlStr + " -> " + code + ": " + body);
        return new JSONObject(body);
    }
    private void httpDelete(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.connect();
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is != null) { try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            while (br.readLine() != null) { /* drain */ }
        }}
        conn.disconnect();
        // non-2xx is not fatal for stop; backend may not support DELETE
    }
    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    // ---------- UI helpers ----------
    private void setRunning(boolean running) {
        main.post(() -> {
            progress.setVisibility(running ? View.VISIBLE : View.GONE);
            btnRun.setEnabled(!running);
            toggleAuto.setEnabled(!running);
            toggleJudge0.setEnabled(!running);
            toggleLive.setEnabled(!running);
        });
    }
    private void printToConsole(String text) {
        if (consoleVM != null) consoleVM.append(text == null ? "" : text);
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

    // ---------- TextMate init + apply from AI ----------
    private void initTextMateIfNeeded() {
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                    new AssetsFileResolver(requireContext().getAssets())
            );

            GrammarRegistry.getInstance().loadGrammars("tm/languages.json");

            ThemeRegistry themeRegistry = ThemeRegistry.getInstance();
            String themeName = "dark"; // must match file name without .json
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

            // 4) tell the editor to use the TextMate color scheme from that theme
            codeEditor.setColorScheme(TextMateColorScheme.create(themeRegistry));

            // 6) pick some language so it highlights
            codeEditor.setEditorLanguage(TextMateLanguage.create("source.python", true));

        } catch (Throwable t) {
            t.printStackTrace();
            printToConsole("TextMate init failed: " + t.getMessage() + "\n");
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
        } catch (Exception ignored) {}
        return res.optString("status", "?");
    }

    // ---------- AUTOCOMPLETE helpers (popup + local suggestions) ----------
    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density);
    }

    private void showInlineSuggestion(@NonNull String suggestion) {
        inlineProposal = suggestion;
        if (inlineAcText != null) inlineAcText.setText(suggestion);
        if (inlineAcContainer != null && inlineAcContainer.getVisibility() != View.VISIBLE) {
            inlineAcContainer.setVisibility(View.VISIBLE);
        }

        if (inlineAcContainer != null) {
            inlineAcContainer.setTranslationX(dp(8));
            inlineAcContainer.setTranslationY(dp(8));
        }
    }

    private void hideInlineSuggestion() {
        inlineProposal = null;
        if (inlineAcContainer != null) inlineAcContainer.setVisibility(View.GONE);
    }

    /** Safely extract ~last 20 lines before caret for AI prompt. */
    private String extractPromptForCompletion() {
        if (codeEditor == null || codeEditor.getText() == null) return "";
        final String all = codeEditor.getText().toString();
        int caret = codeEditor.getCursor() != null ? codeEditor.getCursor().getLeft() : 0;
        if (caret <= 0 || all.isEmpty()) return "";
        if (caret > all.length()) caret = all.length();

        int idx = caret;
        for (int k = 0; k < 20 && idx > 0; k++) {
            int next = all.lastIndexOf('\n', idx - 1);
            if (next < 0) { idx = 0; break; }
            idx = next;
        }
        int from = (idx <= 0) ? 0 : (idx + 1);
        if (from > caret) from = caret;
        // Guard against StringIndexOutOfBounds (begin <= end)
        if (from < 0) from = 0;
        return all.substring(from, caret);
    }

    /** JSON-driven autocomplete from assets/snippets.json */
    private void fetchLocalSuggestions(String prompt) {
        ensureSnippetsLoaded();
        if (prompt == null) {
            hideInlineSuggestion();
            lastSnippetTriggerLen = 0;
            return;
        }
        String lang = currentLanguageKey();
        List<Snippet> list = snippetsByLang.get(lang);
        if (list == null || list.isEmpty()) {
            hideInlineSuggestion();
            lastSnippetTriggerLen = 0;
            return;
        }

        String bestInsert = null;
        int bestLen = -1;

        for (Snippet sn : list) {
            String trig = sn.trigger;
            if (prompt.endsWith(trig) && trig.length() > bestLen) {
                bestInsert = renderSnippet(sn.insertText);
                bestLen = trig.length();
            }
        }

        if (bestInsert != null && !bestInsert.isEmpty()) {
            showInlineSuggestion(bestInsert);
            lastSnippetTriggerLen = bestLen;
        } else {
            hideInlineSuggestion();
            lastSnippetTriggerLen = 0;
        }
    }


    private void ensureSnippetsLoaded() {
        if (snippetsLoaded || getContext() == null) return;
        try (InputStream is = requireContext().getAssets().open("snippets.json");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            JSONObject root = new JSONObject(sb.toString());
            // Android-friendly iteration
            for (java.util.Iterator<String> it = root.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONArray arr = root.optJSONArray(key);
                if (arr == null) continue;

                ArrayList<Snippet> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String trig = o.optString("trigger", "");
                    String text = o.optString("insertText", "");
                    if (!trig.isEmpty() && !text.isEmpty()) {
                        list.add(new Snippet(trig, text));
                    }
                }
                if (!list.isEmpty()) {
                    snippetsByLang.put(key.toLowerCase(), list);
                }
            }

            snippetsLoaded = true;
            printToConsole("Snippets loaded (" + snippetsByLang.size() + " langs).\n");
        } catch (Exception e) {
            printToConsole("Snippet load error: " + e.getMessage() + "\n");
        }
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
        // fallback by content
        String src = getCode().trim().toLowerCase();
        if (src.startsWith("<!doctype") || src.contains("<html")) return "html";
        return "javascript";
    }

    private String renderSnippet(String insertText) {
        String s = insertText;
        s = s.replaceAll("\\$\\{\\d+:([^}]+)\\}", "$1"); // ${1:foo} -> foo
        s = s.replaceAll("\\$\\d+", "");                 // $1, $2, $0 -> ""
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
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
