package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.FlourBlendEntry
import java.time.Duration
import java.time.Instant

/** Ported from the iOS app's `Screens/QueueScreen.swift`'s `QueueFormatting` enum — pure string-formatting helpers. */
object QueueFormatting {

    fun formatTotalTime(minutes: Int): String {
        if (minutes < 60) return "${minutes}m"
        val h = minutes / 60
        val m = minutes % 60
        return if (m > 0) "${h}h ${m}m" else "${h}h"
    }

    fun timeAgo(date: Instant, now: Instant = Instant.now()): String {
        val diffMinutes = Duration.between(date, now).toMinutes().toInt()
        if (diffMinutes < 2) return "just now"
        if (diffMinutes < 60) return "${diffMinutes}m ago"
        val hrs = diffMinutes / 60
        if (hrs < 24) return "${hrs}h ago"
        return "${hrs / 24}d ago"
    }

    /** `t.replace("_", " ")` — JS `String.replace` with a plain-string pattern replaces only the FIRST occurrence, not a global replace. */
    fun replaceFirstUnderscore(s: String): String {
        val idx = s.indexOf('_')
        return if (idx < 0) s else s.replaceRange(idx, idx + 1, " ")
    }

    /**
     * Mirrors CSS `text-transform: capitalize`: uppercases the first
     * letter of each whitespace-separated word, leaving the rest of
     * each word untouched — unlike `RecipeFormatting.formatStyle`, this
     * does NOT also replace underscores (callers here already handle
     * that separately, or the text never had any).
     */
    fun capitalizeWords(s: String): String =
        s.split(" ").joinToString(" ") { word -> if (word.isEmpty()) "" else word[0].uppercaseChar() + word.substring(1) }

    /** Trims a trailing `.0`/`.00…`, matching JS's default `Number` → `String` coercion — kept local to this screen the same way every other screen keeps its own copy. */
    private fun formatNumber(n: Double): String {
        if (n == n.swiftRounded()) return n.toLong().toString()
        var s = String.format("%.4f", n)
        while (s.endsWith("0")) s = s.dropLast(1)
        if (s.endsWith(".")) s = s.dropLast(1)
        return s
    }

    /** `blend.length === 1 ? blend[0].type.replace(...) : blend.map(...).join(" · ")`. The CSS `capitalize` transform is applied by the caller (this returns the same raw casing as the source, keeping the transform purely presentational). */
    fun formatFlourBlend(blend: List<FlourBlendEntry>): String {
        if (blend.size == 1) return replaceFirstUnderscore(blend[0].type)
        return blend.joinToString(" · ") { "${formatNumber(it.percent)}% ${replaceFirstUnderscore(it.type)}" }
    }
}
