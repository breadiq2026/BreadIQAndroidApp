package com.BreadIQ.myapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared stand-in for each remaining tab screen until it's ported from
 * the iOS app's Screens directory (RecipesScreen.swift,
 * LexiconScreen.swift, QueueScreen.swift, CurrentBakeScreen.swift).
 * `CalculatorScreen` graduated out of this file — see
 * `ui/calculator/CalculatorScreen.kt`. See PORTING_PLAN.md for sequencing.
 */
@Composable
fun PlaceholderScreen(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun RecipesScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen("Recipes", "Saved recipes — ports RecipesScreen.swift", modifier)

@Composable
fun LexiconScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen("Lexicon", "Baking term glossary — ports LexiconScreen.swift", modifier)

@Composable
fun QueueScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen("Queue", "Scheduled bakes — ports QueueScreen.swift", modifier)

@Composable
fun CurrentBakeScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen("Current Bake", "Active bake session — ports CurrentBakeScreen.swift", modifier)
