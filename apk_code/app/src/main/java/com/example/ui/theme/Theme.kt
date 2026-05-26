package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = LoveDarkPrimary,
    secondary = LoveDarkSecondary,
    tertiary = LoveDarkTertiary,
    background = LoveDarkBackground,
    surface = LoveDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFFFF0F5),
    onSurface = Color(0xFFFFF0F5)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LovePrimary,
    secondary = LoveSecondary,
    tertiary = LoveTertiary,
    background = LoveBackground,
    surface = LoveSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2C1520),
    onSurface = Color(0xFF2C1520)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Always preserve our curated Valentin's theme style
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
