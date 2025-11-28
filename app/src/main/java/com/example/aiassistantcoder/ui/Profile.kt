package com.example.aiassistantcoder.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonDefaults.outlinedButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.aiassistantcoder.R
import kotlin.jvm.functions.Function0
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlin.jvm.functions.Function6
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType


// =====================================================
// BINDER FUNCTIONS (called from Java)
// =====================================================

// ---------- SIGN IN ----------
fun bindSignInContent(
    composeView: ComposeView,
    onEmailSignIn: Function2<String, String, Unit>,
    onGoogleClick: Function0<Unit>,
    onPhoneSendCode: Function1<String, Unit>,
    onPhoneVerifyCode: Function1<String, Unit>,
    onForgotPassword: Function1<String, Unit>,
    onRegisterClick: Function0<Unit>
) {
    composeView.setContent {
        MaterialTheme(colorScheme = darkColorScheme()) {
            SignInContent(
                onEmailSignIn = { e, p -> onEmailSignIn.invoke(e, p) },
                onGoogleClick = { onGoogleClick.invoke() },
                onPhoneSendCode = { phone -> onPhoneSendCode.invoke(phone) },
                onPhoneVerifyCode = { code -> onPhoneVerifyCode.invoke(code) },
                onForgotPassword = { email -> onForgotPassword.invoke(email) },
                onRegisterClick = { onRegisterClick.invoke() }
            )
        }
    }
}

// ---------- REGISTER ----------
fun bindRegisterContent(
    composeView: ComposeView,
    onRegister: Function6<String, String, String, String, String, String, Unit>,
    onBackToSignIn: Function0<Unit>
) {
    composeView.setContent {
        val bgPrimary = colorResource(R.color.colorBackground)

        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                color = bgPrimary,
                modifier = Modifier.fillMaxSize()
            ) {
                RegisterContent(
                    onRegister = { f, l, e, ce, p, cp ->
                        onRegister.invoke(f, l, e, ce, p, cp)
                    },
                    onBackToSignIn = { onBackToSignIn.invoke() }
                )
            }
        }
    }
}

// ---------- LOGGED IN ----------
fun bindLoggedInContent(
    composeView: ComposeView,
    welcomeText: String,
    onUpdateName: Function1<String, Unit>,
    onResetPassword: Function0<Unit>,
    onSignOut: Function0<Unit>
) {
    composeView.setContent {
        MaterialTheme(colorScheme = darkColorScheme()) {
            LoggedInContent(
                welcomeText = welcomeText,
                onUpdateName = { newName -> onUpdateName.invoke(newName) },
                onResetPassword = { onResetPassword.invoke() },
                onSignOut = { onSignOut.invoke() }
            )
        }
    }
}

// ---------- REAUTH STEP (ENTER CURRENT PASSWORD) ----------
fun bindReauthPasswordContent(
    composeView: ComposeView,
    onSubmitPassword: Function1<String, Unit>
) {
    composeView.setContent {
        MaterialTheme(colorScheme = darkColorScheme()) {
            ReauthPasswordContent(
                onSubmitPassword = { pwd -> onSubmitPassword.invoke(pwd) }
            )
        }
    }
}

// ---------- NEW PASSWORD STEP ----------
fun bindNewPasswordContent(
    composeView: ComposeView,
    onUpdatePassword: Function1<String, Unit>
) {
    composeView.setContent {
        MaterialTheme(colorScheme = darkColorScheme()) {
            NewPasswordContent(
                onUpdatePassword = { pwd -> onUpdatePassword.invoke(pwd) }
            )
        }
    }
}

