package com.better.nothing.music.vizualizer.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R

@Composable
fun BetterVizTheme(
    themeName: String = "Default",
    fontName: String = "NDot",
    m3eEnabled: Boolean = true,
    musicPrimaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val restrictedLocales = listOf("hi", "ar", "ja", "ru", "zh")
    val currentLocale = context.resources.configuration.locales[0].language
    val isRestrictedLocale = restrictedLocales.contains(currentLocale)

    val finalFontName = if (isRestrictedLocale) "Google Sans Flex" else fontName
    val useNType = finalFontName == "NType"
    val useGoogleSans = finalFontName == "Google Sans Flex" || (finalFontName != "NDot" && finalFontName != "NType")

    val isDark = isSystemInDarkTheme()

    val targetColorScheme = remember(themeName, isDark, musicPrimaryColor) {
        when (themeName) {
            "Material You" -> {
                val base = if (isDark) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dynamicDarkColorScheme(context)
                    } else {
                        androidx.compose.material3.darkColorScheme(
                            background = Color.Black
                        )
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dynamicLightColorScheme(context)
                    } else {
                        androidx.compose.material3.lightColorScheme(
                            background = Color.White
                        )
                    }
                }
                
                // Boost surface brightness for better contrast against background
                if (isDark) {
                    base.copy(
                        surface = Color(ColorUtils.blendARGB(base.surface.toArgb(), Color.White.toArgb(), 0.1f)),
                    )
                } else {
                    base.copy(
                        surface = Color(ColorUtils.blendARGB(base.surface.toArgb(), Color.White.toArgb(), 0.5f)),
                    )
                }
            }
            "Nothing" -> {
                if (isDark) {
                    // Nothing Red (Branded Dark)
                    androidx.compose.material3.darkColorScheme(
                        background = Color.Black,
                        surface = Color(0xFF0D0D0D),
                        primary = Color(0xFFD71921),    // Authentic Nothing Red
                        secondary = Color(0xFFD71921),
                        error = Color(0xFFD71921),
                        onBackground = Color.White,
                        onSurface = Color.White,
                        onPrimary = Color.White,
                        onSecondary = Color.White,
                        onError = Color.White,
                        surfaceVariant = Color(0xFF1A1A1A),
                        onSurfaceVariant = Color(0xFFB3B3B3),
                        outline = Color(0xFF333333)
                    )
                } else {
                    // Nothing Light (Branded Light)
                    androidx.compose.material3.lightColorScheme(
                        background = Color.White,
                        surface = Color(0xFFF5F5F5),
                        primary = Color(0xFF000000),
                        secondary = Color(0xFF626262),
                        error = Color(0xFFD71921),
                        onBackground = Color.Black,
                        onSurface = Color.Black,
                        onPrimary = Color.White,
                        onSecondary = Color.White,
                        onError = Color.White,
                        surfaceVariant = Color(0xFFE0E0E0),
                        onSurfaceVariant = Color(0xFF757575),
                        outline = Color(0xFFBDBDBD)
                    )
                }
            }
            "Nothing Red" -> {
                // Fallback for old selection, same as Nothing Dark
                androidx.compose.material3.darkColorScheme(
                    background = Color.Black,
                    surface = Color(0xFF0D0D0D),
                    primary = Color(0xFFD71921),
                    secondary = Color(0xFFD71921),
                    error = Color(0xFFD71921),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onError = Color.White,
                    surfaceVariant = Color(0xFF1A1A1A),
                    onSurfaceVariant = Color(0xFFB3B3B3),
                    outline = Color(0xFF333333)
                )
            }
            "Liquorice Black" -> {
                androidx.compose.material3.darkColorScheme(
                    background = Color(0xFF0F0F0F),
                    surface = Color(0xFF1A1A1A),
                    primary = Color(0xFFD8D3DA),
                    secondary = Color(0xFFA0FFA3),
                    error = Color(0xFFC83B3B),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onPrimary = Color(0xFF1C1A1D),
                    onSecondary = Color(0xFF1C5A21),
                    onError = Color.White,
                    surfaceVariant = Color(0xFF242424),
                    onSurfaceVariant = Color(0xFF676767),
                    outline = Color(0xFF2C2C2C)
                )
            }
            else -> { // Default / OLED Black
                if (themeName == "Default" && !isDark) {
                    // Use Nothing Light for Default in Light Mode
                    androidx.compose.material3.lightColorScheme(
                        background = Color.White,
                        surface = Color(0xFFF5F5F5),
                        primary = Color(0xFF000000),
                        secondary = Color(0xFF626262),
                        error = Color(0xFFD71921),
                        onBackground = Color.Black,
                        onSurface = Color.Black,
                        onPrimary = Color.White,
                        onSecondary = Color.White,
                        onError = Color.White,
                        surfaceVariant = Color(0xFFE0E0E0),
                        onSurfaceVariant = Color(0xFF757575),
                        outline = Color(0xFFBDBDBD)
                    )
                } else {
                    androidx.compose.material3.darkColorScheme(
                        background = Color.Black,
                        surface = Color(0xFF282828),
                        primary = Color(0xFFDCDCDC),
                        secondary = Color(0xFFA0FFA3),
                        error = Color(0xFFC83B3B),
                        onBackground = Color.White,
                        onSurface = Color.White,
                        onPrimary = Color(0xFF1C1A1D),
                        onSecondary = Color(0xFF1C5A21),
                        onError = Color.White,
                        surfaceVariant = Color(0xFF3C3C3C),
                        onSurfaceVariant = Color(0xFFA0A0A0),
                        outline = Color(0xFF2C2C2C)
                    )
                }
            }
        }
    }

    val colorScheme = targetColorScheme.copy(
        primary = animateColorAsState(targetColorScheme.primary, tween(500), label = "primary").value,
        onPrimary = animateColorAsState(targetColorScheme.onPrimary, tween(500), label = "onPrimary").value,
        secondary = animateColorAsState(targetColorScheme.secondary, tween(500), label = "secondary").value,
        onSecondary = animateColorAsState(targetColorScheme.onSecondary, tween(500), label = "onSecondary").value,
        error = animateColorAsState(targetColorScheme.error, tween(500), label = "error").value,
        onError = animateColorAsState(targetColorScheme.onError, tween(500), label = "onError").value,
        background = animateColorAsState(targetColorScheme.background, tween(500), label = "background").value,
        onBackground = animateColorAsState(targetColorScheme.onBackground, tween(500), label = "onBackground").value,
        surface = animateColorAsState(targetColorScheme.surface, tween(500), label = "surface").value,
        onSurface = animateColorAsState(targetColorScheme.onSurface, tween(500), label = "onSurface").value,
        surfaceVariant = animateColorAsState(targetColorScheme.surfaceVariant, tween(500), label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(targetColorScheme.onSurfaceVariant, tween(500), label = "onSurfaceVariant").value,
        outline = animateColorAsState(targetColorScheme.outline, tween(500), label = "outline").value,
    )

    val typography = remember(useNType, useGoogleSans, isRestrictedLocale) {
        val headerFont = if (useGoogleSans) GoogleSansFontFamily else if (useNType) NTypeFontFamily else NDot55FontFamily
        val headlineFont = if (useGoogleSans) GoogleSansFontFamily else if (useNType) NTypeFontFamily else NDotFontFamily

        Typography(
            // HEADERS
            displayLarge = TextStyle(
                fontFamily = headerFont,
                fontSize = if (isRestrictedLocale) 34.sp else 45.sp,
                lineHeight = if (isRestrictedLocale) 42.sp else 55.sp,
                fontWeight = FontWeight.Normal
            ),
            displayMedium = TextStyle(
                fontFamily = headerFont,
                fontSize = if (isRestrictedLocale) 28.sp else 36.sp,
                lineHeight = if (isRestrictedLocale) 36.sp else 44.sp,
                fontWeight = FontWeight.Normal
            ),
            displaySmall = TextStyle(
                fontFamily = headerFont,
                fontSize = if (isRestrictedLocale) 24.sp else 30.sp,
                lineHeight = if (isRestrictedLocale) 32.sp else 38.sp,
                fontWeight = FontWeight.Normal
            ),
            headlineLarge = TextStyle(
                fontFamily = headlineFont,
                fontSize = if (isRestrictedLocale) 24.sp else 32.sp,
                lineHeight = if (isRestrictedLocale) 32.sp else 40.sp,
                fontWeight = FontWeight.Normal
            ),
            headlineMedium = TextStyle(
                fontFamily = headlineFont,
                fontSize = if (isRestrictedLocale) 24.sp else 30.sp,
                lineHeight = if (isRestrictedLocale) 32.sp else 40.sp,
                fontWeight = FontWeight.Normal
            ),
            headlineSmall = TextStyle(
                fontFamily = headlineFont,
                fontSize = if (isRestrictedLocale) 18.sp else 24.sp,
                lineHeight = if (isRestrictedLocale) 24.sp else 32.sp,
                fontWeight = FontWeight.Normal
            ),

            // SUB-HEADERS
            titleLarge = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Normal
            ),
            titleMedium = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal
            ),
            titleSmall = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            ),

            // BODY & LABELS (Use Google Sans Flex for high legibility and branding)
            bodyLarge = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal
            ),
            bodyMedium = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal
            ),
            bodySmall = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal
            ),
            labelLarge = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            ),
            labelMedium = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            labelSmall = TextStyle(
                fontFamily = GoogleSansFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            ),
        )
    }

    val shapes = remember {
        Shapes(
            extraLarge = RoundedCornerShape(32.dp),
            large = RoundedCornerShape(28.dp),
            medium = RoundedCornerShape(20.dp),
            small = RoundedCornerShape(14.dp),
        )
    }

    val animatedEdge by animateDpAsState(
        targetValue = if (targetColorScheme.background == Color.Black) 6.dp else 16.dp,
        animationSpec = tween(500),
        label = "edgeSpacing"
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(colorScheme.background) {
            val window = (view.context as Activity).window
            val isLight = ColorUtils.calculateLuminance(colorScheme.background.toArgb().toLong().toInt()) > 0.5
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
        }
    }

    val appSpacing = remember { AppSpacing() }

    LaunchedEffect(animatedEdge) {
        appSpacing.edge = animatedEdge
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = typography
    ) {
        val mainViewModel = MainViewModel.instance
        val uiAmplitude = if (mainViewModel != null) {
            mainViewModel.uiAmplitude.collectAsStateWithLifecycle().value
        } else {
            1.0f
        }
        
        CompositionLocalProvider(
            LocalAppSpacing provides appSpacing,
            LocalM3EEnabled provides m3eEnabled,
            LocalUIAmplitude provides uiAmplitude
        ) {
            content()
        }
    }

}




val GoogleSansFontFamily = FontFamily.SansSerif

val NTypeFontFamily = FontFamily(
    Font(R.font.ntype82)
)

val NDotFontFamily = FontFamily(
    Font(resId = R.font.ndot57, weight = FontWeight.Normal)
)

val NDot55FontFamily = FontFamily(
    Font(resId = R.font.ndot55, weight = FontWeight.Normal)
)

@Immutable
class AppSpacing(
    edge: Dp = 6.dp,
    val between: Dp = 12.dp,
    val inner: Dp = 20.dp
) {
    var edge by mutableStateOf(edge)
        internal set
}

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalM3EEnabled = compositionLocalOf { true }
val LocalUIAmplitude = compositionLocalOf { 1.0f }
