package com.secondmonday.hodith.di

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertSame
import org.junit.Test

class DispatcherModuleTest {
    @Test
    fun `provideDefaultDispatcher returns the real Dispatchers Default`() {
        assertSame(Dispatchers.Default, DispatcherModule.provideDefaultDispatcher())
    }
}
