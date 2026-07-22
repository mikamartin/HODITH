package com.secondmonday.hodith.testtags

/**
 * Marks a representative happy-path instrumented test for a quick local sanity run:
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.secondmonday.hodith.testtags.Smoke`
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Smoke
