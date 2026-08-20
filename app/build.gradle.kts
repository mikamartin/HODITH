plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.secondmonday.hodith"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.secondmonday.hodith"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "com.secondmonday.hodith.HiltTestRunner"
    }

    sourceSets {
        // Exposes app/schemas/*.json to MigrationTestHelper in instrumented tests.
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Deliberately pinned toolchain/dependency versions (see DEV_PLAYBOOK.md §5, "Tooling
        // Upgrade Reference") — these checks nag on every pin regardless of the reason it's held
        // back, so they're disabled here rather than re-litigated on every lint run. Bumping any
        // of these is its own project, gated by that section's upgrade checklist.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable", "OldTargetApi")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room 2.8.4's MigrationTestHelper (room-migration-bundle) needs kotlinx-serialization-core
// 1.8.1 to deserialize schemas/*.json, but the pinned Compose BOM's constraint set strictly
// pins it to 1.7.3, causing an AbstractMethodError at test runtime. No production code uses
// kotlinx.serialization, so force it only for the androidTest configurations rather than
// touching the pinned Compose BOM (DEV_PLAYBOOK §6).
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation.core)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    compileOnly(libs.error.prone.annotations)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.glance.appwidget)

    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.work.testing)
    // Pinned explicitly: the version ui-test-junit4 pulls transitively throws
    // NoSuchMethodException on android.hardware.input.InputManager.getInstance on some
    // API levels — this is the known fix (see TESTING.md's known environment issues).
    androidTestImplementation(libs.espresso.core)
    kspAndroidTest(libs.hilt.compiler)
}
