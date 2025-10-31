package com.example.aiassistantcoder;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "prefs";
    private static final String PREF_DARK_THEME = "dark_theme";
    private static final String PREF_FONT = "font";
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // ---- 1) Load prefs BEFORE super.onCreate to apply theme/night mode early ----
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Night mode: set an explicit mode either way (fixes “switches to light in Settings”)
        boolean useDarkTheme = preferences.getBoolean(PREF_DARK_THEME, false);
        AppCompatDelegate.setDefaultNightMode(
                useDarkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        // Font theme (scoped app theme variants)
        String font = preferences.getString(PREF_FONT, "Default");
        switch (font) {
            case "Roboto":
                setTheme(R.style.Theme_AiAssistantCoder_Roboto);
                break;
            case "Open Sans":
                setTheme(R.style.Theme_AiAssistantCoder_OpenSans);
                break;
            case "Lato":
                setTheme(R.style.Theme_AiAssistantCoder_Lato);
                break;
            default:
                setTheme(R.style.Theme_AiAssistantCoder);
                break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ---- 2) Edge-to-edge + safe-area padding for content and bottom nav ----
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        final View container = findViewById(R.id.fragment_container);
        final BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // Apply insets: add top padding to container; bottom padding to container & nav
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Top padding for content (so first fragment isn't under the status bar/camera cutout)
            if (container != null) {
                container.setPadding(
                        container.getPaddingLeft(),
                        bars.top,
                        container.getPaddingRight(),
                        container.getPaddingBottom()
                );
            }

            // Bottom padding for both the container and the nav (gesture bar/home area)
            if (container != null) {
                container.setPadding(
                        container.getPaddingLeft(),
                        container.getPaddingTop(),
                        container.getPaddingRight(),
                        bars.bottom // make content scroll above the nav/gesture area
                );
            }
            if (bottomNav != null) {
                bottomNav.setPadding(
                        bottomNav.getPaddingLeft(),
                        bottomNav.getPaddingTop(),
                        bottomNav.getPaddingRight(),
                        bars.bottom
                );
            }
            return insets;
        });

        // ---- 3) Firebase init + offline cache ----
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase manually initialized.");
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        firestore.setFirestoreSettings(settings);

        verifyFirebaseConnection();

        // ---- 4) Bottom navigation ----
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.navigation_projects) {
                selectedFragment = new ProjectsFragment();
            } else if (itemId == R.id.navigation_settings) {
                selectedFragment = new SettingsFragment();
            } else if (itemId == R.id.navigation_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });

        // Default tab
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }
    }

    private void verifyFirebaseConnection() {
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            FirebaseOptions opts = app.getOptions();

            Log.d("FBVerify", "projectId=" + opts.getProjectId());
            Log.d("FBVerify", "applicationId(Google App ID)=" + opts.getApplicationId());
            Log.d("FBVerify", "apiKey=" + opts.getApiKey());
            Log.d("FBVerify", "databaseUrl=" + opts.getDatabaseUrl());

            // Simple write object (you may keep your existing repo logic elsewhere)
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> test = new HashMap<>();
            test.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            Log.e("FBVerify", "Firebase verification failed", e);
        }
    }
}
