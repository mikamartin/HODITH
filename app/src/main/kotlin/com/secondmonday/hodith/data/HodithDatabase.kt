package com.secondmonday.hodith.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration

/**
 * v1-5 never shipped, so [SCHEMA_FREEZE_POINT] (v6) is the first version a real migration is
 * required from. v7 drops the `events.staleNudgeDismissedAt` column via an auto-migration (see
 * [DropStaleNudgeColumn]). `@Database.version` can't be read back via reflection (Room's annotation
 * uses [AnnotationRetention.BINARY]), so this is the one place migration-guard tests should get the
 * current version from instead of a second hardcoded literal.
 */
const val HODITH_DATABASE_VERSION = 7

/** Schema versions at or below this shipped without migrations; every version past it needs one. */
const val SCHEMA_FREEZE_POINT = 6

/** v6 → v7: the 24h stale-ongoing prompt was removed, so its dismissal timestamp is dead weight. */
@DeleteColumn(tableName = "events", columnName = "staleNudgeDismissedAt")
class DropStaleNudgeColumn : AutoMigrationSpec

@Database(
    entities = [
        CaseEntity::class,
        EventEntity::class,
        TagEntity::class,
        EventTagCrossRef::class,
        HunchEntity::class,
        TriggerEntity::class,
    ],
    version = HODITH_DATABASE_VERSION,
    autoMigrations = [AutoMigration(from = 6, to = 7, spec = DropStaleNudgeColumn::class)],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HodithDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao

    abstract fun eventDao(): EventDao

    abstract fun tagDao(): TagDao

    abstract fun hunchDao(): HunchDao

    abstract fun triggerDao(): TriggerDao

    companion object {
        val MIGRATIONS: Array<Migration> = arrayOf()

        /**
         * Auto-migrations aren't in [MIGRATIONS] (Room registers them from the `@Database`
         * annotation directly), so the schema-coverage guard counts them here. Bump when adding an
         * `AutoMigration` entry above.
         */
        const val AUTO_MIGRATION_COUNT = 1
    }
}
