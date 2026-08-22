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
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
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
    gSansWeight: Float = 400f,
    gSansWidth: Float = 100f,
    gSansSlant: Float = 0f,
    gSansOpsz: Float = 14f,
    gSansGrade: Float = 0f,
    gSansRounding: Float = 0f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val restrictedLocales = listOf("hi", "ar", "ja", "ru", "zh")
    val currentLocale = configuration.locales[0].language
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

    val typography = remember(
        useNType, useGoogleSans, isRestrictedLocale,
        gSansWeight, gSansWidth, gSansSlant, gSansOpsz, gSansGrade, gSansRounding
    ) {
        val googleSansFlex = if (useGoogleSans) {
            try {
                // Use a single Font object to avoid overhead
                val variationSettings = FontVariation.Settings(
                    FontVariation.Setting("wght", gSansWeight),
                    FontVariation.Setting("wdth", gSansWidth),
                    FontVariation.Setting("slnt", gSansSlant),
                    FontVariation.Setting("opsz", gSansOpsz),
                    FontVariation.Setting("GRAD", gSansGrade),
                    FontVariation.Setting("ROND", gSansRounding),
                    FontVariation.Setting("SOFT", gSansRounding),
                    FontVariation.Setting("BNHV", gSansRounding)
                )
                
                // Only use ONE font name for faster resolution
                FontFamily(Font(DeviceFontFamilyName("google-sans-flex"), variationSettings = variationSettings))
            } catch (e: Exception) {
                FontFamily.SansSerif
            }
        } else if (useNType) NTypeFontFamily else NDot55FontFamily

        val headlineFont = if (useGoogleSans) googleSansFlex else if (useNType) NTypeFontFamily else NDotFontFamily
        val bodyFont = if (useGoogleSans) googleSansFlex else FontFamily.SansSerif

        // Optimization: Pre-format the string to avoid multiple allocations in TextStyle
        val axisString = if (useGoogleSans) {
            "'wght' $gSansWeight, 'wdth' $gSansWidth, 'slnt' $gSansSlant, 'opsz' $gSansOpsz, 'GRAD' $gSansGrade, 'ROND' $gSansRounding, 'SOFT' $gSansRounding, 'BNHV' $gSansRounding, 'ROUN' $gSansRounding, 'RNDS' $gSansRounding"
        } else ""

        Typography(
            displayLarge = TextStyle(fontFamily = googleSansFlex, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            displayMedium = TextStyle(fontFamily = googleSansFlex, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            displaySmall = TextStyle(fontFamily = googleSansFlex, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            headlineLarge = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            headlineMedium = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            headlineSmall = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            titleLarge = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            titleMedium = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            titleSmall = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontFeatureSettings = axisString),
            bodyLarge = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            bodyMedium = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            bodySmall = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Normal, fontFeatureSettings = axisString),
            labelLarge = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontFeatureSettings = axisString),
            labelMedium = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontFeatureSettings = axisString),
            labelSmall = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontFeatureSettings = axisString)
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
