package com.secondmonday.hodith.ui.case

/**
 * Curated icon set for the Case icon picker (§5, §14). A fixed app-defined list rather than
 * the system emoji keyboard, so the picker looks consistent across devices/OS versions.
 *
 * The picker always shows exactly 42 icons: these defaults first, backfilled at random from
 * [CASE_ICONS_RESERVE] when a default icon is already used by another active Case (see
 * [com.secondmonday.hodith.viewmodel.availableCaseIcons]). Grouped here by eye, not by any
 * in-app label.
 */
val CASE_ICONS_DEFAULT =
    listOf(
        // Mood/feelings
        "😊",
        "😴",
        "😢",
        "😡",
        // Communication
        "🗣️",
        "💬",
        "🤝",
        // Family/pets
        "👶",
        "🐶",
        "🐱",
        "🐾",
        // Health/body
        "🤒",
        "🤧",
        "🤕",
        "🩹",
        "💉",
        "🩸",
        "💊",
        "🦷",
        // Habits/vices
        "☕️",
        "🚬",
        "🍷",
        "🍺",
        "🍔",
        // Life admin
        "💸",
        "💼",
        "⏰",
        "🍽️",
        "🧹",
        // Screens/entertainment
        "🎮",
        "🎵",
        "📚",
        "🎨",
        // Fitness
        "🏃",
        "🧘",
        // Weather/travel
        "🚗",
        // Misc/quirky
        "✨",
        "🎉",
        "🦄",
        "🛸",
        "🧩",
        "🐌",
    )

/** Backfill-only reserve, shuffled in whenever a default icon is unavailable. See [CASE_ICONS_DEFAULT]. */
val CASE_ICONS_RESERVE =
    listOf(
        // Self-care
        "🧴",
        "🛁",
        "🙏",
        // Screens/entertainment
        "🎬",
        // Weather/travel
        "☀️",
        "🌧️",
        "❄️",
        "🚆",
        // Life admin
        "🛏️",
        "🧺",
        "🧾",
        // Misc/quirky
        "🎲",
        "🪄",
        "🎭",
        "🔮",
        "🤡",
        "🦋",
    )
