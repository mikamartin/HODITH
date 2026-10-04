package com.secondmonday.hodith.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** The Story share order, written and read back through a real DataStore file rather than the test fake. */
@RunWith(AndroidJUnit4::class)
class DataStoreShareSectionOrderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository =
        DataStoreSettingsRepository(
            dataStore =
                PreferenceDataStoreFactory.create(
                    produceFile = { context.preferencesDataStoreFile("share-order-${UUID.randomUUID()}") },
                ),
            context = context,
        )

    @Test
    fun aSavedOrderIsReadBackExactly() =
        runBlocking {
            val order =
                listOf(ShareInsightsSection.TAGS, ShareInsightsSection.GAPS) +
                    (ShareInsightsSection.entries - ShareInsightsSection.TAGS - ShareInsightsSection.GAPS)

            repository.setShareSectionOrder(order)

            assertEquals(order, repository.observeShareSectionOrder().first())
        }
}
