package dev.busung.s25uroot.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Shared glass-inspired visual treatment without platform blur or new dependencies. */
@Composable
internal fun galaxyGlassBorder(): BorderStroke {
    val colors = MaterialTheme.colorScheme
    return BorderStroke(
        1.dp,
        Brush.linearGradient(
            listOf(
                lerp(colors.outlineVariant, Color.White, 0.38f),
                colors.outlineVariant.copy(alpha = 0.65f),
                colors.primary.copy(alpha = 0.32f),
            ),
        ),
    )
}

@Composable
internal fun galaxyGlassBackground(): Modifier {
    val colors = MaterialTheme.colorScheme
    return Modifier.background(
        Brush.verticalGradient(
            listOf(
                lerp(colors.surface, colors.primaryContainer, 0.60f),
                colors.surface,
                lerp(colors.surface, colors.secondaryContainer, 0.38f),
            ),
        ),
    )
}

@Composable
internal fun galaxyGlassPanel(): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
