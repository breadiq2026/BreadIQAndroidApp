package com.BreadIQ.myapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientPriceOverrideDao {
    @Query("SELECT * FROM ingredient_price_overrides WHERE ingredientKey = :ingredientKey")
    suspend fun getByKey(ingredientKey: String): IngredientPriceOverrideEntity?

    @Query("SELECT * FROM ingredient_price_overrides")
    fun observeAll(): Flow<List<IngredientPriceOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: IngredientPriceOverrideEntity)

    @Query("DELETE FROM ingredient_price_overrides WHERE ingredientKey = :ingredientKey")
    suspend fun deleteByKey(ingredientKey: String)
}