// =====================================================
// COMPOSABLES
// =====================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInContent(
    onEmailSignIn: (String, String) -> Unit,
    onGoogleClick: () -> Unit,
    onPhoneSendCode: (String) -> Unit,
    onPhoneVerifyCode: (String) -> Unit,
    onForgotPassword: (String) -> Unit,
    onRegisterClick: () -> Unit
) {
    val bgSecondary = colorResource(R.color.colorSurface)
    val bgThird = colorResource(R.color.colorSurfaceVariant)
    val white = colorResource(R.color.colorOnBackground)
    val grey = colorResource(R.color.colorOutline)
    val purple = colorResource(R.color.colorSecondary)
    val purpleBlue = colorResource(R.color.colorSecondaryVariant)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPhoneDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = bgSecondary,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Email
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Password
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onEmailSignIn(email.trim(), password.trim())
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onEmailSignIn(email.trim(), password.trim())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purpleBlue,
                        contentColor = white
                    )
                ) {
                    Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { onGoogleClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = outlinedButtonColors(
                        containerColor = bgSecondary,
                        contentColor = white
                    )
                ) {
                    Text("G", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("Sign in with Google")
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showPhoneDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = outlinedButtonColors(
                        containerColor = bgSecondary,
                        contentColor = white
                    )
                ) {
                    Text("📞")
                    Spacer(Modifier.width(12.dp))
                    Text("Sign in with Phone")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Register",
                color = grey,
                modifier = Modifier
                    .clickable { onRegisterClick() }
                    .padding(4.dp)
            )
            Text(
                text = "Forgot password?",
                color = purple,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onForgotPassword(email.trim()) }
                    .padding(4.dp)
            )
        }
    }

    if (showPhoneDialog) {
        PhoneSignInDialogContent(
            onSendCode = { phone -> onPhoneSendCode(phone) },
            onVerifyCode = { code -> onPhoneVerifyCode(code) },
            onDismiss = { showPhoneDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSignInDialogContent(
    onSendCode: (String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val bgSecondary = colorResource(R.color.colorSurface)
    val bgThird = colorResource(R.color.colorSurfaceVariant)
    val white = colorResource(R.color.colorOnBackground)
    val purpleBlue = colorResource(R.color.colorSecondaryVariant)

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = bgSecondary,
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign in with phone",
                    color = white,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Phone number (+27...)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { onSendCode(phone.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purpleBlue,
                        contentColor = white
                    )
                ) {
                    Text("Send code")
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Verification code") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { onVerifyCode(code.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purpleBlue,
                        contentColor = white
                    )
                ) {
                    Text("Verify & sign in")
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Close",
                    color = white,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterContent(
    onRegister: (
        String, String,
        String, String,
        String, String
    ) -> Unit,
    onBackToSignIn: () -> Unit
) {
    val bgSecondary = colorResource(R.color.colorSurface)
    val bgThird = colorResource(R.color.colorSurfaceVariant)
    val white = colorResource(R.color.colorOnBackground)
    val grey = colorResource(R.color.colorOutline)
    val purpleBlue = colorResource(R.color.colorSecondaryVariant)

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = bgSecondary,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("First name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Last name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmEmail,
                    onValueChange = { confirmEmail = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Confirm email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Confirm password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()

                        onRegister(
                            firstName.trim(),
                            lastName.trim(),
                            email.trim(),
                            confirmEmail.trim(),
                            password.trim(),
                            confirmPassword.trim()
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purpleBlue,
                        contentColor = white
                    )
                ) {
                    Text("Create account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Already have an account? Sign in",
            color = grey,
            modifier = Modifier
                .clickable { onBackToSignIn() }
                .padding(4.dp),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReauthPasswordContent(
    onSubmitPassword: (String) -> Unit
) {
    val bgPrimary = colorResource(R.color.colorBackground)
    val bgSecondary = colorResource(R.color.colorSurface)
    val bgThird = colorResource(R.color.colorSurfaceVariant)
    val white = colorResource(R.color.colorOnBackground)
    val purpleBlue = colorResource(R.color.colorSecondaryVariant)

    var password by remember { mutableStateOf("") }

    // Get activity so we can close this screen from Compose
    val context = LocalContext.current
    val activity = context as? Activity

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = bgSecondary,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Confirm your password",
                        color = white,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Current password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = bgThird,
                            unfocusedContainerColor = bgThird,
                            focusedBorderColor = purpleBlue,
                            unfocusedBorderColor = bgThird,
                            focusedTextColor = white,
                            unfocusedTextColor = white,
                            cursorColor = purpleBlue
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onSubmitPassword(password.trim()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = purpleBlue,
                            contentColor = white
                        )
                    ) {
                        Text("Continue")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Cancel",
                color = white,
                modifier = Modifier
                    .clickable { activity?.finish() }
                    .padding(8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPasswordContent(
    onUpdatePassword: (String) -> Unit
) {
    val bgPrimary = colorResource(R.color.colorBackground)
    val bgSecondary = colorResource(R.color.colorSurface)
    val bgThird = colorResource(R.color.colorSurfaceVariant)
    val white = colorResource(R.color.colorOnBackground)
    val purpleBlue = colorResource(R.color.colorSecondaryVariant)

    val context = LocalContext.current
    val activity = context as? Activity   // <- safe cast

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = bgSecondary,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Set new password",
                        color = white,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("New password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = bgThird,
                            unfocusedContainerColor = bgThird,
                            focusedBorderColor = purpleBlue,
                            unfocusedBorderColor = bgThird,
                            focusedTextColor = white,
                            unfocusedTextColor = white,
                            cursorColor = purpleBlue
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Confirm new password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = bgThird,
                            unfocusedContainerColor = bgThird,
                            focusedBorderColor = purpleBlue,
                            unfocusedBorderColor = bgThird,
                            focusedTextColor = white,
                            unfocusedTextColor = white,
                            cursorColor = purpleBlue
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val np = newPassword.trim()
                            val cp = confirmPassword.trim()

                            val root =
                                activity?.findViewById<android.view.View>(android.R.id.content)

                            when {
                                root == null -> {
                                    // no activity / root, just bail out safely
                                    return@Button
                                }

                                np.length < 6 -> {
                                    SnackBarApp.show(
                                        root,
                                        message = "Password must be at least 6 characters",
                                        type = SnackBarApp.Type.WARNING
                                    )
                                }

                                np != cp -> {
                                    SnackBarApp.show(
                                        root,
                                        message = "Passwords do not match",
                                        type = SnackBarApp.Type.ERROR
                                    )
                                }

                                else -> onUpdatePassword(np)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = purpleBlue,
                            contentColor = white
                        )
                    ) {
                        Text("Update password")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Cancel",
                color = white,
                modifier = Modifier
                    .clickable { activity?.finish() }
                    .padding(8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LoggedInContent(
    welcomeText: String,
    onUpdateName: (String) -> Unit,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit
) {
    val bgSecondary = colorResource(R.color.colorSurface)
    val bgThird = colorResource(R.color.colorSurfaceVariant)
    val white = colorResource(R.color.colorOnBackground)
    val purpleBlue = colorResource(R.color.colorSecondaryVariant)

    var newName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = bgSecondary,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = welcomeText,
                    color = white,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("New display name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bgThird,
                        unfocusedContainerColor = bgThird,
                        focusedBorderColor = purpleBlue,
                        unfocusedBorderColor = bgThird,
                        focusedTextColor = white,
                        unfocusedTextColor = white,
                        cursorColor = purpleBlue
                    )
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { onUpdateName(newName.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purpleBlue,
                        contentColor = white
                    )
                ) {
                    Text("Update name")
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { onResetPassword() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = outlinedButtonColors(
                        containerColor = bgSecondary,
                        contentColor = white
                    )
                ) {
                    Text("Reset password")
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Sign out",
                    color = white,
                    modifier = Modifier
                        .clickable { onSignOut() }
                        .padding(8.dp)
                )
            }
        }
    }
}
