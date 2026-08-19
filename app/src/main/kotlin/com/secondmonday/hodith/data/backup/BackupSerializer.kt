package com.secondmonday.hodith.data.backup

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import javax.inject.Inject

private const val SCHEMA_VERSION_KEY = "schemaVersion"

/** One step in the backup-upgrade chain: brings a raw payload declared at [fromVersion] forward by one version. */
interface BackupUpgradeStep {
    val fromVersion: Int

    fun upgrade(raw: Map<String, Any?>): Map<String, Any?>
}

/**
 * Registered upgrade steps. Empty today: no backup schema version has ever shipped besides the
 * current one, so there is nothing to upgrade from yet. The first entry lands the day
 * [BACKUP_SCHEMA_VERSION] bumps to 2.
 */
private val UPGRADE_STEPS: List<BackupUpgradeStep> = emptyList()

/**
 * Applies every step in [steps] whose [BackupUpgradeStep.fromVersion] falls in
 * `[declaredVersion, targetVersion)`, in ascending order. A no-op when no step matches - the only
 * case reachable today, since [UPGRADE_STEPS] is still empty. Exposed as a standalone function
 * (rather than inlined into [BackupSerializer.fromJson]) so the fold/filter/sort mechanism is
 * unit-testable against a fake [BackupUpgradeStep] without a real historical payload to test.
 */
internal fun applyUpgradeSteps(
    raw: Map<String, Any?>,
    declaredVersion: Int,
    targetVersion: Int,
    steps: List<BackupUpgradeStep>,
): Map<String, Any?> =
    steps
        .filter { it.fromVersion in declaredVersion until targetVersion }
        .sortedBy { it.fromVersion }
        .fold(raw) { payload, step -> step.upgrade(payload) }

/** JSON <-> [BackupData]. Callers handle [JsonDataException]/[IOException] on malformed input. */
class BackupSerializer
    @Inject
    constructor(
        moshi: Moshi,
    ) {
        private val adapter = moshi.adapter(BackupData::class.java)
        private val rawMapAdapter: JsonAdapter<Map<String, Any?>> =
            moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

        fun toJson(data: BackupData): String = adapter.toJson(data)

        fun fromJson(json: String): BackupData = adapter.fromJson(json) ?: throw JsonDataException("Backup JSON parsed to null")

        /**
         * Reads just `schemaVersion` from [json] without requiring the rest of the payload to match
         * the current [BackupData] shape, so callers can decide how to handle an older or newer file
         * before running it through the strict adapter. Returns null if [json] isn't a parseable
         * JSON object at all - a genuinely malformed/non-JSON payload, not merely an old one.
         */
        fun peekSchemaVersion(json: String): Int? =
            try {
                val raw = rawMapAdapter.fromJson(json) ?: return null
                // Moshi's generic Map<String, Any?> adapter decodes JSON numbers as Double, never Int.
                (raw[SCHEMA_VERSION_KEY] as? Double)?.toInt() ?: BACKUP_SCHEMA_VERSION
            } catch (e: JsonDataException) {
                null
            } catch (e: IOException) {
                null
            }

        /**
         * Parses [json] declared at [declaredVersion]. At the current version this is identical to
         * [fromJson]. For an older version, folds the raw payload through [applyUpgradeSteps] before
         * parsing - a no-op fold (no matching step registered, the only case reachable today) falls
         * through to the strict parse unchanged, so it still succeeds or fails on its own merits
         * rather than silently dropping data.
         */
        fun fromJson(
            json: String,
            declaredVersion: Int,
        ): BackupData {
            if (declaredVersion == BACKUP_SCHEMA_VERSION) return fromJson(json)

            val raw = rawMapAdapter.fromJson(json) ?: throw JsonDataException("Backup JSON parsed to null")
            val upgraded = applyUpgradeSteps(raw, declaredVersion, BACKUP_SCHEMA_VERSION, UPGRADE_STEPS)

            return adapter.fromJsonValue(upgraded) ?: throw JsonDataException("Backup JSON parsed to null")
        }
    }
