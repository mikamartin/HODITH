package com.secondmonday.hodith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

private val SCHEMA_DIR = File("schemas/com.secondmonday.hodith.data.HodithDatabase")

/**
 * Guards against a schema bump landing without a matching [Migration][androidx.room.migration.Migration].
 * v1-5 never shipped (no fallback data at risk), so [HODITH_DATABASE_VERSION] is the first version a
 * real migration is required from. If this fails, either add the missing `Migration` to
 * [HodithDatabase.MIGRATIONS] or the schema bump wasn't intentional.
 */
class SchemaMigrationCoverageTest {
    @Test
    fun `every schema version past the freeze point has a matching migration`() {
        val schemaVersions =
            SCHEMA_DIR
                .listFiles { file -> file.extension == "json" }
                .orEmpty()
                .map { it.nameWithoutExtension.toInt() }

        val highestSchemaVersion = schemaVersions.max()
        val expectedMigrationCount = highestSchemaVersion - HODITH_DATABASE_VERSION

        assertEquals(expectedMigrationCount, HodithDatabase.MIGRATIONS.size)
    }
}
