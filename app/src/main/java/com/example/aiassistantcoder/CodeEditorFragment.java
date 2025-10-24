package com.example.aiassistantcoder;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import io.github.rosemoe.sora.widget.CodeEditor;

public class CodeEditorFragment extends Fragment {

    private CodeEditor codeEditor;
    private Project currentProject;

    private FloatingActionButton btnRun;
    private TextView console;
    private ProgressBar progress;

    // AI hints (optional)
    private String aiLang;
    private String aiRuntime;
    private String aiRunnerHint;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // ---------- lifecycle ----------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_code_editor, container, false);

        codeEditor = v.findViewById(R.id.code_editor);
        btnRun     = v.findViewById(R.id.btn_run);
        console    = v.findViewById(R.id.console_output);
        progress   = v.findViewById(R.id.progress);

        console.setMovementMethod(new ScrollingMovementMethod());

        // Load hints + code from arguments or intent
        Bundle args = getArguments();
        if (args != null) {
            aiLang       = args.getString("ai_language");
            aiRuntime    = args.getString("ai_runtime");
            aiRunnerHint = args.getString("ai_notes");
            String aiCode = args.getString("ai_code");
            if (aiCode != null && !aiCode.isEmpty()) setCode(aiCode);
        }
        if (getActivity() != null && getActivity().getIntent() != null) {
            if (aiLang == null)       aiLang       = getActivity().getIntent().getStringExtra("ai_language");
            if (aiRuntime == null)    aiRuntime    = getActivity().getIntent().getStringExtra("ai_runtime");
            if (aiRunnerHint == null) aiRunnerHint = getActivity().getIntent().getStringExtra("ai_notes");
            if (codeEditor.getText().length() == 0) {
                String aiCode = getActivity().getIntent().getStringExtra("ai_code");
                if (aiCode != null && !aiCode.isEmpty()) setCode(aiCode);
            }
        }

        if ((aiLang != null && !aiLang.isEmpty()) || (aiRuntime != null && !aiRuntime.isEmpty())) {
            printToConsole("Detected: " + (aiLang != null ? aiLang : "?")
                    + (aiRuntime != null && !aiRuntime.isEmpty() ? " (" + aiRuntime + ")" : "") + "\n");
        }
        if (aiRunnerHint != null && !aiRunnerHint.isEmpty()) {
            printToConsole("// Notes: " + aiRunnerHint + "\n");
        }

