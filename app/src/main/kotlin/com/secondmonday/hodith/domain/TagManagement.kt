package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.TagEntity

/** Above this many tags the management screen offers a filter; at or below it the list is short enough to scan. */
const val TAG_FILTER_THRESHOLD = 10

/** A tag with how many events carry it, across every Case. */
data class TagSummary(
    val tag: TagEntity,
    val eventCount: Int,
)

/** What a rename request resolves to, decided before any write so the warning can state it. */
sealed interface TagRenameOutcome {
    /** Nothing to change: blank input, or the same spelling as the tag already has. */
    data object NoChange : TagRenameOutcome

    /** The new name is free (no other tag matches it ignoring case): the tag is renamed in place. */
    data object Rename : TagRenameOutcome

    /** The new name belongs to another tag: the tags merge into [target]. */
    data class Merge(
        val target: TagEntity,
    ) : TagRenameOutcome
}

fun shouldShowTagFilter(tagCount: Int): Boolean = tagCount > TAG_FILTER_THRESHOLD

/** Blank [query] shows every tag. Otherwise a case-insensitive substring match on the tag name. */
fun filterTagSummaries(
    tags: List<TagSummary>,
    query: String,
): List<TagSummary> {
    val needle = query.trim()
    if (needle.isEmpty()) return tags
    return tags.filter { it.tag.name.contains(needle, ignoreCase = true) }
}

/**
 * [collision] is the other tag (not [source]) that the requested name matches case-insensitively,
 * as the repository's lookup finds it. A case-only change on [source] itself has no collision and
 * is a plain rename.
 */
fun classifyTagRename(
    source: TagEntity,
    requestedName: String,
    collision: TagEntity?,
): TagRenameOutcome {
    val trimmed = requestedName.trim()
    return when {
        trimmed.isEmpty() || trimmed == source.name -> TagRenameOutcome.NoChange
        collision != null && collision.id != source.id -> TagRenameOutcome.Merge(collision)
        else -> TagRenameOutcome.Rename
    }
}
