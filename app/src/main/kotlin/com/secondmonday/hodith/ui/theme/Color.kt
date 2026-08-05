package com.secondmonday.hodith.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.secondmonday.hodith.data.AppTheme

/**
 * One [ColorScheme] per theme × light/dark (spec §12's "full light/dark mode within each
 * theme"). Roles not set here (tertiary family, inverse*, scrim, surfaceTint, the extra
 * surfaceContainer tiers) intentionally keep Material3's baseline values — nothing in the app
 * renders them prominently today; revisit if that changes.
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
internal val PlainLightBackground = Color(0xFFF4F6F8)
internal val PlainLightSurface = Color(0xFFFFFFFF)
internal val PlainLightOnSurface = Color(0xFF1B2126)
internal val PlainLightOnSurfaceVariant = Color(0xFF5B6670)
internal val PlainLightError = Color(0xFFBA1A1A)
internal val PlainLightOnError = Color(0xFFFFFFFF)

private val plainLight =
    lightColorScheme(
        primary = PlainLightPrimary,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC7E8ED),
        onPrimaryContainer = Color(0xFF082024),
        secondary = Color(0xFF57646C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDAE4E9),
        onSecondaryContainer = Color(0xFF141E24),
        error = PlainLightError,
        onError = PlainLightOnError,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = PlainLightBackground,
        onBackground = Color(0xFF1B2126),
        surface = PlainLightSurface,
        onSurface = PlainLightOnSurface,
        surfaceVariant = Color(0xFFDEE4E7),
        onSurfaceVariant = PlainLightOnSurfaceVariant,
        surfaceContainerHigh = Color(0xFFE9ECEE),
        outline = Color(0xFFD3DAE0),
        outlineVariant = Color(0xFFE7ECEE),
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
        surfaceContainerHigh = Color(0xFF232A2E),
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
        surfaceContainerHigh = Color(0xFFEDEDEB),
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
        surfaceContainerHigh = Color(0xFF232326),
        outline = Color(0xFF3A3A3E),
        outlineVariant = Color(0xFF2A2A2D),
    )

// Bright: the "playful accent pair" is a real functional pair, not decorative — secondary
// is the turquoise half, used for selected states, distinct from the coral primary.
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
        onBackground = Color(0xFF2B2118),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF2B2118),
        surfaceVariant = Color(0xFFF2E4D4),
        onSurfaceVariant = Color(0xFF8A7A68),
        surfaceContainerHigh = Color(0xFFFBEEE0),
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
        onBackground = Color(0xFFFFF3E6),
        surface = Color(0xFF35271F),
        onSurface = Color(0xFFFFF3E6),
        surfaceVariant = Color(0xFF4A372C),
        onSurfaceVariant = Color(0xFFC9AF9C),
        surfaceContainerHigh = Color(0xFF403026),
        outline = Color(0xFF4A372C),
        outlineVariant = Color(0xFF5C4736),
    )
