package com.example.aiassistantcoder;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.aiassistantcoder.ui.NeonBottomBarKt;
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
        // prefs/theme stuff unchanged...
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean useDarkTheme = preferences.getBoolean(PREF_DARK_THEME, false);
        AppCompatDelegate.setDefaultNightMode(
                useDarkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        String font = preferences.getString(PREF_FONT, "Default");
        switch (font) {
            default:
                setTheme(R.style.Theme_AiAssistantCoder);
                break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        final View container = findViewById(R.id.fragment_container);
        final View bottomBar = findViewById(R.id.compose_bottom_nav);

        // Insets handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (container != null) {
                // top padding so content isn't under status bar
                container.setPadding(
                        container.getPaddingLeft(),
                        bars.top,
                        container.getPaddingRight(),
                        container.getPaddingBottom()
                );

                // bottom padding so content scrolls above nav/gesture
                container.setPadding(
                        container.getPaddingLeft(),
                        container.getPaddingTop(),
                        container.getPaddingRight(),
                        bars.bottom
                );
            }
            // optional: bottom padding for the compose nav bar if you need it
            if (bottomBar != null) {
                bottomBar.setPadding(
                        bottomBar.getPaddingLeft(),
                        bottomBar.getPaddingTop(),
                        bottomBar.getPaddingRight(),
                        bars.bottom
                );
            }
            return insets;
        });

        // Firebase init (unchanged)
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

        // 🚀 Compose bottom bar
        ComposeView composeBottomNav = findViewById(R.id.compose_bottom_nav);
        if (composeBottomNav != null) {
            NeonBottomBarKt.setupNeonBottomBar(composeBottomNav, this);
        }

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
