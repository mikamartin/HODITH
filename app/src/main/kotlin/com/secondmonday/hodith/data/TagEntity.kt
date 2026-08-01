package com.secondmonday.hodith.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "tags", indices = [Index("name", unique = true)])
@JsonClass(generateAdapter = true)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
