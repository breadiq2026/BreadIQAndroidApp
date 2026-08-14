package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.core.TemperatureFormatting
import com.BreadIQ.myapp.core.swiftRounded
import com.BreadIQ.myapp.model.TemperatureUnit
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import kotlin.math.pow

/**
 * Ported from the iOS app's `Screens/CalculatorScreen.swift` — its
 * "Shared row atoms (port of calculator.tsx's sub-components)" section
 * at the bottom of that file. Calculator-specific (unlike
 * `ui/components/`, which the user asked to keep general-purpose), so
 * these live alongside [FormulaResultView] in `ui/calculator/`.
 */
@Composable
fun CalcSectionLabel(title: String, modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = colors.mutedForeground,
        modifier = modifier,
    )
}

@Composable
fun CalcDivider(modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(colors.border),
    )
}

@Composable
fun CalcToggleRow(
    label: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    premium: Boolean = false,
) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
            if (description != null) {
                Text(description, fontSize = 12.sp, color = colors.mutedForeground)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (premium) {
                Text(
                    text = "⭐ Premium",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF92400E),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7))
                        .border(1.dp, Color(0xFFFCD34D), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Switch(
                checked = value,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
            )
        }
    }
}

/**
 * When [temperatureUnit] is set, [value]/[minValue]/[maxValue] are still
 * stored/passed in Fahrenheit (internal storage stays Fahrenheit
 * everywhere — see [TemperatureFormatting]) but every displayed/typed/
 * stepped number is projected through [TemperatureFormatting] for that
 * unit. `null` (the default) preserves this atom's plain numeric
 * behavior, so every non-temperature stepper (batch size, hydration%,
 * etc.) is unaffected.
 */
@Composable
fun CalcStepperRow(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    minValue: Double,
    maxValue: Double,
    step: Double,
    modifier: Modifier = Modifier,
    unit: String = "",
    decimals: Int = 0,
    note: String? = null,
    displayText: String? = null,
    temperatureUnit: TemperatureUnit? = null,
) {
    val colors = LocalBreadIQColors.current

    fun fmt(v: Double): String = String.format("%.${decimals}f", v)
    fun clamp(v: Double): Double = v.coerceIn(minValue, maxValue)
    fun idleText(v: Double): String =
        if (temperatureUnit == null) fmt(v) + unit else TemperatureFormatting.editableText(v, temperatureUnit, decimals) + unit
    fun editingText(v: Double): String =
        if (temperatureUnit == null) fmt(v) else TemperatureFormatting.editableText(v, temperatureUnit, decimals)
    fun parsedFahrenheit(typed: String): Double? =
        if (temperatureUnit == null) typed.toDoubleOrNull() else TemperatureFormatting.parseEditedText(typed, temperatureUnit)
    fun newStepValue(direction: Double): Double {
        if (temperatureUnit == null) {
            val scale = 10.0.pow(decimals + 2)
            return clamp(((value + direction * step) * scale).swiftRounded() / scale)
        }
        return TemperatureFormatting.steppedValue(value, direction * step, temperatureUnit, minValue, maxValue, decimals)
    }

    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(displayText ?: idleText(value)) }

    // Mirrors the source's separate `onChange(of: value)` /
    // `onChange(of: displayText)` / `onChange(of: temperatureUnit)`
    // handlers, all three collapsing to the same "resync the idle text
    // unless the user is actively editing" behavior.
    LaunchedEffect(value, displayText, temperatureUnit) {
        if (!focused) text = displayText ?: idleText(value)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.foreground, modifier = Modifier.weight(1f))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .background(colors.muted)
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .clickable { onValueChange(newStepValue(-1.0)) },
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = colors.foreground, modifier = Modifier.size(13.dp))
            }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.primary, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .border(1.dp, if (focused) colors.primary else colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .onFocusChanged { state ->
                        val isFocused = state.isFocused
                        if (isFocused == focused) return@onFocusChanged
                        focused = isFocused
                        if (isFocused) {
                            text = editingText(value)
                        } else {
                            val parsed = parsedFahrenheit(text)
                            val committed = if (parsed != null) clamp(parsed) else value
                            if (parsed != null) onValueChange(committed)
                            text = displayText ?: idleText(committed)
                        }
                    },
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .background(colors.muted)
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .clickable { onValueChange(newStepValue(1.0)) },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase", tint = colors.foreground, modifier = Modifier.size(13.dp))
            }
        }
        if (note != null) {
            Text(note, fontSize = 11.sp, color = colors.mutedForeground)
        }
    }
}

data class CalcChipOption(val value: String, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalcChipRow(options: List<CalcChipOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { opt ->
            val active = opt.value == selected
            Text(
                text = opt.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) Color.White else colors.mutedForeground,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) colors.primary else colors.muted)
                    .border(1.dp, if (active) colors.primary else colors.border, CircleShape)
                    .clickable { onSelect(opt.value) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

data class CalcSelectOption(val value: String, val label: String, val sublabel: String? = null)

@Composable
fun CalcSelectMenu(
    selectedLabel: String,
    options: List<CalcSelectOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = LocalBreadIQColors.current
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground)
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.muted)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(selectedLabel, fontSize = 14.sp, color = colors.foreground, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(13.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(if (opt.sublabel != null) "${opt.label} — ${opt.sublabel}" else opt.label)
                        },
                        onClick = {
                            onSelect(opt.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

enum class CalcInfoVariant { NEUTRAL, WARNING, ERROR, BLUE }

@Composable
fun CalcInfoBox(text: String, variant: CalcInfoVariant, modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    val (bg, fg, border) = when (variant) {
        CalcInfoVariant.WARNING -> Triple(Color(0xFFFFFBEB), Color(0xFF92400E), Color(0xFFFDE68A))
        CalcInfoVariant.ERROR -> Triple(Color(0xFFFEF2F2), Color(0xFFB91C1C), Color(0xFFFCA5A5))
        CalcInfoVariant.BLUE -> Triple(Color(0xFFEFF6FF), Color(0xFF1D4ED8), Color(0xFFBFDBFE))
        CalcInfoVariant.NEUTRAL -> Triple(colors.muted, colors.mutedForeground, colors.border)
    }
    Text(
        text = text,
        fontSize = 12.sp,
        color = fg,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}
