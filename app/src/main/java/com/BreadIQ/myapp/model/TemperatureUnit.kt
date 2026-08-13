package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/TemperatureUnit.swift`.
 *
 * The user's preferred display unit for temperature — a pure
 * presentation-layer preference. Every internal calculation
 * (`FormulaCalculator`, `ProofTimeCalculator`, `AutolyseCalculator`,
 * `BakeStepAssembler`) keeps working exclusively in Fahrenheit `Double`s
 * regardless of this setting; only temperature-formatting code reads it, to
 * format a Fahrenheit value for display or parse a typed value back to
 * Fahrenheit at the UI boundary.
 */
enum class TemperatureUnit(val rawValue: String) {
    FAHRENHEIT("fahrenheit"),
    CELSIUS("celsius");

    val symbol: String
        get() = when (this) {
            FAHRENHEIT -> "°F"
            CELSIUS -> "°C"
        }
}
