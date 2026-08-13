package com.BreadIQ.myapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.data.AuthErrorHumanizer
import com.BreadIQ.myapp.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP }

/**
 * Port of `app/auth.tsx`'s `handleSubmit` client-side validation from the
 * source Expo app, checked in the same order as the iOS port's
 * `AuthFormValidation`: fields present, then (signup only) name present,
 * then password length.
 */
private fun validateAuthForm(mode: AuthMode, name: String, email: String, password: String): String? {
    val trimmedEmail = email.trim()
    val trimmedName = name.trim()
    if (trimmedEmail.isEmpty() || password.isEmpty()) return "Please fill in all fields."
    if (mode == AuthMode.SIGN_UP && trimmedName.isEmpty()) return "Please enter your name."
    if (password.length < 6) return "Password must be at least 6 characters."
    return null
}

/**
 * Ported from the iOS app's `Screens/AuthScreen.swift`.
 *
 * **A fixed, non-theme-aware color palette, ported faithfully.** Every
 * color here is a bare hex literal, not routed through the app's Material
 * theme —
 * matching the iOS source's own deliberate choice: this is a "brand"
 * screen that looks the same regardless of system light/dark mode, kept
 * as local constants rather than added to the shared theme, for the same
 * reason `BreadIQColors` doesn't define them either.
 *
 * **Client validation and post-signup confirmation match the iOS port
 * exactly**: [validateAuthForm]'s three-check order, and
 * `AuthErrorHumanizer` applied ONLY to the sign-in error path (sign-up
 * errors show raw, matching the source Expo app's own asymmetry).
 *
 * **`handleForgotPassword` deliberately does NOT match the original Expo
 * source, matching the iOS port's own fix.** The original never checked
 * whether the reset email actually sent — always showed "sent" regardless.
 * This shows the real result instead, same as every other error path here.
 *
 * **Navigation on success is NOT handled here** — `MainActivity` already
 * reactively swaps to the tab shell the instant `AuthViewModel`'s
 * `currentUser` becomes non-null, so this screen only needs to call
 * `signIn`/`signUp`.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var forgotPasswordSent by remember { mutableStateOf(false) }
    var signedUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun switchMode(next: AuthMode) {
        errorMessage = null
        mode = next
    }

    fun handleSubmit() {
        errorMessage = null
        val validationError = validateAuthForm(mode, name, email, password)
        if (validationError != null) {
            errorMessage = validationError
            return
        }
        val trimmedEmail = email.trim()
        val trimmedName = name.trim()
        isSubmitting = true
        scope.launch {
            if (mode == AuthMode.SIGN_UP) {
                val result = viewModel.signUp(trimmedEmail, password, trimmedName)
                result.onFailure { errorMessage = it.message }
                result.onSuccess { signedUp = true }
            } else {
                val result = viewModel.signIn(trimmedEmail, password)
                result.onFailure { errorMessage = AuthErrorHumanizer.humanize(it.message ?: "") }
            }
            isSubmitting = false
        }
    }

    fun handleForgotPassword() {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            errorMessage = "Enter your email above first."
            return
        }
        scope.launch {
            viewModel.requestPasswordReset(trimmedEmail).fold(
                onSuccess = {
                    forgotPasswordSent = true
                    errorMessage = null
                },
                onFailure = { errorMessage = it.message },
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuthColors.nav),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (signedUp) {
            SignedUpConfirmation(
                email = email.trim(),
                onBackToSignIn = {
                    signedUp = false
                    switchMode(AuthMode.SIGN_IN)
                },
            )
        } else {
            AuthForm(
                mode = mode,
                name = name,
                onNameChange = { name = it },
                email = email,
                onEmailChange = { email = it },
                password = password,
                onPasswordChange = { password = it },
                showPassword = showPassword,
                onToggleShowPassword = { showPassword = !showPassword },
                errorMessage = errorMessage,
                forgotPasswordSent = forgotPasswordSent,
                isSubmitting = isSubmitting,
                onSwitchMode = ::switchMode,
                onSubmit = ::handleSubmit,
                onForgotPassword = ::handleForgotPassword,
            )
        }
    }
}

@Composable
private fun BreadIQLogo() {
    Row {
        Text("Bread", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = AuthColors.cream)
        Text("IQ", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = AuthColors.orange)
    }
}

@Composable
private fun SignedUpConfirmation(email: String, onBackToSignIn: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
    ) {
        BreadIQLogo()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(top = 32.dp)
                .background(AuthColors.card, RoundedCornerShape(20.dp))
                .border(1.dp, AuthColors.border, RoundedCornerShape(20.dp))
                .padding(32.dp),
        ) {
            Text("📧", fontSize = 40.sp)
            Text(
                "Check your email",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AuthColors.cream,
                textAlign = TextAlign.Center,
            )
            Text(
                buildString {
                    append("We sent a verification link to\n")
                    append(email)
                },
                fontSize = 15.sp,
                color = AuthColors.muted,
                textAlign = TextAlign.Center,
            )
            Text(
                "Click the link to verify your account and get started with your 30-day Premium trial — all features unlocked.",
                fontSize = 13.sp,
                color = AuthColors.muted,
                textAlign = TextAlign.Center,
            )
            CtaButton(label = "Back to Sign In", isSubmitting = false, onClick = onBackToSignIn)
        }
    }
}

@Composable
private fun AuthForm(
    mode: AuthMode,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    errorMessage: String?,
    forgotPasswordSent: Boolean,
    isSubmitting: Boolean,
    onSwitchMode: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 36.dp),
        ) {
            BreadIQLogo()
            Text(
                "PRECISE. SCIENTIFIC. SIMPLE.",
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
                color = AuthColors.muted,
            )
        }

        Column(
            modifier = Modifier
                .background(AuthColors.card, RoundedCornerShape(20.dp))
                .border(1.dp, AuthColors.border, RoundedCornerShape(20.dp)),
        ) {
            AuthTabs(mode = mode, onSwitchMode = onSwitchMode)
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                if (mode == AuthMode.SIGN_UP) {
                    AuthField(
                        label = "What should we call you?",
                        value = name,
                        onValueChange = onNameChange,
                        placeholder = "Your name",
                        capitalization = KeyboardCapitalization.Words,
                    )
                }
                AuthField(
                    label = "Email",
                    value = email,
                    onValueChange = onEmailChange,
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Password")
                    PasswordField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = if (mode == AuthMode.SIGN_UP) "Min. 6 characters" else "Password",
                        showPassword = showPassword,
                        onToggleShowPassword = onToggleShowPassword,
                        onSubmit = onSubmit,
                    )
                }

                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AuthColors.errorText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (forgotPasswordSent) {
                    Text(
                        "Password reset email sent — check your inbox.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AuthColors.successText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                CtaButton(
                    label = if (mode == AuthMode.SIGN_UP) "Create Account" else "Sign In",
                    isSubmitting = isSubmitting,
                    onClick = onSubmit,
                )

                if (mode == AuthMode.SIGN_IN && !forgotPasswordSent) {
                    TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth()) {
                        Text("Forgot password?", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AuthColors.muted)
                    }
                }

                if (mode == AuthMode.SIGN_UP) {
                    TrialBadge()
                }
            }
        }

        Text(
            if (mode == AuthMode.SIGN_UP) DISCLOSURE_TEXT else FINE_TEXT,
            fontSize = if (mode == AuthMode.SIGN_UP) 12.sp else 11.sp,
            color = AuthColors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (mode == AuthMode.SIGN_UP) 12.dp else 16.dp)
                .padding(top = if (mode == AuthMode.SIGN_UP) 20.dp else 24.dp),
        )
    }
}

@Composable
private fun AuthTabs(mode: AuthMode, onSwitchMode: (AuthMode) -> Unit) {
    Column {
        Row(modifier = Modifier.height(48.dp)) {
            AuthTabButton("Sign In", selected = mode == AuthMode.SIGN_IN, modifier = Modifier.weight(1f)) {
                onSwitchMode(AuthMode.SIGN_IN)
            }
            AuthTabButton("Sign Up", selected = mode == AuthMode.SIGN_UP, modifier = Modifier.weight(1f)) {
                onSwitchMode(AuthMode.SIGN_UP)
            }
        }
        HorizontalDivider(color = AuthColors.border, thickness = 1.dp)
    }
}

@Composable
private fun AuthTabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AuthColors.cream else AuthColors.muted,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .height(2.dp)
                .background(if (selected) AuthColors.orange else Color.Transparent),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        color = AuthColors.muted,
    )
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = AuthColors.muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            colors = authFieldColors(),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = AuthColors.muted) },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = {
            IconButton(onClick = onToggleShowPassword) {
                Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = AuthColors.muted,
                )
            }
        },
        colors = authFieldColors(),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AuthColors.nav,
    unfocusedContainerColor = AuthColors.nav,
    focusedTextColor = AuthColors.cream,
    unfocusedTextColor = AuthColors.cream,
    focusedBorderColor = AuthColors.border,
    unfocusedBorderColor = AuthColors.border,
    cursorColor = AuthColors.orange,
)

@Composable
private fun CtaButton(label: String, isSubmitting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isSubmitting,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthColors.orange,
            disabledContainerColor = AuthColors.orange.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(vertical = 15.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.3.sp)
        }
    }
}

@Composable
private fun TrialBadge() {
    Text(
        "⭐ Includes 30-day Premium trial — all features unlocked",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = AuthColors.badgeText,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(AuthColors.orange.copy(alpha = 0.125f), RoundedCornerShape(10.dp))
            .border(1.dp, AuthColors.orange.copy(alpha = 0.314f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private object AuthColors {
    val nav = Color(0xFF0F2557)
    val card = Color(0xFF1A2F6A)
    val orange = Color(0xFFF97316)
    val cream = Color(0xFFFFF8F0)
    val muted = Color(0xFF8DA6CC)
    val border = Color(0xFF2A4080)
    val errorText = Color(0xFFFCA5A5)
    val successText = Color(0xFF86EFAC)
    val badgeText = Color(0xFFFED7AA)
}

private const val DISCLOSURE_TEXT = "By creating an account, you'll receive a free 30-day Premium trial with all features unlocked. After your trial, a subscription is required to access Premium features. You can manage or cancel your subscription anytime in Settings → Manage Subscription.\n\nPayment will be charged at confirmation of purchase. Subscriptions automatically renew unless cancelled at least 24 hours before the end of the current billing period. By continuing, you agree to our Terms of Service and Privacy Policy."
private const val FINE_TEXT = "By signing in you agree to our Terms of Service and Privacy Policy."
