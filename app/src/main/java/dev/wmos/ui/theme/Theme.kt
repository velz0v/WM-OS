package dev.wmos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class WmExtraColors(
    val surfaceElevated: Color,
    val surfaceSunken: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val outlineSubtle: Color,
)

private val DarkExtras = WmExtraColors(
    surfaceElevated = Graphite700,
    surfaceSunken = Graphite900,
    success = Success,
    successContainer = SuccessSoft,
    onSuccessContainer = Success,
    warning = Warning,
    warningContainer = WarningSoft,
    onWarningContainer = Warning,
    danger = Danger,
    dangerContainer = DangerSoft,
    onDangerContainer = Danger,
    info = Info,
    infoContainer = InfoSoft,
    onInfoContainer = Info,
    accentSoft = LimeSoft,
    onAccent = OnLime,
    outlineSubtle = Graphite500,
)

private val LightExtras = WmExtraColors(
    surfaceElevated = PaperSurface,
    surfaceSunken = PaperSurfaceAlt,
    success = Color(0xFF1F7A4D),
    successContainer = Color(0xFFDDF3E7),
    onSuccessContainer = Color(0xFF0F4A2D),
    warning = Color(0xFF8A5B00),
    warningContainer = Color(0xFFFDF0D5),
    onWarningContainer = Color(0xFF5A3B00),
    danger = Color(0xFFB3261E),
    dangerContainer = Color(0xFFFBE3E1),
    onDangerContainer = Color(0xFF6E1713),
    info = Color(0xFF2559A8),
    infoContainer = Color(0xFFDEE9FB),
    onInfoContainer = Color(0xFF14315C),
    accentSoft = Color(0xFFE8F5D2),
    onAccent = OnLime,
    outlineSubtle = PaperOutline,
)

val LocalWmColors = staticCompositionLocalOf { DarkExtras }

private val DarkScheme = darkColorScheme(
    primary = Lime,
    onPrimary = OnLime,
    primaryContainer = LimeSoft,
    onPrimaryContainer = Lime,
    secondary = Ink300,
    onSecondary = Graphite900,
    secondaryContainer = Graphite700,
    onSecondaryContainer = Ink100,
    tertiary = Info,
    onTertiary = Graphite900,
    background = Graphite900,
    onBackground = Ink100,
    surface = Graphite800,
    onSurface = Ink100,
    surfaceVariant = Graphite700,
    onSurfaceVariant = Ink300,
    surfaceContainerLowest = Graphite900,
    surfaceContainerLow = Graphite850,
    surfaceContainer = Graphite800,
    surfaceContainerHigh = Graphite700,
    surfaceContainerHighest = Graphite600,
    outline = Graphite500,
    outlineVariant = Graphite600,
    error = Danger,
    onError = Graphite900,
    errorContainer = DangerSoft,
    onErrorContainer = Danger,
    inverseSurface = Ink100,
    inverseOnSurface = Graphite900,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = LimeOnLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5D2),
    onPrimaryContainer = Color(0xFF2B4706),
    secondary = PaperTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = PaperSurfaceAlt,
    onSecondaryContainer = PaperText,
    tertiary = Color(0xFF2559A8),
    onTertiary = Color.White,
    background = Paper,
    onBackground = PaperText,
    surface = PaperSurface,
    onSurface = PaperText,
    surfaceVariant = PaperSurfaceAlt,
    onSurfaceVariant = PaperTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBF7),
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceAlt,
    surfaceContainerHighest = Color(0xFFE4E7DD),
    outline = Color(0xFFB4B9AD),
    outlineVariant = PaperOutline,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE3E1),
    onErrorContainer = Color(0xFF6E1713),
    inverseSurface = PaperText,
    inverseOnSurface = Paper,
    scrim = Color.Black,
)

private val WmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extras = if (darkTheme) DarkExtras else LightExtras
    CompositionLocalProvider(LocalWmColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = WmTypography,
            shapes = WmShapes,
            content = content,
        )
    }
}
