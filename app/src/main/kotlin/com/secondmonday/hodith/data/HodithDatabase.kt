package com.secondmonday.hodith.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * Freeze point is v6: v1-5 never shipped, so no migration is needed until v7. `@Database.version`
 * can't be read back via reflection (Room's annotation uses [AnnotationRetention.BINARY]), so this
 * is the one place migration-guard tests should get the frozen version from instead of a second
 * hardcoded literal.
 */
const val HODITH_DATABASE_VERSION = 6

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
    }
}
