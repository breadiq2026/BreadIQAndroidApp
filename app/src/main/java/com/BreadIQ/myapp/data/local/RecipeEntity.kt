package com.BreadIQ.myapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.Recipe
import java.time.Instant

/**
 * Room persistence for [Recipe] — replaces SwiftData's `@Model final
 * class Recipe` (`Models/Recipe.swift`).
 *
 * No relationships — a straightforward 1:1 with the domain model, unlike
 * [BakeSessionEntity]/[QueuedBakeEntity]. Kept as an entity-plus-mapper
 * anyway rather than annotating [Recipe] directly, for consistency with
 * the rest of this package — see [BakeSessionEntity]'s own doc comment.
 *
 * `id` is server-assigned (`Int`, from the Supabase-backed REST API,
 * per `Recipe`'s own doc comment) — not `autoGenerate`, since Room
 * should never invent a value for this column locally.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: Int,
    val userId: String,
    val name: String,
    val loafStyle: String,
    val numLoaves: Int,
    val hydrationPercent: Double,
    val fatPercent: Double,
    val flourWeight: Double,
    val waterWeight: Double,
    val fatWeight: Double,
    val saltWeight: Double,
    val yeastWeight: Double,
    val yeastPercent: Double,
    val yeastType: String?,
    val loafShape: String?,
    val preFermentType: String?,
    val preFermentFlourWeight: Double?,
    val preFermentWaterWeight: Double?,
    val preFermentYeastWeight: Double?,
    val fermentationType: String,
    val proofMinutes: Double?,
    val notes: String?,
    val flourBlend: List<FlourBlendEntry>?,
    val sweetenerType: String?,
    val sweetenerWeight: Double?,
    val humidityRh: Int?,
    val humidityDirection: String?,
    val humidityAdjusted: Boolean,
    val waterWeightUnadjusted: Double?,
    val createdAt: Instant,
    val importSourceURL: String?,
    val importSourceName: String?,
    val formatNote: String?,
)

fun RecipeEntity.toDomain(): Recipe = Recipe(
    id = id,
    userId = userId,
    name = name,
    loafStyle = loafStyle,
    numLoaves = numLoaves,
    hydrationPercent = hydrationPercent,
    fatPercent = fatPercent,
    flourWeight = flourWeight,
    waterWeight = waterWeight,
    fatWeight = fatWeight,
    saltWeight = saltWeight,
    yeastWeight = yeastWeight,
    yeastPercent = yeastPercent,
    yeastType = yeastType,
    loafShape = loafShape,
    preFermentType = preFermentType,
    preFermentFlourWeight = preFermentFlourWeight,
    preFermentWaterWeight = preFermentWaterWeight,
    preFermentYeastWeight = preFermentYeastWeight,
    fermentationType = fermentationType,
    proofMinutes = proofMinutes,
    notes = notes,
    flourBlend = flourBlend,
    sweetenerType = sweetenerType,
    sweetenerWeight = sweetenerWeight,
    humidityRh = humidityRh,
    humidityDirection = humidityDirection,
    humidityAdjusted = humidityAdjusted,
    waterWeightUnadjusted = waterWeightUnadjusted,
    createdAt = createdAt,
    importSourceURL = importSourceURL,
    importSourceName = importSourceName,
    formatNote = formatNote,
)

fun Recipe.toEntity(): RecipeEntity = RecipeEntity(
    id = id,
    userId = userId,
    name = name,
    loafStyle = loafStyle,
    numLoaves = numLoaves,
    hydrationPercent = hydrationPercent,
    fatPercent = fatPercent,
    flourWeight = flourWeight,
    waterWeight = waterWeight,
    fatWeight = fatWeight,
    saltWeight = saltWeight,
    yeastWeight = yeastWeight,
    yeastPercent = yeastPercent,
    yeastType = yeastType,
    loafShape = loafShape,
    preFermentType = preFermentType,
    preFermentFlourWeight = preFermentFlourWeight,
    preFermentWaterWeight = preFermentWaterWeight,
    preFermentYeastWeight = preFermentYeastWeight,
    fermentationType = fermentationType,
    proofMinutes = proofMinutes,
    notes = notes,
    flourBlend = flourBlend,
    sweetenerType = sweetenerType,
    sweetenerWeight = sweetenerWeight,
    humidityRh = humidityRh,
    humidityDirection = humidityDirection,
    humidityAdjusted = humidityAdjusted,
    waterWeightUnadjusted = waterWeightUnadjusted,
    createdAt = createdAt,
    importSourceURL = importSourceURL,
    importSourceName = importSourceName,
    formatNote = formatNote,
)
