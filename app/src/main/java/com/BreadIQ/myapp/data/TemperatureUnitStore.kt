package com.BreadIQ.myapp.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.BreadIQ.myapp.model.TemperatureUnit

/**
 * Ported from the iOS app's `Stores/TemperatureUnitStore.swift`.
 *
 * The app-wide temperature display preference — [SharedPreferences]-backed,
 * same pattern as `AuthViewModel`'s cached-display-name storage (a small,
 * non-secret preference under a namespaced key).
 *
 * Purely a display preference — nothing in this store, or in
 * [com.BreadIQ.myapp.core.TemperatureFormatting] which reads it, ever
 * touches how a calculation is performed. Internal math stays Fahrenheit
 * everywhere.
 *
 * **No Settings screen exists on Android yet to call [setUnit]** — the
 * iOS source's own toggle for this lives in a Settings screen that isn't
 * part of this port yet either. Every consumer today just reads [unit],
 * which defaults to [TemperatureUnit.FAHRENHEIT] on a fresh install,
 * exactly matching the source's own default.
 */
class TemperatureUnitStore(private val prefs: SharedPreferences?) {

    var unit: TemperatureUnit = loadInitial()
        private set

    fun setUnit(newUnit: TemperatureUnit) {
        unit = newUnit
        prefs?.edit { putString(UNIT_KEY, newUnit.rawValue) }
    }

    private fun loadInitial(): TemperatureUnit {
        val raw = prefs?.getString(UNIT_KEY, null)
        return TemperatureUnit.entries.firstOrNull { it.rawValue == raw } ?: TemperatureUnit.FAHRENHEIT
    }

    private companion object {
        const val UNIT_KEY = "breadiq.temperatureUnit"
    }
}
