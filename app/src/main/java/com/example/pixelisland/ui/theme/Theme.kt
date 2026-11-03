package com.example.pixelisland.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive with the user's wallpaper-derived dynamic color (what Pixel's own apps use),
 * expressive spring motion, and the emphasized type styles that come with the expressive theme.
 * Font: the system font (Google Sans / Google Sans Flex on Pixel) – we deliberately don't bundle one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelIslandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
