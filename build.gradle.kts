// AGP 9's built-in Kotlin support defaults to an older bundled Kotlin Gradle plugin version.
// Pin the version this project targets instead of the AGP default (DEV_PLAYBOOK §5, gotcha 1).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
    id("com.google.dagger.hilt.android") version "2.60" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}
