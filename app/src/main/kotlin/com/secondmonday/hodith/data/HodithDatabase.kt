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
 * [DropStaleNudgeColumn]); v8 adds the `hunches.metric` / `observationWindow` / `windowStartDate`
 * columns (spec §8), a pure additive auto-migration carrying column defaults; v9 drops the
 * `cases.hunchNudgeDismissed` column (see [DropHunchNudgeDismissedColumn]) now the Hunch-nudge
 * "don't ask again" opt-out is gone; v10 adds the `hunches.resolved*` verdict-snapshot columns and
 * `resolvedVerdictSnapshotTaken`, another pure additive auto-migration — schema-only, since a
 * migration can't run [com.secondmonday.hodith.domain.computeVerdict]; the one-time backfill that
 * populates them for pre-existing resolved Hunches runs from `HodithApplication` on next launch
 * instead. v11 adds `events.utcOffsetMinutes`, another pure additive auto-migration with a static
 * `0` column default — no production installs exist yet to backfill correctly, so old rows simply
 * read as UTC until re-logged. `@Database.version` can't be read back via reflection (Room's
 * annotation uses [AnnotationRetention.BINARY]), so this is the one place migration-guard tests
 * should get the current version from instead of a second hardcoded literal.
 */
const val HODITH_DATABASE_VERSION = 11

/** Schema versions at or below this shipped without migrations; every version past it needs one. */
const val SCHEMA_FREEZE_POINT = 6

/** v6 → v7: the 24h stale-ongoing prompt was removed, so its dismissal timestamp is dead weight. */
@DeleteColumn(tableName = "events", columnName = "staleNudgeDismissedAt")
class DropStaleNudgeColumn : AutoMigrationSpec

/** v8 → v9: the Hunch-nudge "don't ask again" opt-out was removed, so its flag is dead weight. */
@DeleteColumn(tableName = "cases", columnName = "hunchNudgeDismissed")
class DropHunchNudgeDismissedColumn : AutoMigrationSpec

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
    autoMigrations = [
        AutoMigration(from = 6, to = 7, spec = DropStaleNudgeColumn::class),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = DropHunchNudgeDismissedColumn::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ],
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
        const val AUTO_MIGRATION_COUNT = 5
    }
}
