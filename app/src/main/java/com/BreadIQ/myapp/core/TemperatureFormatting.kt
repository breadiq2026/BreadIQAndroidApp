package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.TemperatureUnit
import kotlin.math.pow

/**
 * Ported from the iOS app's `Core/TemperatureFormatting.swift`.
 *
 * The single conversion+formatting point every temperature display/input
 * touch point routes through — presentation-layer only. Internal
 * calculations ([FormulaCalculator], [ProofTimeCalculator],
 * [AutolyseCalculator]) must never call these functions and must never be
 * passed a converted value — they keep working exclusively in Fahrenheit
 * `Double`s, exactly as before this preference existed.
 * [ProofTimeCalculator.fermentRate]'s own internal Fahrenheit→Celsius→
 * Kelvin step in particular is unrelated to this file: that conversion
 * exists purely for the Arrhenius rate equation's own math and has no
 * display responsibility at all.
 *
 * Celsius output is rounded to the nearest whole degree uniformly —
 * continuous values (steppers, computed readouts) and discrete presets
 * (e.g. the cold-retard fridge chip row) alike. No curated/special-cased
 * values; standard rounding is the real, final behavior.
 */
object TemperatureFormatting {

    fun fahrenheitToCelsius(fahrenheit: Double): Double = (fahrenheit - 32) * 5 / 9

    fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9 / 5 + 32

    /**
     * Formats a Fahrenheit value for display in the given unit, e.g.
     * `"475°F"` or `"246°C"` — the returned string already includes the
     * unit symbol.
     */
    fun display(fahrenheit: Double, unit: TemperatureUnit): String = when (unit) {
        TemperatureUnit.FAHRENHEIT -> "${fahrenheit.swiftRounded().toInt()}${unit.symbol}"
        TemperatureUnit.CELSIUS -> "${fahrenheitToCelsius(fahrenheit).swiftRounded().toInt()}${unit.symbol}"
    }

    /**
     * Formats a Fahrenheit `low...high` range for display, e.g.
     * `"460–480°F"` or `"238–249°C"` — matches the codebase's existing
     * en-dash range convention (`BreadStyleDef`'s own hydration/yeast/
     * oven-temp range prose).
     */
    fun displayRange(low: Double, high: Double, unit: TemperatureUnit): String = when (unit) {
        TemperatureUnit.FAHRENHEIT -> "${low.swiftRounded().toInt()}–${high.swiftRounded().toInt()}${unit.symbol}"
        TemperatureUnit.CELSIUS -> {
            val lowC = fahrenheitToCelsius(low).swiftRounded().toInt()
            val highC = fahrenheitToCelsius(high).swiftRounded().toInt()
            "$lowC–$highC${unit.symbol}"
        }
    }

    /**
     * Parses a value the user typed/selected in [unit] back to Fahrenheit
     * — the unit every calculator input (`ProofTimeInput.waterTempF`,
     * `.ambientTempF`, etc.) actually stores. A no-op when [unit] is
     * already [TemperatureUnit.FAHRENHEIT].
     */
    fun toFahrenheit(value: Double, from: TemperatureUnit): Double = when (from) {
        TemperatureUnit.FAHRENHEIT -> value
        TemperatureUnit.CELSIUS -> celsiusToFahrenheit(value)
    }

    /**
     * Converts a Fahrenheit *difference* (not an absolute temperature) to
     * Celsius — e.g. a "reduce bake temp by 10°F" instruction, where the
     * usual [fahrenheitToCelsius] absolute-value formula (which subtracts
     * 32) would be wrong. A 10°F difference is a 5.56°C difference, not
     * `(10-32)*5/9`.
     */
    fun fahrenheitDeltaToCelsius(delta: Double): Double = delta * 5 / 9

    /**
     * Formats a Fahrenheit `low...high` *differential* range (e.g.
     * "reduce by 10–15°F") for display — see [fahrenheitDeltaToCelsius].
     */
    fun displayDeltaRange(low: Double, high: Double, unit: TemperatureUnit): String = when (unit) {
        TemperatureUnit.FAHRENHEIT -> "${low.swiftRounded().toInt()}–${high.swiftRounded().toInt()}${unit.symbol}"
        TemperatureUnit.CELSIUS -> {
            val lowC = fahrenheitDeltaToCelsius(low).swiftRounded().toInt()
            val highC = fahrenheitDeltaToCelsius(high).swiftRounded().toInt()
            "$lowC–$highC${unit.symbol}"
        }
    }

    // MARK: - Editable text-field support (CalcStepperRow)

    /**
     * The value to show in an editable text field while the user is
     * actively typing, projected into [unit]'s space — e.g.
     * `editableText(75.2, unit = CELSIUS)` returns `"24"`. Deliberately
     * without the unit symbol, matching every stepper's existing
     * "no suffix while editing" convention.
     */
    fun editableText(fahrenheit: Double, unit: TemperatureUnit, decimals: Int = 0): String {
        val displayValue = if (unit == TemperatureUnit.FAHRENHEIT) fahrenheit else fahrenheitToCelsius(fahrenheit)
        return String.format("%.${decimals}f", displayValue)
    }

    /**
     * Parses text typed in [unit]'s space back to Fahrenheit — `null` if
     * it doesn't parse as a number at all, so the caller can leave the
     * stored value unchanged rather than clobbering it with garbage.
     */
    fun parseEditedText(text: String, unit: TemperatureUnit): Double? {
        val typed = text.toDoubleOrNull() ?: return null
        return toFahrenheit(typed, from = unit)
    }

    /**
     * The new Fahrenheit value after stepping by [step] — interpreted in
     * [unit]'s space, so `unit = CELSIUS` steps by whole Celsius degrees
     * even though [fahrenheit]/[minValue]/[maxValue] are all Fahrenheit —
     * rounded to [decimals] digits in [unit]'s space before converting
     * back and clamping to `minValue...maxValue`.
     */
    fun steppedValue(
        fahrenheit: Double,
        step: Double,
        unit: TemperatureUnit,
        minValue: Double,
        maxValue: Double,
        decimals: Int = 0,
    ): Double {
        val displayValue = if (unit == TemperatureUnit.FAHRENHEIT) fahrenheit else fahrenheitToCelsius(fahrenheit)
        val scale = 10.0.pow(decimals + 2)
        val steppedDisplay = ((displayValue + step) * scale).swiftRounded() / scale
        val newFahrenheit = toFahrenheit(steppedDisplay, from = unit)
        return minOf(maxValue, maxOf(minValue, newFahrenheit))
    }
}
