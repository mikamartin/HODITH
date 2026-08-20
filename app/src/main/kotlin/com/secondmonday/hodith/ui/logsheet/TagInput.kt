package com.secondmonday.hodith.ui.logsheet

/**
 * Pure tag-entry logic split out of [LogDetailSheet]'s `TagEditor` so it's unit-testable on the
 * JVM without Compose (same rationale as `ui.bigpicture.BigPictureFilterState`). Tag matching is
 * case-insensitive throughout — it mirrors `viewmodel.tagDiff` and Case Edit's own duplicate-name
 * check, so typing "Coffee" against an existing "coffee" reuses the existing tag rather than
 * creating a near-duplicate.
 *
 * This function: the tag to add for a typed [input], or null if there's nothing to add (blank,
 * or already present in [existingTags]).
 */
internal fun tagToAdd(
    input: String,
    existingTags: List<String>,
): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (existingTags.any { it.equals(trimmed, ignoreCase = true) }) return null
    return trimmed
}

/** Which of [suggestions] are worth showing: not already in [selectedTags], and matching [query] if one is typed. */
internal fun filterTagSuggestions(
    suggestions: List<String>,
    selectedTags: List<String>,
    query: String,
): List<String> =
    suggestions.filter { suggestion ->
        selectedTags.none { it.equals(suggestion, ignoreCase = true) } &&
            (query.isEmpty() || suggestion.contains(query, ignoreCase = true))
    }
