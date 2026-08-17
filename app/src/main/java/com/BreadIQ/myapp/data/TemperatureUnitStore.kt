package com.BreadIQ.myapp.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.BreadIQ.myapp.model.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * **Reactive now, backed by a [StateFlow]** — the Settings screen calls
 * [setUnit] from a real UI control now, and any already-alive
 * `CalculatorViewModel` needs to see that change immediately, not only
 * after a full app relaunch. This is one **shared instance**, constructed
 * once in `MainActivity` (a plain field, not a `ViewModel` — this class
 * has no coroutine work of its own beyond exposing the flow) and threaded
 * into both [com.BreadIQ.myapp.viewmodel.CalculatorViewModelFactory] and
 * `SettingsScreen`, the same "one shared instance, threaded down" shape
 * `SubscriptionViewModel` established for the identical class of problem
 * (`CalculatorViewModel.userTier`'s own live binding).
 */
class TemperatureUnitStore(private val prefs: SharedPreferences?) {

    private val _unit = MutableStateFlow(loadInitial())
    val unit: StateFlow<TemperatureUnit> = _unit.asStateFlow()

    fun setUnit(newUnit: TemperatureUnit) {
        _unit.value = newUnit
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
