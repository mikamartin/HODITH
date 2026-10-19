package com.secondmonday.hodith.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

private const val SCHEMA_VERSION_KEY = "schemaVersion"

/** One step in the backup-upgrade chain: brings a raw payload declared at [fromVersion] forward by one version. */
interface BackupUpgradeStep {
    val fromVersion: Int

    fun upgrade(raw: JsonObject): JsonObject
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
    raw: JsonObject,
    declaredVersion: Int,
    targetVersion: Int,
    steps: List<BackupUpgradeStep>,
): JsonObject =
    steps
        .filter { it.fromVersion in declaredVersion until targetVersion }
        .sortedBy { it.fromVersion }
        .fold(raw) { payload, step -> step.upgrade(payload) }

/** JSON <-> [BackupData]. Callers handle [SerializationException] on malformed input. */
class BackupSerializer
    @Inject
    constructor(
        private val json: Json,
    ) {
        fun toJson(data: BackupData): String = json.encodeToString(data)

        fun fromJson(content: String): BackupData = json.decodeFromString<BackupData>(content)

        /**
         * Reads just `schemaVersion` from [content] without requiring the rest of the payload to
         * match the current [BackupData] shape, so callers can decide how to handle an older or
         * newer file before running it through the strict decoder. Returns null if [content] isn't
         * a parseable JSON object at all - a genuinely malformed/non-JSON payload, not merely an
         * old one.
         */
        fun peekSchemaVersion(content: String): Int? =
            try {
                val raw = json.parseToJsonElement(content) as? JsonObject ?: return null
                (raw[SCHEMA_VERSION_KEY] as? JsonPrimitive)?.intOrNull ?: BACKUP_SCHEMA_VERSION
            } catch (e: SerializationException) {
                null
            }

        /**
         * Parses [content] declared at [declaredVersion]. At the current version this is identical
         * to [fromJson]. For an older version, folds the raw payload through [applyUpgradeSteps]
         * before decoding - a no-op fold (no matching step registered, the only case reachable
         * today) falls through to the strict decode unchanged, so it still succeeds or fails on its
         * own merits rather than silently dropping data.
         */
        fun fromJson(
            content: String,
            declaredVersion: Int,
        ): BackupData {
            if (declaredVersion == BACKUP_SCHEMA_VERSION) return fromJson(content)

            val raw =
                json.parseToJsonElement(content) as? JsonObject
                    ?: throw SerializationException("Backup JSON is not an object")
            val upgraded = applyUpgradeSteps(raw, declaredVersion, BACKUP_SCHEMA_VERSION, UPGRADE_STEPS)

            return json.decodeFromJsonElement<BackupData>(upgraded)
        }
    }
