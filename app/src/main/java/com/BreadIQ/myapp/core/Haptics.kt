package com.BreadIQ.myapp.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticImpactStyle { LIGHT, MEDIUM, HEAVY }

enum class HapticNotificationType { SUCCESS, WARNING }

/**
 * Ported from the iOS app's `Core/Haptics.swift`, which wraps
 * `UIImpactFeedbackGenerator`/`UINotificationFeedbackGenerator` — no
 * platform equivalent of either exists on Android, so this is a from
 * scratch (but behaviorally equivalent) implementation over
 * `Vibrator`/`VibrationEffect` rather than a literal line-for-line port.
 * Amplitudes (`LIGHT`=40, `MEDIUM`=255 i.e. `DEFAULT_AMPLITUDE`, `HEAVY`
 * =255 with a longer duration, `SUCCESS`=two short pulses, `WARNING`=one
 * longer pulse) are this port's own judgment call standing in for iOS's
 * built-in Taptic Engine curves — there is no source value to match here,
 * only an intent ("light tap" vs "heavy thud" vs "success"/"warning"
 * feel) to approximate.
 *
 * Needs a [Context] to obtain the system [Vibrator] service, so — unlike
 * the source, which can be called from anywhere — every call site in
 * this port must live in the Compose UI layer (where a `Context` is
 * naturally available via `LocalContext.current`), not deep inside
 * ViewModel/calculator logic.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun vibrate(context: Context, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator(context).vibrate(effect)
        }
    }

    fun impact(context: Context, style: HapticImpactStyle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val (durationMs, amplitude) = when (style) {
            HapticImpactStyle.LIGHT -> 10L to 40
            HapticImpactStyle.MEDIUM -> 20L to VibrationEffect.DEFAULT_AMPLITUDE
            HapticImpactStyle.HEAVY -> 30L to 255
        }
        vibrate(context, VibrationEffect.createOneShot(durationMs, amplitude))
    }

    fun notification(context: Context, type: HapticNotificationType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val effect = when (type) {
            HapticNotificationType.SUCCESS -> VibrationEffect.createWaveform(
                longArrayOf(0, 40, 60, 40), intArrayOf(0, 200, 0, 200), -1,
            )
            HapticNotificationType.WARNING -> VibrationEffect.createOneShot(60, 200)
        }
        vibrate(context, effect)
    }
}
