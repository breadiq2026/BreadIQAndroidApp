plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    // NOTE: matches the applicationId already registered in Google Play
    // Console for this app. Do not change without also updating the Play
    // Console listing — they must match exactly, including case.
    namespace = "com.BreadIQ.myapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.BreadIQ.myapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // BreadIQ is portrait-only on iOS for iPhone; matched here via the
        // MainActivity manifest entry (screenOrientation="portrait") rather
        // than here.

        // Room schema export (PORTING_PLAN.md step 5) — writes a JSON
        // snapshot of the schema per DB version to app/schemas/, needed to
        // write real migrations later and to unit-test them. The classic
        // KSP-arg form rather than the newer dedicated `androidx.room`
        // Gradle plugin — one fewer plugin to wire up for the same result.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // supabase-kt (Auth + Postgrest) — same Supabase project as iOS. See
    // data/SupabaseConfig.kt. Ktor's Android engine is supabase-kt's
    // recommended HTTP client engine for this platform.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    // Originally added just for decoding supabase-kt's own @Serializable
    // types (e.g. UserSession in KeystoreSessionManager). Now also used by
    // Room's TypeConverters (below) to JSON-encode this app's own small
    // value types (FlourBlendEntry, QueuedBakeStepPlan) — see the
    // kotlin.plugin.serialization entry above, added for that reason.
    implementation(libs.kotlinx.serialization.json)

    // Room (local persistence, PORTING_PLAN.md step 5) — the Android
    // equivalent of SwiftData. room-ktx for Flow/suspend DAO support.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // CameraX + ML Kit Text Recognition (PORTING_PLAN.md step 7, camera/
    // OCR import Session A) — camera-view's PreviewView for the live
    // preview surface (via AndroidView interop, see
    // ui/components/RecipeScanCapture.kt's own doc comment), ML Kit for
    // on-device OCR. Both fully offline at inference time.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)

    // RevenueCat (PORTING_PLAN.md step 6, RevenueCat + Subscription
    // screen) — owns the Play Billing purchase flow, server-side
    // receipt validation, and entitlement caching. Ships its own Kotlin
    // coroutine `awaitX()` suspend extensions (awaitCustomerInfo/
    // awaitLogIn/awaitLogOut/awaitRestore/awaitOfferings/awaitPurchase)
    // used directly in core/RevenueCatPurchasesService.kt, rather than
    // the older callback-based `xWith(...)` API.
    implementation(libs.revenuecat.purchases)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
