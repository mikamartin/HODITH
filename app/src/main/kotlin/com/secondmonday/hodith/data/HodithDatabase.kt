package com.secondmonday.hodith.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [
        CaseEntity::class,
        EventEntity::class,
        TagEntity::class,
        EventTagCrossRef::class,
        HunchEntity::class,
        TriggerEntity::class,
    ],
    version = 6,
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
        /** Freeze point is v6: v1-5 never shipped, so no migration is needed until v7. */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
