package com.example.aiassistantcoder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class ConsoleFragment extends Fragment {

    private WebView preview;
    private TextView consoleText;
    private ScrollView consoleScroll;
    private EditText input;
    private ImageButton btnSend;

    private ConsoleViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_console, container, false);

        preview = v.findViewById(R.id.preview_webview);
        consoleText = v.findViewById(R.id.console_text);
        consoleScroll = v.findViewById(R.id.console_scroll);
        input = v.findViewById(R.id.input_command);
        btnSend = v.findViewById(R.id.btn_send);

        vm = new ViewModelProvider(requireActivity()).get(ConsoleViewModel.class);

        // Show logs + autoscroll
        vm.getLogs().observe(getViewLifecycleOwner(), text -> {
            consoleText.setText(text);
            consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
        });

        // Optional preview
        preview.getSettings().setJavaScriptEnabled(true);
        vm.getPreviewUrl().observe(getViewLifecycleOwner(), url -> {
            if (url != null && !url.isEmpty()) preview.loadUrl(url);
        });

        Runnable sendNow = () -> {
            String cmd = input.getText().toString();
            if (cmd == null) return;
            String trimmed = cmd.trim();
            if (trimmed.isEmpty()) return;

            if ("/clear".equalsIgnoreCase(trimmed)) {
                vm.clearConsole();
                input.setText("");
                return;
            }

            // Echo and forward to editor
            vm.append("> " + cmd + "\n");
            vm.sendCommand(trimmed);  // Editor will interpret (stdin or /restart)
            input.setText("");
        };

        btnSend.setOnClickListener(_v -> sendNow.run());
        input.setOnEditorActionListener((tv, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendNow.run();
                return true;
            }
            return false;
        });

        return v;
    }
}
