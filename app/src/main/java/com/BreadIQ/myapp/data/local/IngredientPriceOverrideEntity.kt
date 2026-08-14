package com.BreadIQ.myapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.BreadIQ.myapp.model.IngredientPriceOverride

/**
 * Room persistence for [IngredientPriceOverride] — replaces SwiftData's
 * `@Model final class IngredientPriceOverride`
 * (`Models/IngredientReferencePrice.swift`).
 *
 * NOT the bundled [com.BreadIQ.myapp.model.IngredientReferencePriceCatalog]
 * itself — that stays a plain in-memory object per its own doc comment
 * (ship-time-constant reference data, never user-edited). This is
 * specifically the user-editable per-ingredient override on top of it.
 */
@Entity(tableName = "ingredient_price_overrides")
data class IngredientPriceOverrideEntity(
    @PrimaryKey val ingredientKey: String,
    val pricePerGram: Double,
)

fun IngredientPriceOverrideEntity.toDomain(): IngredientPriceOverride = IngredientPriceOverride(
    ingredientKey = ingredientKey,
    pricePerGram = pricePerGram,
)

fun IngredientPriceOverride.toEntity(): IngredientPriceOverrideEntity = IngredientPriceOverrideEntity(
    ingredientKey = ingredientKey,
    pricePerGram = pricePerGram,
)
