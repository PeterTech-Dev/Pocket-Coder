package com.example.aiassistantcoder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    // Shared prefs bucket
    private static final String PREFS_NAME = "prefs";

    // Existing keys
    private static final String PREF_DARK_THEME = "dark_theme";

    // New font keys (public so other screens can read them)
    public static final String PREF_EDITOR_FONT   = "pref_editor_font";   // editor font value
    public static final String PREF_CONSOLE_FONT  = "pref_console_font";  // console font value

    // Compatibility: some code reads this key for the editor font
    private static final String KEY_CODE_FONT_FAMILY = "code_font_family"; // mirror of PREF_EDITOR_FONT

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // ---- Views ----
        SwitchMaterial themeSwitch       = view.findViewById(R.id.theme_switch);
        SwitchMaterial sAuto             = view.findViewById(R.id.switch_auto_apply);
        SwitchMaterial sDiff             = view.findViewById(R.id.switch_show_diffs);
        Spinner editorFontSpinner        = view.findViewById(R.id.spinner_editor_font);
        Spinner consoleFontSpinner       = view.findViewById(R.id.spinner_console_font);
        View clearHistoryButton          = view.findViewById(R.id.clear_history_button);

        Context ctx = requireContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // ---- Theme switch ----
        boolean useDarkTheme = prefs.getBoolean(PREF_DARK_THEME, false);
        themeSwitch.setChecked(useDarkTheme);
        themeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            prefs.edit().putBoolean(PREF_DARK_THEME, isChecked).apply();
            int nightMode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(nightMode);
            requireActivity().recreate(); // recreate only for theme changes
        });

        // ---- Other switches ----
        sAuto.setChecked(Prefs.autoApply(ctx));
        sDiff.setChecked(Prefs.showDiffs(ctx));
        sAuto.setOnCheckedChangeListener((b, v) -> Prefs.setAutoApply(ctx, v));
        sDiff.setOnCheckedChangeListener((b, v) -> Prefs.setShowDiffs(ctx, v));

        // ---- Font spinners (Editor + Console) ----
        ArrayAdapter<CharSequence> fontAdapter = ArrayAdapter.createFromResource(
                ctx, R.array.code_fonts_labels, android.R.layout.simple_spinner_item);
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        editorFontSpinner.setAdapter(fontAdapter);
        consoleFontSpinner.setAdapter(fontAdapter);

        // Restore selections from saved "values" array tokens
        String editorSaved  = prefs.getString(PREF_EDITOR_FONT,  "system_monospace");
        String consoleSaved = prefs.getString(PREF_CONSOLE_FONT, "system_monospace");
        setSpinnerSelectionByValue(editorFontSpinner, editorSaved);
        setSpinnerSelectionByValue(consoleFontSpinner, consoleSaved);

        // Save on change (no recreate; Editor/Console fragments will re-apply in onResume)
        editorFontSpinner.setOnItemSelectedListener(new SaveFontValueListener(prefs, PREF_EDITOR_FONT, /*mirrorToCodeFamily=*/true));
        consoleFontSpinner.setOnItemSelectedListener(new SaveFontValueListener(prefs, PREF_CONSOLE_FONT, /*mirrorToCodeFamily=*/false));

        // ---- Clear history ----
        clearHistoryButton.setOnClickListener(v -> {
            ProjectRepository.getInstance().clearProjects();

            Fragment projectsFragment = getParentFragmentManager().findFragmentByTag("ProjectsFragment");
            if (projectsFragment != null) {
                getParentFragmentManager()
                        .beginTransaction()
                        .detach(projectsFragment)
                        .attach(projectsFragment)
                        .commit();
            }
            Toast.makeText(ctx, "Project history cleared", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    /** Select spinner item whose corresponding value equals savedValue. */
    private void setSpinnerSelectionByValue(Spinner spinner, String savedValue) {
        String[] values = getResources().getStringArray(R.array.code_fonts_values);
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(savedValue)) { idx = i; break; }
        }
        spinner.setSelection(idx);
    }

    /** Persists chosen font "value" to the given key; optionally mirrors to code font family key. */
    private class SaveFontValueListener implements AdapterView.OnItemSelectedListener {
        private final SharedPreferences prefs;
        private final String prefKey;
        private final boolean mirrorToCodeFamily;

        SaveFontValueListener(SharedPreferences prefs, String prefKey, boolean mirrorToCodeFamily) {
            this.prefs = prefs;
            this.prefKey = prefKey;
            this.mirrorToCodeFamily = mirrorToCodeFamily;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String[] values = getResources().getStringArray(R.array.code_fonts_values);
            if (position >= 0 && position < values.length) {
                String val = values[position];
                // Save to the main key
                prefs.edit().putString(prefKey, val).apply();
                // Mirror editor font to code_font_family for compatibility with CodeEditorFragment
                if (mirrorToCodeFamily) {
                    prefs.edit().putString(KEY_CODE_FONT_FAMILY, val).apply();
                }
            }
        }

        @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
    }
}
