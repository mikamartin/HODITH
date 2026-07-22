package com.secondmonday.hodith.testtags

/**
 * Marks a Compose UI instrumented test class. CI's instrumented-tests workflow filters on this
 * to shard UI tests separately from DAO/repository tests (see instrumented-tests.yml).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class UiTest
