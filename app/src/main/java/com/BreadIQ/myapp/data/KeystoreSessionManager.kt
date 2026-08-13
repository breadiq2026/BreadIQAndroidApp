package com.BreadIQ.myapp.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ported from the iOS app's `Core/KeychainStore.swift`.
 *
 * A minimal, single-purpose secure store for the Supabase session,
 * implementing supabase-kt's [SessionManager] plugin point — the Android
 * equivalent of iOS wrapping Keychain Services directly.
 *
 * **Deliberately not `androidx.security:security-crypto`'s
 * `EncryptedSharedPreferences`**, which the plan that scoped this file
 * originally suggested. Checked current guidance before adding it: Google
 * deprecated the whole `security-crypto` library as of `1.1.0-beta01`
 * ("Deprecated all APIs in favour of existing platform APIs and direct use
 * of Android Keystore" — the library's own release notes), and the latest
 * stable release (`1.1.0`, July 2025) carries that deprecation. Using it
 * for brand-new code in 2026 would mean depending on a library Google
 * itself now points away from. This talks to `AndroidKeyStore` directly
 * instead — one dependency fewer, and the same "talk to the platform
 * security API directly rather than a convenience wrapper over it" choice
 * as iOS's `KeychainStore` calling `Security.framework` directly.
 *
 * Stores the *entire* [UserSession] (access token, refresh token,
 * expiry, user), not just the refresh token the way `KeychainStore` does
 * on iOS — a necessary difference, not a stylistic one: iOS's
 * `SupabaseAuthService` is a hand-rolled REST client that keeps the access
 * token in memory and re-derives it from the refresh token on cold start,
 * so only the refresh token needs durable storage. supabase-kt (the real
 * SDK, used here per this port's own plan) owns the whole session
 * lifecycle itself through this exact interface, and its contract is
 * "give me back the session I last saved" — so the full session is what
 * this needs to persist.
 *
 * The AES key never leaves the `AndroidKeyStore` hardware/software-backed
 * keystore; only ciphertext is ever written to disk (in ordinary
 * [SharedPreferences] — the encryption happens before the value reaches
 * that store, so the store itself doesn't need to be "encrypted").
 */
class KeystoreSessionManager(context: Context) : SessionManager {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private val secretKey: SecretKey
        get() = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // No `.setUserAuthenticationRequired(true)` — matches
            // Keychain's `kSecAttrAccessibleAfterFirstUnlock` on iOS:
            // readable in the background without a fresh biometric/
            // passcode prompt (needed for a session read on cold launch),
            // not gated behind device auth the way a "confirm it's you"
            // credential would be.
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override suspend fun saveSession(session: UserSession) {
        val plaintext = Json.encodeToString(UserSession.serializer(), session).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey) }
        val ciphertext = cipher.doFinal(plaintext)
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_DATA, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    override suspend fun loadSession(): UserSession? {
        // Nullable return, not a thrown `NoSessionFoundException` — matches
        // this pinned supabase-kt version's actual `SessionManager`
        // contract (verified against the real interface source at this
        // exact version tag; a later supabase-kt release changes
        // `loadSession()` to a throwing, non-nullable signature, but this
        // app isn't on that version yet — see this dependency's version
        // comment in `gradle/libs.versions.toml`).
        val ivString = prefs.getString(KEY_IV, null)
        val dataString = prefs.getString(KEY_DATA, null)
        if (ivString == null || dataString == null) return null

        val iv = Base64.decode(ivString, Base64.NO_WRAP)
        val ciphertext = Base64.decode(dataString, Base64.NO_WRAP)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            val plaintext = cipher.doFinal(ciphertext)
            Json.decodeFromString(UserSession.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            // Corrupt/undecryptable (e.g. the Keystore key was lost, which
            // can happen after a device credential reset) — treat as "no
            // session" rather than crashing, the same fail-safe spirit as
            // iOS's `SecItemCopyMatching` simply returning nil on any
            // Keychain error rather than throwing.
            deleteSession()
            null
        }
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(KEY_IV).remove(KEY_DATA).apply()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "breadiq_supabase_session_key"
        const val PREFS_NAME = "breadiq_secure_session"
        const val KEY_IV = "session_iv"
        const val KEY_DATA = "session_data"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
