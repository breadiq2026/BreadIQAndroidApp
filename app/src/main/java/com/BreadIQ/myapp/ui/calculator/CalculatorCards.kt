package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.CalculatorUiState
import com.BreadIQ.myapp.viewmodel.CalculatorViewModel

/**
 * Placeholder card bodies — [CalculatorScreen]'s header/footer/card-switch
 * shell is real and wired to [CalculatorViewModel] (this session's
 * "shared row atoms + header/footer shell" step); the actual 5-card
 * content (`CardStyleShapeBatch` through `CardCalculateResults`, ported
 * from `Screens/CalculatorScreen.swift`'s own `cardStyleShapeBatch`
 * through `cardCalculateResults`) lands in the next commits. Kept as
 * their own file so replacing a stub with real content is a same-file,
 * same-signature edit.
 */
@Composable
internal fun CardStyleShapeBatch(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    CardPlaceholder("Card 1 of 5 — Style, Shape & Batch")
}

@Composable
internal fun CardFlourAndFormula(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    CardPlaceholder("Card 2 of 5 — Flour Blend & Formula")
}

@Composable
internal fun CardFermentation(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    CardPlaceholder("Card 3 of 5 — Fermentation")
}

@Composable
internal fun CardEnvironment(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    CardPlaceholder("Card 4 of 5 — Environment")
}

@Composable
internal fun CardCalculateResults(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    CardPlaceholder("Card 5 of 5 — Calculate & Results")
}

@Composable
private fun CardPlaceholder(label: String) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text = "$label — coming in the next commit.", color = colors.mutedForeground)
    }
}
