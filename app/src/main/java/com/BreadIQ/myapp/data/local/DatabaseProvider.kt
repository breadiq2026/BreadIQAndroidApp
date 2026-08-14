package com.BreadIQ.myapp.data.local

import android.content.Context
import androidx.room.Room

/**
 * Builds/holds the single app-wide [BreadIQDatabase] — the Room
 * counterpart to `data/SupabaseClientProvider.kt`'s same
 * double-checked-locking singleton shape for [io.github.jan.supabase.SupabaseClient].
 * Called from `BreadIQApplication.onCreate()`, mirroring
 * `BreadIQApp.swift`'s `init()` calling `Self.makeModelContainer()` at
 * app launch — see [BreadIQDatabase]'s own doc comment for how Room's
 * lazy-open behavior makes that call structurally similar but not an
 * equivalent success/failure gate the way the SwiftData call is.
 */
object DatabaseProvider {
    private const val DATABASE_NAME = "breadiq.db"

    @Volatile
    private var instance: BreadIQDatabase? = null

    fun getInstance(context: Context): BreadIQDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(appContext: Context): BreadIQDatabase =
        Room.databaseBuilder(appContext, BreadIQDatabase::class.java, DATABASE_NAME)
            .build()
}
