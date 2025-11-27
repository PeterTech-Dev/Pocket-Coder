package com.example.aiassistantcoder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;

import com.example.aiassistantcoder.ui.SettingsKt;
import com.example.aiassistantcoder.ui.SnackBarApp;

import java.util.Arrays;
import java.util.List;

import kotlin.Unit;

public class SettingsFragment extends Fragment {

    // Shared prefs bucket
    private static final String PREFS_NAME = "prefs";

    // Existing keys
    private static final String PREF_DARK_THEME = "dark_theme";

    // New font keys (public so other screens can read them)
    public static final String PREF_EDITOR_FONT = "pref_editor_font";   // editor font value
    public static final String PREF_CONSOLE_FONT = "pref_console_font";  // console font value

    // Compatibility: some code reads this key for the editor font
    private static final String KEY_CODE_FONT_FAMILY = "code_font_family"; // mirror of PREF_EDITOR_FONT

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        Context ctx = requireContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // ---- current values ----
        boolean useDarkTheme = prefs.getBoolean(PREF_DARK_THEME, false);
        boolean autoApply = Prefs.autoApply(ctx);
        boolean showDiffs = Prefs.showDiffs(ctx);

        String[] fontLabelsArr = getResources().getStringArray(R.array.code_fonts_labels);
        String[] fontValuesArr = getResources().getStringArray(R.array.code_fonts_values);
        List<String> fontLabels = Arrays.asList(fontLabelsArr);

        String editorSaved = prefs.getString(PREF_EDITOR_FONT, "system_monospace");
        String consoleSaved = prefs.getString(PREF_CONSOLE_FONT, "system_monospace");

        int editorIndex = indexOfValue(fontValuesArr, editorSaved);
        int consoleIndex = indexOfValue(fontValuesArr, consoleSaved);

        // ---- ComposeView ----
        ComposeView composeView = view.findViewById(R.id.settings_compose);

        SettingsKt.bindSettingsContent(
                composeView,
                useDarkTheme,
                autoApply,
                showDiffs,
                fontLabels,
                editorIndex,
                consoleIndex,
                // onDarkThemeChange
                isChecked -> {
                    prefs.edit().putBoolean(PREF_DARK_THEME, isChecked).apply();
                    int nightMode = isChecked
                            ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO;
                    AppCompatDelegate.setDefaultNightMode(nightMode);
                    requireActivity().recreate(); // same behaviour as before
                    return Unit.INSTANCE;
                },
                // onAutoApplyChange
                value -> {
                    Prefs.setAutoApply(ctx, value);
                    return Unit.INSTANCE;
                },
                // onShowDiffChange
                value -> {
                    Prefs.setShowDiffs(ctx, value);
                    return Unit.INSTANCE;
                },
                // onEditorFontChange (index -> save value + mirror to code_font_family)
                index -> {
                    int safe = clampIndex(index, fontValuesArr.length);
                    String val = fontValuesArr[safe];
                    prefs.edit()
                            .putString(PREF_EDITOR_FONT, val)
                            .putString(KEY_CODE_FONT_FAMILY, val)
                            .apply();
                    return Unit.INSTANCE;
                },
                // onConsoleFontChange
                index -> {
                    int safe = clampIndex(index, fontValuesArr.length);
                    String val = fontValuesArr[safe];
                    prefs.edit()
                            .putString(PREF_CONSOLE_FONT, val)
                            .apply();
                    return Unit.INSTANCE;
                },
                // onClearHistory
                () -> {
                    ProjectRepository.getInstance().clearProjects();

                    Fragment projectsFragment =
                            getParentFragmentManager().findFragmentByTag("ProjectsFragment");
                    if (projectsFragment != null) {
                        getParentFragmentManager()
                                .beginTransaction()
                                .detach(projectsFragment)
                                .commit();

                        getParentFragmentManager()
                                .beginTransaction()
                                .attach(projectsFragment)
                                .commit();
                    }
                    View root = requireActivity().findViewById(android.R.id.content);

                    SnackBarApp.INSTANCE.show(
                            root,
                            "Project history cleared",
                            SnackBarApp.Type.SUCCESS
                    );

                    return Unit.INSTANCE;
                }
        );

        return view;
    }

    private int indexOfValue(String[] arr, String value) {
        if (value == null) value = "system_monospace";
        for (int i = 0; i < arr.length; i++) {
            if (value.equals(arr[i])) return i;
        }
        return 0;
    }

    private int clampIndex(int index, int length) {
        if (length <= 0) return 0;
        if (index < 0) return 0;
        if (index >= length) return length - 1;
        return index;
    }
}
