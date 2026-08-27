package com.secondmonday.hodith.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.secondmonday.hodith.data.AppTheme

/**
 * One [ColorScheme] per theme × light/dark (spec §12's "full light/dark mode within each
 * theme"). Every surfaceContainer tier is authored in all six schemes (Lowest/Low/plain/
 * Highest matching that scheme's own `surface`, `High` its own deliberate value) — any tier left
 * unset falls back to `lightColorScheme`/`darkColorScheme`'s hardcoded M3 baseline default
 * (a generic purple unrelated to this app's palette), which leaked into any plain `Card()`
 * (Insights/Hunch) and the bottom `NavigationBar`'s default background before this was found.
 * Roles not set here (inverse*, scrim, surfaceTint, and the tertiary family outside Plain's
 * `tertiaryContainer`/`onTertiaryContainer`) intentionally keep Material3's baseline values —
 * nothing in the app renders them prominently today; revisit if that changes.
 */
fun hodithColorScheme(
    theme: AppTheme,
    darkTheme: Boolean,
): ColorScheme =
    when (theme) {
        AppTheme.PLAIN -> if (darkTheme) plainDark else plainLight
        AppTheme.INTENSE -> if (darkTheme) intenseDark else intenseLight
        AppTheme.BRIGHT -> if (darkTheme) brightDark else brightLight
    }

// Named rather than inlined below, specifically for these seven roles: Glance widget theming
// can't consume a Compose Material3 ColorScheme (DEV_PLAYBOOK.md §4), so WidgetCommon.kt's
// WidgetPalette — which renders the Plain theme's light palette regardless of the user's chosen
// in-app theme — sources these same values from here instead of duplicating the hex literals.
internal val PlainLightPrimary = Color(0xFF3A6B76)
internal val PlainLightBackground = Color(0xFFEAF5FC)
internal val PlainLightSurface = Color(0xFFFFFFFF)
internal val PlainLightOnSurface = Color(0xFF071620)
internal val PlainLightOnSurfaceVariant = Color(0xFF164156)
internal val PlainLightError = Color(0xFFBA1A1A)
internal val PlainLightOnError = Color(0xFFFFFFFF)

private val plainLight =
    lightColorScheme(
        primary = PlainLightPrimary,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC7E8ED),
        onPrimaryContainer = Color(0xFF082024),
        secondary = Color(0xFF12394C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE6F5),
        onSecondaryContainer = Color(0xFF0A2A36),
        // Settings-only accent: ActionRow buttons and the theme/check-in segmented pickers need a
        // lighter, colder tone than secondaryContainer (which stays punchier for Insights' chips
        // and the nav indicator pill) — see docs/mockups/plain-theme-light-neutrals.html.
        tertiaryContainer = Color(0xFFBAD7E6),
        onTertiaryContainer = Color(0xFF071620),
        error = PlainLightError,
        onError = PlainLightOnError,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = PlainLightBackground,
        onBackground = Color(0xFF071620),
        surface = PlainLightSurface,
        onSurface = PlainLightOnSurface,
        surfaceVariant = Color(0xFFCFE8F8),
        onSurfaceVariant = PlainLightOnSurfaceVariant,
        // Every surfaceContainer tier authored white: Insights/Hunch's plain Card() (and any
        // other unstyled M3 component) defaults to one of these depending on the exact
        // component, and leaving any tier unset doesn't derive a neutral from this scheme's own
        // primary — lightColorScheme(...) fills an unset parameter with M3's stock default
        // (a generic purple), unrelated to this app's palette. Found via screenshots showing
        // Insights/Hunch cards rendering that stock purple instead of a white plank.
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFFFFFFF),
        surfaceContainerHighest = Color(0xFFFFFFFF),
        outline = Color(0xFF6FB8DE),
        outlineVariant = Color(0xFF9CCEE8),
    )

private val plainDark =
    darkColorScheme(
        primary = Color(0xFF6FA8B5),
        onPrimary = Color(0xFF0B1416),
        primaryContainer = Color(0xFF1F3A40),
        onPrimaryContainer = Color(0xFFC7E8ED),
        secondary = Color(0xFFB8C4C9),
        onSecondary = Color(0xFF283338),
        secondaryContainer = Color(0xFF3F4C51),
        onSecondaryContainer = Color(0xFFD4E0E4),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF14181B),
        onBackground = Color(0xFFE7ECEF),
        surface = Color(0xFF1C2226),
        onSurface = Color(0xFFE7ECEF),
        surfaceVariant = Color(0xFF3A444A),
        onSurfaceVariant = Color(0xFF96A3AB),
        // Same reasoning as plainLight: every surfaceContainer tier authored (matching surface)
        // rather than left to leak M3's stock baseline purple into Insights/Hunch cards.
        surfaceContainerLowest = Color(0xFF1C2226),
        surfaceContainerLow = Color(0xFF1C2226),
        surfaceContainer = Color(0xFF1C2226),
        surfaceContainerHigh = Color(0xFF232A2E),
        surfaceContainerHighest = Color(0xFF232A2E),
        outline = Color(0xFF2A3237),
        outlineVariant = Color(0xFF3A444A),
    )

