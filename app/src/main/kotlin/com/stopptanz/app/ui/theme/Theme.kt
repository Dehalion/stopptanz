package com.stopptanz.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

private val NeonColorScheme = darkColorScheme(
    primary = NeonPurple,
    secondary = NeonPink,
    tertiary = NeonCyan,
    background = NeonBackgroundBottom,
    surface = NeonSurface,
    onBackground = NeonTextPrimary,
    onSurface = NeonTextPrimary,
)

private val NeonShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

val NeonBackgroundBrush = Brush.verticalGradient(listOf(NeonBackgroundTop, NeonBackgroundBottom))
val NeonPrimaryButtonBrush = Brush.horizontalGradient(listOf(NeonPink, NeonPurple))
val NeonAccentButtonBrush = Brush.horizontalGradient(listOf(NeonCyan, NeonPurple))
val NeonTitleBrush = Brush.horizontalGradient(listOf(NeonPink, NeonPurple, NeonCyan))
val NeonTimerBrush = Brush.horizontalGradient(listOf(NeonPink, NeonCyan))

@Composable
fun StopptanzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonColorScheme,
        shapes = NeonShapes,
        content = content,
    )
}
