package com.secondmonday.hodith.data.backup

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import javax.inject.Inject

/** JSON <-> [BackupData]. Callers handle [JsonDataException]/[java.io.IOException] on malformed input. */
class BackupSerializer
    @Inject
    constructor(
        moshi: Moshi,
    ) {
        private val adapter = moshi.adapter(BackupData::class.java)

        fun toJson(data: BackupData): String = adapter.toJson(data)

        fun fromJson(json: String): BackupData = adapter.fromJson(json) ?: throw JsonDataException("Backup JSON parsed to null")
    }
