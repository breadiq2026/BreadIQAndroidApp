package com.BreadIQ.myapp.ui.lexicon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.BreadIQ.myapp.model.LexiconCatalog
import com.BreadIQ.myapp.model.LexiconTerm
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import kotlinx.coroutines.launch

/**
 * Ported from the iOS app's `Models/LexiconTerm.swift` (the
 * `LexiconSection` type) and `Screens/LexiconScreen.swift` (`LexiconSearch`).
 * Kept as pure, unit-testable functions rather than methods on the
 * screen, matching the source's own split.
 */
data class LexiconSection(val title: String, val terms: List<LexiconTerm>)

object LexiconSearch {
    /** `t.term.toLowerCase().includes(query) || ...`. [query] is expected pre-lowercased. */
    fun matches(term: LexiconTerm, query: String): Boolean {
        if (term.term.lowercase().contains(query)) return true
        if (term.definition.lowercase().contains(query)) return true
        term.details?.forEach { d ->
            if (d.label.lowercase().contains(query) || d.desc.lowercase().contains(query)) return true
        }
        return false
    }

    /** Filters each section's terms and drops sections left with zero matches. */
    fun filteredSections(sections: List<LexiconSection>, query: String): List<LexiconSection> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return sections
        val lowered = trimmed.lowercase()
        return sections.mapNotNull { section ->
            val matched = section.terms.filter { matches(it, lowered) }
            if (matched.isEmpty()) null else LexiconSection(section.title, matched)
        }
    }
}

private val romanNumerals = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
private fun romanNumeral(index: Int): String = romanNumerals.getOrElse(index) { (index + 1).toString() }

private val sectionSubtitles = mapOf(
    "Dough Archetypes" to "The \"Chemistry\"",
    "Pre-Ferments" to "The \"Head Start\"",
    "Loaf Formats" to "The \"Geometry\"",
)

private const val HEADER_KEY_PREFIX = "header:"

/**
 * Ported from the iOS app's `Screens/LexiconScreen.swift` — a pure
 * browser over the static [LexiconCatalog], no persistence involved.
 *
 * **Category-pill ↔ scroll-position sync, reworked for Compose rather
 * than transliterated.** The source drives this via a `GeometryReader`-
 * backed `PreferenceKey` measuring each section header's position — a
 * SwiftUI-specific workaround with no Compose equivalent needed:
 * [androidx.compose.foundation.lazy.LazyListState] already exposes each
 * visible item's viewport offset directly, so this reads
 * `layoutInfo.visibleItemsInfo` for the section-header items (tagged
 * with a `"header:"`-prefixed key) and picks the last one whose offset
 * is at or above a small threshold — the same "last header that's
 * scrolled to or past the top edge" rule the source's own
 * `activeSection(from:order:)` uses, just fed from Compose's own
 * scroll-position API instead of a manually-measured preference value.
 * Tapping a pill still sets the active category immediately (before the
 * scroll animation settles) exactly like the source.
 *
 * **The source's dead `pillScrollRef`** (declared, attached, never
 * actually used to auto-scroll the pill row) is correctly NOT
 * reproduced — this port's pill row simply doesn't auto-scroll on
 * scroll-driven category changes either, matching the source's real
 * (if accidental) behavior.
 */
