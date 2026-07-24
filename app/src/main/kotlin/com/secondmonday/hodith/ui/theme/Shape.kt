package com.secondmonday.hodith.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme

/** One corner-radius scale per theme (spec §12), validated in the theme-review mockup. */
fun hodithShapes(theme: AppTheme): Shapes =
    when (theme) {
        AppTheme.PLAIN -> plainShapes
        AppTheme.INTENSE -> intenseShapes
        AppTheme.BRIGHT -> brightShapes
    }

private val plainShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(12.dp),
    )

private val intenseShapes =
    Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(2.dp),
        large = RoundedCornerShape(2.dp),
        extraLarge = RoundedCornerShape(4.dp),
    )

private val brightShapes =
    Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
