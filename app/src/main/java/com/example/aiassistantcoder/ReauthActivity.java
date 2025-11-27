package com.example.aiassistantcoder;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;

import com.example.aiassistantcoder.ui.ProfileKt;
import com.example.aiassistantcoder.ui.SnackBarApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class ReauthActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private ComposeView composeView;  // we'll reuse this for both steps

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reauth);

        mAuth = FirebaseAuth.getInstance();
        composeView = findViewById(R.id.compose_reauth);

        showReauthStep();
    }

    /**
     * Step 1: ask user for their current password and re-authenticate them.
     */
    private void showReauthStep() {
        ProfileKt.bindReauthPasswordContent(
                composeView,
                new Function1<String, Unit>() {
                    @Override
                    public Unit invoke(String password) {

                        View root = findViewById(android.R.id.content);   // reuse for all snackbars

                        String pwd = password == null ? "" : password.trim();
                        if (pwd.isEmpty()) {
                            SnackBarApp.INSTANCE.show(
                                    root,
                                    "Please enter your password",
                                    SnackBarApp.Type.WARNING
                            );
                            return Unit.INSTANCE;
                        }

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user == null || user.getEmail() == null) {
                            SnackBarApp.INSTANCE.show(
                                    root,
                                    "No logged-in user.",
                                    SnackBarApp.Type.ERROR
                            );
                            return Unit.INSTANCE;
                        }

                        AuthCredential credential =
                                EmailAuthProvider.getCredential(user.getEmail(), pwd);

                        user.reauthenticate(credential)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        // Go to step 2 (new password screen)
                                        showNewPasswordStep();
                                    } else {
                                        SnackBarApp.INSTANCE.show(
                                                root,
                                                "Re-authentication failed.",
                                                SnackBarApp.Type.ERROR
                                        );
                                    }
                                });
                        return Unit.INSTANCE;
                    }
                }
        );
    }

    /**
     * Step 2: ask for new password (Compose validates length & confirm) and update in Firebase.
     */
    private void showNewPasswordStep() {
        ProfileKt.bindNewPasswordContent(
                composeView,
                new Function1<String, Unit>() {
                    @Override
                    public Unit invoke(String newPassword) {

                        View root = findViewById(android.R.id.content);   // use once

                        String np = newPassword == null ? "" : newPassword.trim();

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user == null) {
                            SnackBarApp.INSTANCE.show(
                                    root,
                                    "No logged-in user.",
                                    SnackBarApp.Type.ERROR
                            );
                            return Unit.INSTANCE;
                        }

                        user.updatePassword(np)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {

                                        SnackBarApp.INSTANCE.show(
                                                root,
                                                "Password updated. Please log in again.",
                                                SnackBarApp.Type.SUCCESS
                                        );

                                        mAuth.signOut();
                                        finish();

                                    } else {

                                        SnackBarApp.INSTANCE.show(
                                                root,
                                                "Failed to update password.",
                                                SnackBarApp.Type.ERROR
                                        );
                                    }
                                });

                        return Unit.INSTANCE;
                    }
                }
        );
    }
}
