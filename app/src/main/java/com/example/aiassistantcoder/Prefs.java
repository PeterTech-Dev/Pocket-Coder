package com.example.aiassistantcoder;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}

    // Single prefs file
    private static final String FILE = "aiassistant_prefs";

    // Toggles
    private static final String K_AUTO_APPLY  = "auto_apply_ai_code";
    private static final String K_SHOW_DIFFS  = "show_diffs_before_apply";

    // Fonts
    public static final String KEY_EDITOR_FONT_FAMILY  = "editor_font_family";   // "monospace", "jetbrains", etc
    public static final String KEY_EDITOR_FONT_SIZE    = "editor_font_size";     // float sp
    public static final String KEY_CONSOLE_FONT_FAMILY = "console_font_family";
    public static final String KEY_CONSOLE_FONT_SIZE   = "console_font_size";

    // --- internal ---
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // --- toggles ---
    public static boolean autoApply(Context c) {
        return sp(c).getBoolean(K_AUTO_APPLY, true); // default ON
    }
    public static void setAutoApply(Context c, boolean v) {
        sp(c).edit().putBoolean(K_AUTO_APPLY, v).apply();
    }

    public static boolean showDiffs(Context c) {
        return sp(c).getBoolean(K_SHOW_DIFFS, true); // default ON
    }
    public static void setShowDiffs(Context c, boolean v) {
        sp(c).edit().putBoolean(K_SHOW_DIFFS, v).apply();
    }

    // --- editor font ---
    public static String editorFont(Context c) {
        return sp(c).getString(KEY_EDITOR_FONT_FAMILY, "monospace");
    }
    public static void setEditorFont(Context c, String family) {
        sp(c).edit().putString(KEY_EDITOR_FONT_FAMILY, family == null ? "monospace" : family).apply();
    }
    public static float editorFontSize(Context c) {
        SharedPreferences p = sp(c);
        if (p.contains(KEY_EDITOR_FONT_SIZE)) {
            try { return p.getFloat(KEY_EDITOR_FONT_SIZE, 14f); } catch (ClassCastException ignored) {}
            try { return (float) p.getInt(KEY_EDITOR_FONT_SIZE, 14); } catch (ClassCastException ignored) {}
        }
        return 14f;
    }
    public static void setEditorFontSize(Context c, float spVal) {
        sp(c).edit().putFloat(KEY_EDITOR_FONT_SIZE, spVal).apply();
    }

    // --- console font ---
    public static String consoleFont(Context c) {
        return sp(c).getString(KEY_CONSOLE_FONT_FAMILY, "monospace");
    }
    public static void setConsoleFont(Context c, String family) {
        sp(c).edit().putString(KEY_CONSOLE_FONT_FAMILY, family == null ? "monospace" : family).apply();
    }
    public static float consoleFontSize(Context c) {
        SharedPreferences p = sp(c);
        if (p.contains(KEY_CONSOLE_FONT_SIZE)) {
            try { return p.getFloat(KEY_CONSOLE_FONT_SIZE, 13f); } catch (ClassCastException ignored) {}
            try { return (float) p.getInt(KEY_CONSOLE_FONT_SIZE, 13); } catch (ClassCastException ignored) {}
        }
        return 13f;
    }
    public static void setConsoleFontSize(Context c, float spVal) {
        sp(c).edit().putFloat(KEY_CONSOLE_FONT_SIZE, spVal).apply();
    }
}
