package com.example.aiassistantcoder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "prefs";
    private static final String PREF_DARK_THEME = "dark_theme";
    private static final String PREF_FONT = "font";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        SwitchMaterial themeSwitch = view.findViewById(R.id.theme_switch);
        Spinner fontSpinner = view.findViewById(R.id.font_spinner);
        Button clearHistoryButton = view.findViewById(R.id.clear_history_button);

        SharedPreferences preferences = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean useDarkTheme = preferences.getBoolean(PREF_DARK_THEME, false);
        themeSwitch.setChecked(useDarkTheme);

        // Listener for Dark Mode Switch
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(PREF_DARK_THEME, isChecked).apply();
            int nightMode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(nightMode);
            getActivity().recreate();
        });

        // Font Spinner Setup
        String[] fonts = {"Default", "Roboto", "Open Sans", "Lato"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, fonts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fontSpinner.setAdapter(adapter);

        String selectedFont = preferences.getString(PREF_FONT, "Default");
        fontSpinner.setSelection(adapter.getPosition(selectedFont));

        fontSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String fontName = (String) parent.getItemAtPosition(position);
                if (!fontName.equals(preferences.getString(PREF_FONT, "Default"))) {
                    preferences.edit().putString(PREF_FONT, fontName).apply();
                    getActivity().recreate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // Clear History Button
        clearHistoryButton.setOnClickListener(v -> {
            ProjectRepository.getInstance().clearProjects();
            // Refresh the projects fragment if it's visible
            Fragment projectsFragment = getParentFragmentManager().findFragmentByTag("ProjectsFragment");
            if (projectsFragment != null) {
                getParentFragmentManager().beginTransaction().detach(projectsFragment).attach(projectsFragment).commit();
            }
            Toast.makeText(getContext(), "Project history cleared", Toast.LENGTH_SHORT).show();
        });

        return view;
    }
}
