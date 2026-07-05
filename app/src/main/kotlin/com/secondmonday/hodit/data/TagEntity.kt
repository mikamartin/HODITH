package com.secondmonday.hodit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags", indices = [Index("name", unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
