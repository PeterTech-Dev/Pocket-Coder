package com.example.aiassistantcoder;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ReauthActivity extends AppCompatActivity {

    private EditText passwordField;
    private Button submitButton;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reauth);

        mAuth = FirebaseAuth.getInstance();
        passwordField = findViewById(R.id.password_field_reauth);
        submitButton = findViewById(R.id.submit_button_reauth);

        submitButton.setOnClickListener(v -> {
            String password = passwordField.getText().toString();
            if (password.isEmpty()) {
                Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && user.getEmail() != null) {
                AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
                user.reauthenticate(credential)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Re-authentication successful, now prompt for new password
                                showNewPasswordDialog();
                            } else {
                                Toast.makeText(ReauthActivity.this, "Re-authentication failed.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
    }

    private void showNewPasswordDialog() {
        // In a real app, you would use a DialogFragment for this.
        // For simplicity, we'll just use a simple EditText and Button in the same activity for now.
        setContentView(R.layout.activity_new_password); // A new layout for entering the new password

        EditText newPasswordField = findViewById(R.id.new_password_field);
        Button updatePasswordButton = findViewById(R.id.update_password_button);

        updatePasswordButton.setOnClickListener(v -> {
            String newPassword = newPasswordField.getText().toString();
            if (newPassword.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                user.updatePassword(newPassword)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(ReauthActivity.this, "Password updated. Please log in again.", Toast.LENGTH_LONG).show();
                                mAuth.signOut();
                                finish();
                            } else {
                                Toast.makeText(ReauthActivity.this, "Failed to update password.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
    }
}