// Intense: monochrome (black/white/gray) plus exactly one accent, crimson, reserved for
// interactive elements — error uses a distinct amber/orange so "destructive" never gets
// confused with ordinary crimson-accented actions.
private val intenseLight =
    lightColorScheme(
        primary = Color(0xFFA3132E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9DE),
        onPrimaryContainer = Color(0xFF3F0012),
        secondary = Color(0xFF5A5A5E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE3E3E1),
        onSecondaryContainer = Color(0xFF1C1C1E),
        error = Color(0xFF8A4B00),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDDB3),
        onErrorContainer = Color(0xFF2B1700),
        background = Color(0xFFFAFAF8),
        onBackground = Color(0xFF141414),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF141414),
        surfaceVariant = Color(0xFFE3E3E1),
        onSurfaceVariant = Color(0xFF6B6B6B),
        // Same reasoning as plainLight: every surfaceContainer tier authored (Lowest/Low/plain/
        // Highest matching surface) rather than left to leak M3's stock baseline purple into
        // Insights/Hunch cards. surfaceContainerHigh keeps its existing deliberate value.
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFEDEDEB),
        surfaceContainerHighest = Color(0xFFFFFFFF),
        outline = Color(0xFF3D3D3D),
        outlineVariant = Color(0xFFD8D8D6),
    )

private val intenseDark =
    darkColorScheme(
        primary = Color(0xFFD0324C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF5C0A1F),
        onPrimaryContainer = Color(0xFFFFD9DE),
        secondary = Color(0xFFB8B8BC),
        onSecondary = Color(0xFF2A2A2D),
        secondaryContainer = Color(0xFF3F3F43),
        onSecondaryContainer = Color(0xFFEAEAEC),
        error = Color(0xFFFFB870),
        onError = Color(0xFF452B00),
        errorContainer = Color(0xFF643F00),
        onErrorContainer = Color(0xFFFFDDB3),
        background = Color(0xFF0D0D0F),
        onBackground = Color(0xFFF2F2F0),
        surface = Color(0xFF18181B),
        onSurface = Color(0xFFF2F2F0),
        surfaceVariant = Color(0xFF3A3A3E),
        onSurfaceVariant = Color(0xFF9A9A9E),
        surfaceContainerLowest = Color(0xFF18181B),
        surfaceContainerLow = Color(0xFF18181B),
        surfaceContainer = Color(0xFF18181B),
        surfaceContainerHigh = Color(0xFF232326),
        surfaceContainerHighest = Color(0xFF18181B),
        outline = Color(0xFF3A3A3E),
        outlineVariant = Color(0xFF2A2A2D),
    )

// Bright: the "playful accent pair" is a real functional pair, not decorative — secondary
// is the turquoise half, used for selected states, distinct from the coral primary.
//
// onBackground/onSurface/BrightXHeadingInk aren't flat ink — both are the primary blended into
// a warm neutral (Soft Glow mockup formula, docs/mockups/bright-theme-soft-glow.html: 26% primary
// for body-weight ink, 52% for heading-weight ink), so text reads as part of the coral/turquoise
// palette rather than generic near-black/near-white. *HeadingInk isn't a ColorScheme role — it's
// for Baloo 2 heading-weight text (screen titles, card labels) once those screens pick it up
// (PROGRESS.md's Bright theme redesign checklist); body-weight ink covers everything else via
// onBackground/onSurface.
internal val BrightLightHeadingInk = Color(0xFFA8523A)
internal val BrightDarkHeadingInk = Color(0xFFFFB8A3)

private val brightLight =
    lightColorScheme(
        primary = Color(0xFFFF6B4A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDBCF),
        onPrimaryContainer = Color(0xFF3A0900),
        secondary = Color(0xFF17B3A3),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCBF1EB),
        onSecondaryContainer = Color(0xFF00201C),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFF7ED),
        onBackground = Color(0xFF794531),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF794531),
        surfaceVariant = Color(0xFFF2E4D4),
        onSurfaceVariant = Color(0xFF8A7A68),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFFBEEE0),
        surfaceContainerHighest = Color(0xFFFFFFFF),
        outline = Color(0xFFF2DCC4),
        outlineVariant = Color(0xFFF6E9D9),
    )

private val brightDark =
    darkColorScheme(
        primary = Color(0xFFFF8266),
        onPrimary = Color(0xFF3A0900),
        primaryContainer = Color(0xFF5C2210),
        onPrimaryContainer = Color(0xFFFFDBCF),
        secondary = Color(0xFF34D6C4),
        onSecondary = Color(0xFF00201C),
        secondaryContainer = Color(0xFF0B4A42),
        onSecondaryContainer = Color(0xFFCBF1EB),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF2A1E1A),
        onBackground = Color(0xFFFFD6C5),
        surface = Color(0xFF35271F),
        onSurface = Color(0xFFFFD6C5),
        surfaceVariant = Color(0xFF4A372C),
        onSurfaceVariant = Color(0xFFC9AF9C),
        surfaceContainerLowest = Color(0xFF35271F),
        surfaceContainerLow = Color(0xFF35271F),
        surfaceContainer = Color(0xFF35271F),
        surfaceContainerHigh = Color(0xFF403026),
        surfaceContainerHighest = Color(0xFF35271F),
        outline = Color(0xFF4A372C),
        outlineVariant = Color(0xFF5C4736),
    )
