package com.secondmonday.hodith.data.backup

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.loggedZone
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val CSV_HEADER = "case_name,occurred_at,ended_at,duration_minutes,intensity,tags,note"
private const val CRLF = "\r\n"
private const val MILLIS_PER_MINUTE = 60_000L

/** Always renders seconds and a numeric offset (`+00:00`, never `Z`) so every row is uniform regardless of whether a timestamp happens to fall on a whole minute. */
private val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

/**
 * [BackupData] flattened into a human-readable, spreadsheet-friendly CSV: one row per event
 * (spec §17). Export-only - a flattened tabular shape can't round-trip cleanly back into the
 * relational schema, so unlike [BackupSerializer] there is no `fromCsv`. Archived cases (and their
 * events) are left out, since a CSV is more likely to be opened and read directly by a person than
 * the JSON backup is.
 */
class CsvBackupSerializer
    @Inject
    constructor() {
        fun toCsv(data: BackupData): String {
            val activeCases = data.cases.filterNot { it.archived }
            val eventsByCase = data.events.groupBy { it.caseId }
            val tagNamesById = data.tags.associateBy({ it.id }, { it.name })
            val tagIdsByEvent = data.eventTags.groupBy({ it.eventId }, { it.tagId })

            return buildString {
                append(CSV_HEADER).append(CRLF)
                activeCases.forEach { case ->
                    eventsByCase[case.id]
                        .orEmpty()
                        .sortedBy { it.occurredAt }
                        .forEach { event ->
                            val tagNames = tagIdsByEvent[event.id].orEmpty().mapNotNull { tagNamesById[it] }.sorted()
                            append(rowFor(case.name, event, tagNames)).append(CRLF)
                        }
                }
            }
        }

        private fun rowFor(
            caseName: String,
            event: EventEntity,
            tagNames: List<String>,
        ): String {
            val durationMinutes = event.endedAt?.let { (it - event.occurredAt) / MILLIS_PER_MINUTE }
            val fields =
                listOf(
                    csvField(caseName),
                    csvField(isoOffsetDateTime(event.occurredAt, event.loggedZone())),
                    event.endedAt?.let { csvField(isoOffsetDateTime(it, event.loggedZone())) } ?: "",
                    durationMinutes?.toString() ?: "",
                    event.intensity?.toString() ?: "",
                    csvField(tagNames.joinToString(", ")),
                    csvField(event.note.orEmpty()),
                )
            return fields.joinToString(",")
        }

        private fun isoOffsetDateTime(
            millis: Long,
            offset: ZoneOffset,
        ): String = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(millis).atOffset(offset))

        private fun csvField(value: String): String =
            if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
    }
