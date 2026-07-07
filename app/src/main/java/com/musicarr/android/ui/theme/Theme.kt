package com.musicarr.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF22C55E)
private val GreenDark = Color(0xFF16A34A)

// Music apps live in the dark; one dark scheme for phone and TV alike keeps
// the two form factors identical and avoids washed-out covers on TV panels.
private val MusicarrColors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF06280F),
    primaryContainer = GreenDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9CA3AF),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF11161D),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1A212B),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF374151),
    error = Color(0xFFF87171),
)

@Composable
fun MusicarrTheme(content: @Composable () -> Unit) {
    // Deliberately dark in both system modes.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = MusicarrColors, content = content)
}
