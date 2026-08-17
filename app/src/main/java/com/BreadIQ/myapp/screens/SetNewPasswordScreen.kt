package com.BreadIQ.myapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.BreadIQ.myapp.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Pure validation for [SetNewPasswordScreen] — no source equivalent to
 * port (`AuthScreen.kt`'s own `validateAuthForm`'s password-length rule
 * is the closest precedent, reused here at 6 characters for consistency),
 * plus a confirm-password match check this app has never needed before.
 * Matches the iOS port's `SetNewPasswordFormValidation` exactly.
 */
object SetNewPasswordFormValidation {
    fun validate(password: String, confirmPassword: String): String? {
        if (password.isEmpty() || confirmPassword.isEmpty()) return "Please fill in both fields."
        if (password.length < 6) return "Password must be at least 6 characters."
        if (password != confirmPassword) return "Passwords don't match."
        return null
    }
}

/**
 * Ported from the iOS app's `Screens/SetNewPasswordScreen.swift`.
 *
 * New product surface with no source Expo screen to port — confirmed on
 * the iOS port that neither `breadiq-mobile` nor `bread-lab` ever had a
 * working "set new password" destination. Styled to match `AuthScreen.kt`'s
 * fixed, non-theme-aware brand palette deliberately, since `MainActivity.kt`
 * shows this in the exact same pre-sign-in position — while a pending
 * password-recovery deep link is set, this shows INSTEAD of
 * `AuthScreen`/`BreadIQApp`, regardless of `AuthViewModel`'s own
 * `currentUser` (a recovery session is a limited, task-specific session,
 * not a normal signed-in one, until this screen completes it).
 *
 * **Fixed brand palette, duplicated locally, not shared** — matching the
 * iOS source's own precedent (`SetNewPasswordScreen.swift` independently
 * re-declares its own color constants rather than importing
 * `AuthScreen.swift`'s palette, even though the values are identical).
 * `AuthScreen.kt`'s own `AuthColors` is file-private in Kotlin, not just
 * class-private — exporting it would be a real visibility change to an
 * existing file, out of scope here, and the source doesn't share it
 * either.
 *
 * [authViewModel] is the SAME [AuthViewModel] instance `MainActivity`
 * already owns, not a fresh one — success needs to update the same
 * `uiState` the rest of the app reads to decide it's now signed in, the
 * same `viewModel`-parameter-not-created-inside shape `AuthScreen(authViewModel)`
 * already uses.
 */
@Composable
fun SetNewPasswordScreen(
    accessToken: String,
    refreshToken: String,
    authViewModel: AuthViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun handleSubmit() {
        errorMessage = null
        val validationError = SetNewPasswordFormValidation.validate(password, confirmPassword)
        if (validationError != null) {
            errorMessage = validationError
            return
        }
        isSubmitting = true
        scope.launch {
            val result = authViewModel.completePasswordRecovery(accessToken, refreshToken, password)
            isSubmitting = false
            result.fold(
                onSuccess = { onComplete() },
                onFailure = { errorMessage = it.message },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SetNewPasswordColors.nav)
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
            SetNewPasswordLogo()
            Text(
                "SET A NEW PASSWORD",
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
                color = SetNewPasswordColors.muted,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .background(SetNewPasswordColors.card, RoundedCornerShape(20.dp))
                .border(1.dp, SetNewPasswordColors.border, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            FieldGroup(label = "New Password") {
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Min. 6 characters",
                    showPassword = showPassword,
                    onToggleShowPassword = { showPassword = !showPassword },
                    onSubmit = ::handleSubmit,
                )
            }
            FieldGroup(label = "Confirm Password") {
                PasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = "Re-enter your password",
                    showPassword = showPassword,
                    onToggleShowPassword = { showPassword = !showPassword },
                    onSubmit = ::handleSubmit,
                )
            }

            if (errorMessage != null) {
                Text(
                    errorMessage ?: "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SetNewPasswordColors.errorText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            CtaButton(isSubmitting = isSubmitting, onClick = ::handleSubmit)
        }
    }
}

@Composable
private fun SetNewPasswordLogo() {
    Row {
        Text("Bread", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = SetNewPasswordColors.cream)
        Text("IQ", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = SetNewPasswordColors.orange)
    }
}

@Composable
private fun FieldGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = SetNewPasswordColors.muted,
        )
        content()
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
        placeholder = { Text(placeholder, color = SetNewPasswordColors.muted) },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = {
            IconButton(onClick = onToggleShowPassword) {
                Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = SetNewPasswordColors.muted,
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SetNewPasswordColors.nav,
            unfocusedContainerColor = SetNewPasswordColors.nav,
            focusedTextColor = SetNewPasswordColors.cream,
            unfocusedTextColor = SetNewPasswordColors.cream,
            focusedBorderColor = SetNewPasswordColors.border,
            unfocusedBorderColor = SetNewPasswordColors.border,
            cursorColor = SetNewPasswordColors.orange,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CtaButton(isSubmitting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isSubmitting,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SetNewPasswordColors.orange,
            disabledContainerColor = SetNewPasswordColors.orange.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(vertical = 15.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Set New Password", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.3.sp)
        }
    }
}

private object SetNewPasswordColors {
    val nav = Color(0xFF0F2557)
    val card = Color(0xFF1A2F6A)
    val orange = Color(0xFFF97316)
    val cream = Color(0xFFFFF8F0)
    val muted = Color(0xFF8DA6CC)
    val border = Color(0xFF2A4080)
    val errorText = Color(0xFFFCA5A5)
}
