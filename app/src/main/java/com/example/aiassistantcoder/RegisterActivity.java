package com.example.aiassistantcoder;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;

import com.example.aiassistantcoder.ui.ProfileKt;
import com.example.aiassistantcoder.ui.SnackBarApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function6;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);  // 🔹 uses your activity_register.xml

        mAuth = FirebaseAuth.getInstance();

        ComposeView composeView = findViewById(R.id.compose_register);

        // Hook up the Compose register UI
        ProfileKt.bindRegisterContent(
                composeView,
                // onRegister(firstName, lastName, email, confirmEmail, password, confirmPassword)
                new Function6<String, String, String, String, String, String, Unit>() {
                    @Override
                    public Unit invoke(String firstName, String lastName,
                                       String email, String confirmEmail,
                                       String password, String confirmPassword) {
                        registerWithEmail(firstName, lastName, email, confirmEmail, password, confirmPassword);
                        return Unit.INSTANCE;
                    }
                },
                // onBackToSignIn()
                new Function0<Unit>() {
                    @Override
                    public Unit invoke() {
                        finish(); // just close this activity and go back to ProfileActivity
                        return Unit.INSTANCE;
                    }
                }
        );
    }

    private void registerWithEmail(
            String firstName,
            String lastName,
            String email,
            String confirmEmail,
            String password,
            String confirmPassword
    ) {
        View root = findViewById(android.R.id.content);  // Snackbar root

        final String fName = firstName == null ? "" : firstName.trim();
        final String lName = lastName == null ? "" : lastName.trim();
        final String e = email == null ? "" : email.trim();
        final String ce = confirmEmail == null ? "" : confirmEmail.trim();
        final String p = password == null ? "" : password.trim();
        final String cp = confirmPassword == null ? "" : confirmPassword.trim();

        // --- validation ---
        if (fName.isEmpty() || lName.isEmpty()) {
            SnackBarApp.INSTANCE.show(root, "Name and surname required", SnackBarApp.Type.WARNING);
            return;
        }

        if (e.isEmpty() || ce.isEmpty()) {
            SnackBarApp.INSTANCE.show(root, "Email required", SnackBarApp.Type.WARNING);
            return;
        }

        if (!e.equals(ce)) {
            SnackBarApp.INSTANCE.show(root, "Emails do not match", SnackBarApp.Type.ERROR);
            return;
        }

        if (p.isEmpty() || cp.isEmpty()) {
            SnackBarApp.INSTANCE.show(root, "Password required", SnackBarApp.Type.WARNING);
            return;
        }

        if (!p.equals(cp)) {
            SnackBarApp.INSTANCE.show(root, "Passwords do not match", SnackBarApp.Type.ERROR);
            return;
        }

        if (p.length() < 6) {
            SnackBarApp.INSTANCE.show(root, "Password must be at least 6 characters", SnackBarApp.Type.WARNING);
            return;
        }

        // --- register user ---
        mAuth.createUserWithEmailAndPassword(e, p)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        SnackBarApp.INSTANCE.show(
                                root,
                                "Registration failed: " + task.getException().getMessage(),
                                SnackBarApp.Type.ERROR
                        );
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) return;

                    String fullName = fName + " " + lName;
                    UserProfileChangeRequest profileUpdates =
                            new UserProfileChangeRequest.Builder()
                                    .setDisplayName(fullName)
                                    .build();

                    user.updateProfile(profileUpdates);

                    user.sendEmailVerification()
                            .addOnCompleteListener(v -> {
                                if (v.isSuccessful()) {
                                    SnackBarApp.INSTANCE.show(
                                            root,
                                            "Verification email sent to " + e,
                                            SnackBarApp.Type.INFO
                                    );
                                }
                            });

                    finish();
                });
    }
}
