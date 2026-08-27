package com.jrstudio.svyazsbogom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DivineBlack = Color(0xFF05020A)
val DivinePurple = Color(0xFF7C3AED)
val DivineViolet = Color(0xFFA855F7)
val DivineLavender = Color(0xFFD8B4FE)
val DivineWhite = Color(0xFFF8F5FF)
val DivineMuted = Color(0xFFB7A9C8)

private val Colors = darkColorScheme(
    primary = DivineViolet,
    secondary = DivineLavender,
    background = DivineBlack,
    surface = Color(0xFF0E0915),
    onPrimary = Color.White,
    onBackground = DivineWhite,
    onSurface = DivineWhite
)

@Composable
fun SvyazSBogomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content
    )
}
