package com.example.aiassistantcoder;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class ProfileFragment extends Fragment {

    private FirebaseAuth mAuth;

    private LinearLayout loggedInView, loggedOutView;
    private TextView userNameText;
    private EditText emailField, passwordField, displayNameField;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();

        loggedInView = view.findViewById(R.id.logged_in_view);
        loggedOutView = view.findViewById(R.id.logged_out_view);
        userNameText = view.findViewById(R.id.user_name_text);
        emailField = view.findViewById(R.id.email_field);
        passwordField = view.findViewById(R.id.password_field);
        displayNameField = view.findViewById(R.id.display_name_field);

        Button signInButton = view.findViewById(R.id.sign_in_button);
        Button registerButton = view.findViewById(R.id.register_button);
        Button signOutButton = view.findViewById(R.id.sign_out_button);
        Button changePasswordButton = view.findViewById(R.id.change_password_button);
        Button updateNameButton = view.findViewById(R.id.update_name_button);

        // Email/Password Sign-In & Registration
        signInButton.setOnClickListener(v -> {
            String email = emailField.getText().toString();
            String password = passwordField.getText().toString();
            if (!email.isEmpty() && !password.isEmpty()) {
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                updateUI(mAuth.getCurrentUser());
                            } else {
                                Toast.makeText(getContext(), "Authentication failed.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        registerButton.setOnClickListener(v -> {
            String email = emailField.getText().toString();
            String password = passwordField.getText().toString();
            if (!email.isEmpty() && !password.isEmpty()) {
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                updateUI(mAuth.getCurrentUser());
                            } else {
                                Toast.makeText(getContext(), "Registration failed.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        // Logged In Actions
        signOutButton.setOnClickListener(v -> {
            mAuth.signOut();
            updateUI(null);
        });

        changePasswordButton.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), ReauthActivity.class));
        });

        updateNameButton.setOnClickListener(v -> {
            String newName = displayNameField.getText().toString();
            if (newName.isEmpty()) {
                return;
            }
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build();
                user.updateProfile(profileUpdates)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(getContext(), "Display name updated.", Toast.LENGTH_SHORT).show();
                                updateUI(user);
                            }
                        });
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateUI(mAuth.getCurrentUser());
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            loggedInView.setVisibility(View.VISIBLE);
            loggedOutView.setVisibility(View.GONE);
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                userNameText.setText("Welcome, " + user.getDisplayName());
            } else {
                userNameText.setText("Welcome!");
            }
        } else {
            loggedInView.setVisibility(View.GONE);
            loggedOutView.setVisibility(View.VISIBLE);
        }
    }
}