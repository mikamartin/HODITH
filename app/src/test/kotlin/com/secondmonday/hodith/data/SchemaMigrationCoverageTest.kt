package com.secondmonday.hodith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

private val SCHEMA_DIR = File("schemas/com.secondmonday.hodith.data.HodithDatabase")

/**
 * Guards against a schema bump landing without a matching migration — hand-written (in
 * [HodithDatabase.MIGRATIONS]) or an `AutoMigration` (counted by [HodithDatabase.AUTO_MIGRATION_COUNT]).
 * v1-5 never shipped (no fallback data at risk), so [SCHEMA_FREEZE_POINT] is the first version a
 * migration is required from. If this fails, either cover the new schema version or the bump wasn't
 * intentional.
 */
class SchemaMigrationCoverageTest {
    @Test
    fun `every schema version past the freeze point has a matching migration`() {
        val highestSchemaVersion =
            SCHEMA_DIR
                .listFiles { file -> file.extension == "json" }
                .orEmpty()
                .map { it.nameWithoutExtension.toInt() }
                .max()

        val expectedMigrationCount = highestSchemaVersion - SCHEMA_FREEZE_POINT
        val actualMigrationCount = HodithDatabase.MIGRATIONS.size + HodithDatabase.AUTO_MIGRATION_COUNT

        assertEquals(expectedMigrationCount, actualMigrationCount)
    }
}
