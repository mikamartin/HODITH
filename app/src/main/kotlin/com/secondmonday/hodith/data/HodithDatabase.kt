package com.secondmonday.hodith.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CaseEntity::class,
        EventEntity::class,
        TagEntity::class,
        EventTagCrossRef::class,
        HunchEntity::class,
        TriggerEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HodithDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao

    abstract fun eventDao(): EventDao

    abstract fun tagDao(): TagDao

    abstract fun hunchDao(): HunchDao

    abstract fun triggerDao(): TriggerDao
}