        btnRun.setOnClickListener(v1 -> routeAndRun());
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentProject != null && currentProject.getCode() != null
                && codeEditor.getText().length() == 0) {
            codeEditor.setText(currentProject.getCode());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentProject != null && codeEditor != null) {
            currentProject.setCode(codeEditor.getText().toString());
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                ProjectRepository.getInstance().saveProjectToFirestore(
                        currentProject,
                        new ProjectRepository.ProjectSaveCallback() {
                            @Override public void onSaved(String projectId) {}
                            @Override public void onError(Exception e) {}
                        }
                );
            }
        }
    }

    // External hooks
    public void setProject(Project project) { this.currentProject = project; }
    public void setCode(String code) {
        if (codeEditor != null) codeEditor.setText(code);
        if (currentProject != null) currentProject.setCode(code);
    }

    // ---------- runners ----------

    private void routeAndRun() {
        String lang = (aiLang == null ? "" : aiLang).toLowerCase().trim();
        String script = codeEditor.getText().toString();

        if (lang.contains("python")) { runPythonCode(script); return; }
        if (lang.contains("html")) {
            printToConsole("⏵ HTML detected. (Preview not implemented yet.)\n");
            return;
        }
        if (lang.contains("javascript") || lang.contains("js") || lang.isEmpty()) {
            runJavaScript(script);
            return;
        }

        printToConsole("ℹ Unsupported language: " + aiLang + " — running as JavaScript.\n");
        runJavaScript(script);
    }

    // ---- Python with stdout/stderr + input() support ----

    // Writer that receives Python prints / tracebacks
    public static class PyConsoleWriter {
        private final Consumer<String> sink;
        public PyConsoleWriter(Consumer<String> sink) { this.sink = sink; }
        @SuppressWarnings("unused")  // called from Python
        public void write(String s) { if (s != null) sink.accept(s); }
    }

    // Provides lines for Python input()
    public class PyInputProvider {
        private final SynchronousQueue<String> queue = new SynchronousQueue<>();
        @SuppressWarnings("unused")  // called from Python
        public String readLine() {
            main.post(this::showPrompt);
            try { return queue.take(); } catch (InterruptedException e) { return ""; }
        }
        private void showPrompt() {
            if (getContext() == null) { queue.offer(""); return; }
            final EditText et = new EditText(getContext());
            new AlertDialog.Builder(getContext())
                    .setTitle("Python input()")
                    .setView(et)
                    .setCancelable(false)
                    .setPositiveButton("OK", (d, w) -> queue.offer(et.getText().toString()))
                    .setNegativeButton("Cancel", (d, w) -> queue.offer(""))
                    .show();
        }
    }

    private void runPythonCode(String code) {
        setRunning(true);
        printToConsole("⏵ Running Python...\n");

        exec.submit(() -> {
            try {
                Python py = Python.getInstance();
                PyObject bridge = py.getModule("bridge");

                // Hook stdout/stderr and input()
                PyConsoleWriter writer = new PyConsoleWriter(this::printToConsole);
                PyInputProvider inputProvider = new PyInputProvider();
                bridge.callAttr("hook_io", writer, inputProvider);

                // Execute the script
                bridge.callAttr("run_code", code);

                printToConsole("\n✔ Finished.\n");
            } catch (Throwable t) {
                printToConsole("\n✖ Python error: " + t.getMessage() + "\n");
            } finally {
                setRunning(false);
            }
        });
    }

    // ---- JavaScript (Rhino) with print() ----
    private void runJavaScript(String script) {
        setRunning(true);
        printToConsole("⏵ Running JavaScript...\n");

        Future<Void> task = exec.submit((Callable<Void>) () -> {
            try {
                Context cx = Context.enter();
                cx.setOptimizationLevel(-1); // Android friendly
                Scriptable scope = cx.initStandardObjects();

                // print(...)
                ScriptableObject.putProperty(scope, "print",
                        new org.mozilla.javascript.BaseFunction() {
                            @Override
                            public Object call(Context context, Scriptable scope,
                                               Scriptable thisObj, Object[] args) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < args.length; i++) {
                                    if (i > 0) sb.append(" ");
                                    sb.append(Context.toString(args[i]));
                                }
                                printToConsole(sb.toString() + "\n");
                                return Context.getUndefinedValue();
                            }
                        });

                Object result = cx.evaluateString(scope, script, "user.js", 1, null);
                printToConsole("\n✔ Result: " + String.valueOf(result) + "\n");
            } catch (Throwable t) {
                printToConsole("\n✖ Error: " + t.getMessage() + "\n");
            } finally {
                Context.exit();
            }
            return null;
        });

        // 5s timeout guard
        exec.submit(() -> {
            try { task.get(5, TimeUnit.SECONDS); }
            catch (Exception e) { task.cancel(true); printToConsole("\n⏱️ Timed out after 5s\n"); }
            finally { setRunning(false); }
        });
    }

    // ---------- UI helpers ----------

    private void setRunning(boolean running) {
        main.post(() -> {
            progress.setVisibility(running ? View.VISIBLE : View.GONE);
            btnRun.setEnabled(!running);
        });
    }

    private void printToConsole(String text) {
        main.post(() -> {
            console.append(text);
            int scrollAmount = console.getLayout() == null ? 0 :
                    console.getLayout().getLineTop(console.getLineCount()) - console.getHeight();
            if (scrollAmount > 0) console.scrollTo(0, scrollAmount);
            else console.scrollTo(0, 0);
        });
    }
}
