package com.BreadIQ.myapp.data.local

import androidx.room.TypeConverter
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.QueuedBakeStepPlan
import com.BreadIQ.myapp.model.StepStatus
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Room `TypeConverter`s for the value types the persisted entities in this
 * package use but SQLite/Room has no native column type for — the
 * equivalent of SwiftData transparently handling `Date`, `Codable` enums,
 * and `Codable` arrays as stored properties without any extra code on the
 * iOS side. Room needs each of those spelled out explicitly.
 */
class RoomConverters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    /** Stores the same `rawValue` string the Kotlin enum already carries for parity with the iOS `Codable` raw-value enums — not the enum's Kotlin case name, which would break if a case were ever renamed. */
    @TypeConverter
    fun bakeStatusToString(value: BakeStatus): String = value.rawValue

    @TypeConverter
    fun stringToBakeStatus(value: String): BakeStatus = BakeStatus.entries.first { it.rawValue == value }

    @TypeConverter
    fun stepStatusToString(value: StepStatus): String = value.rawValue

    @TypeConverter
    fun stringToStepStatus(value: String): StepStatus = StepStatus.entries.first { it.rawValue == value }

    /** [com.BreadIQ.myapp.model.BakeStep.coilFoldNotifIds] — the only `List<String>?` column in this schema. */
    @TypeConverter
    fun stringListToJson(value: List<String>?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? = value?.let { Json.decodeFromString(it) }

    @TypeConverter
    fun flourBlendToJson(value: List<FlourBlendEntry>?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun jsonToFlourBlend(value: String?): List<FlourBlendEntry>? = value?.let { Json.decodeFromString(it) }

    @TypeConverter
    fun stepPlanListToJson(value: List<QueuedBakeStepPlan>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToStepPlanList(value: String): List<QueuedBakeStepPlan> = Json.decodeFromString(value)
}