@Composable
fun LexiconScreen(modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    val isSearchActive = searchQuery.trim().isNotEmpty()

    val allSections = remember {
        LexiconCatalog.categories.map { cat -> LexiconSection(cat, LexiconCatalog.terms.filter { it.category == cat }) }
    }
    val filteredSections = remember(searchQuery) { LexiconSearch.filteredSections(allSections, searchQuery) }

    var activeCategory by remember { mutableStateOf(LexiconCatalog.categories.firstOrNull().orEmpty()) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState, isSearchActive, filteredSections) {
        if (isSearchActive) return@LaunchedEffect
        val thresholdPx = with(density) { 80.dp.toPx() }
        snapshotFlow { listState.layoutInfo }.collect { layoutInfo ->
            var result: String? = null
            for (item in layoutInfo.visibleItemsInfo) {
                val key = item.key as? String ?: continue
                if (key.startsWith(HEADER_KEY_PREFIX) && item.offset <= thresholdPx) {
                    result = key.removePrefix(HEADER_KEY_PREFIX)
                }
            }
            if (result != null) activeCategory = result
        }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        LexiconHeader(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearchActive = isSearchActive,
            showPills = !isSearchActive,
            activeCategory = activeCategory,
            onCategoryTap = { category ->
                activeCategory = category
                val index = filteredSections.indexOfFirst { it.title == category }
                if (index >= 0) {
                    coroutineScope.launch {
                        // Each section contributes 1 (header) + N (terms) + 1 (spacer) items.
                        var flatIndex = 0
                        for (i in 0 until index) flatIndex += 2 + filteredSections[i].terms.size
                        listState.animateScrollToItem(flatIndex)
                    }
                }
            },
        )

        if (isSearchActive && filteredSections.isEmpty()) {
            LexiconEmptyState(searchQuery = searchQuery, onClearSearch = { searchQuery = "" })
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                filteredSections.forEachIndexed { index, section ->
                    item(key = "$HEADER_KEY_PREFIX${section.title}") {
                        SectionHeader(section = section, index = index)
                    }
                    itemsIndexed(section.terms, key = { _, term -> term.id }) { _, term ->
                        TermCard(term)
                    }
                    item(key = "spacer:${section.title}") {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                item(key = "bottom-padding") { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun LexiconHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    showPills: Boolean,
    activeCategory: String,
    onCategoryTap: (String) -> Unit,
) {
    val colors = LocalBreadIQColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 12.dp),
    ) {
        Text("Lexicon", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        Text("The technical standards and terminology of BreadIQ.", fontSize = 13.sp, color = colors.mutedForeground)

        SearchRow(searchQuery = searchQuery, onSearchQueryChange = onSearchQueryChange, isSearchActive = isSearchActive)

        if (showPills) {
            PillRow(activeCategory = activeCategory, onCategoryTap = onCategoryTap)
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun SearchRow(searchQuery: String, onSearchQueryChange: (String) -> Unit, isSearchActive: Boolean) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = colors.foreground),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (searchQuery.isEmpty()) {
                    Text("Search terms…", fontSize = 14.sp, color = colors.mutedForeground)
                }
                innerTextField()
            },
        )
        if (isSearchActive) {
            Icon(
                Icons.Filled.Close, contentDescription = "Clear search", tint = colors.mutedForeground,
                modifier = Modifier.size(16.dp).clickable { onSearchQueryChange("") },
            )
        }
    }
}

@Composable
private fun PillRow(activeCategory: String, onCategoryTap: (String) -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        LexiconCatalog.categories.forEach { category ->
            val active = category == activeCategory
            Text(
                text = category, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = if (active) androidx.compose.ui.graphics.Color.White else colors.mutedForeground,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) colors.primary else colors.card)
                    .border(1.dp, if (active) colors.primary else colors.border, CircleShape)
                    .clickable { onCategoryTap(category) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(section: LexiconSection, index: Int) {
    val colors = LocalBreadIQColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
    ) {
        Text(
            "${romanNumeral(index)}.", fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = colors.primary.copy(alpha = 0.6f), modifier = Modifier.width(24.dp),
        )
        Column {
            Text(section.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            sectionSubtitles[section.title]?.let {
                Text(it, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = colors.mutedForeground)
            }
        }
    }
}

@Composable
private fun TermCard(term: LexiconTerm) {
    val colors = LocalBreadIQColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(term.term, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
        Text(term.definition, fontSize = 13.sp, color = colors.mutedForeground)
        val details = term.details
        if (!details.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                details.forEach { detail ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("·", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary, modifier = Modifier.width(10.dp))
                        val text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.foreground)) { append("${detail.label}: ") }
                            withStyle(SpanStyle(color = colors.mutedForeground)) { append(detail.desc) }
                        }
                        Text(text, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LexiconEmptyState(searchQuery: String, onClearSearch: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
    ) {
        Text("No results", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
        Text(
            "No terms match “$searchQuery”.", fontSize = 13.sp, color = colors.mutedForeground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "Clear search", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.primary,
            modifier = Modifier.padding(top = 4.dp).clickable(onClick = onClearSearch),
        )
    }
}
