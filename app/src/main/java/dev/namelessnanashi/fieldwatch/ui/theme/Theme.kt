package dev.namelessnanashi.fieldwatch.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.namelessnanashi.fieldwatch.R

/** Default accent: red on black. */
val Accent = Color(0xFFE53935)
/** Checked switch / slider / chip fill - same hue as Accent, a little darker. */
val AccentActive = Color(0xFFC62828)
/** Healthy status only (header dot). Kept green so it never reads as an alert. */
val Healthy = Color(0xFF3DFF9A)
val Amber = Color(0xFFFFB020)
val SignalRed = Color(0xFFFF3D5A)
val Cyan = Color(0xFF4FC3F7)
/** OLED black: background and cards are pure black so unlit pixels stay off. */
val Night = Color(0xFF000000)
val Panel = Color(0xFF000000)
/** Text fields, chips, and other filled inputs: just off black. */
val Panel2 = Color(0xFF0D0606)
val Hairline = Color(0xFF2A1414)

/** Default scheme: OLED black, red chrome. Class and data colors keep their hues. */
private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0000),
    primaryContainer = Color(0xFF3A0E0E),
    onPrimaryContainer = Color(0xFFFF8A80),
    secondary = Amber,
    onSecondary = Color(0xFF2A1A00),
    tertiary = Cyan,
    background = Night,
    onBackground = Color(0xFFE8DCDC),
    surface = Panel,
    onSurface = Color(0xFFE8DCDC),
    surfaceVariant = Panel2,
    onSurfaceVariant = Color(0xFFB39A9A),
    surfaceTint = Night,
    surfaceDim = Night,
    surfaceBright = Color(0xFF140A0A),
    surfaceContainerLowest = Night,
    surfaceContainerLow = Night,
    surfaceContainer = Color(0xFF050202),
    surfaceContainerHigh = Color(0xFF0A0505),
    surfaceContainerHighest = Color(0xFF0F0808),
    outline = Color(0xFF4A2626),
    outlineVariant = Hairline,
    error = SignalRed,
)

/**
 * Light scheme: same red chrome on warm white. Primary is the darker [AccentActive] so red
 * text and controls keep enough contrast on white. Flat surfaces, like the dark scheme.
 */
private val LightColors = lightColorScheme(
    primary = AccentActive,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    tertiary = Color(0xFF0277BD),
    background = Color(0xFFFFFBFA),
    onBackground = Color(0xFF1F1515),
    surface = Color.White,
    onSurface = Color(0xFF1F1515),
    surfaceVariant = Color(0xFFF5E6E4),
    onSurfaceVariant = Color(0xFF5A4442),
    surfaceTint = Color.White,
    surfaceDim = Color(0xFFEDE0DE),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF8F7),
    surfaceContainer = Color(0xFFFCEFED),
    surfaceContainerHigh = Color(0xFFF7E9E7),
    surfaceContainerHighest = Color(0xFFF2E3E1),
    outline = Color(0xFFB59A97),
    outlineVariant = Color(0xFFEAD8D6),
    error = Color(0xFFBA1A1A),
)

/** 0xProto, the app-wide font. OFL-1.1, see assets/licenses/0xProto-OFL.txt. */
val OxProto = FontFamily(
    Font(R.font.oxproto_regular, FontWeight.Normal),
    Font(R.font.oxproto_bold, FontWeight.Bold),
    Font(R.font.oxproto_italic, FontWeight.Normal, FontStyle.Italic),
)

private val AppTypography: Typography = Typography().run {
    fun TextStyle.ox() = copy(fontFamily = OxProto)
    Typography(
        displayLarge = displayLarge.ox(),
        displayMedium = displayMedium.ox(),
        displaySmall = displaySmall.ox(),
        headlineLarge = headlineLarge.ox(),
        headlineMedium = headlineMedium.ox(),
        headlineSmall = headlineSmall.ox(),
        titleLarge = titleLarge.ox(),
        titleMedium = titleMedium.ox(),
        titleSmall = titleSmall.ox(),
        bodyLarge = bodyLarge.ox(),
        bodyMedium = bodyMedium.ox(),
        bodySmall = bodySmall.ox(),
        labelLarge = labelLarge.ox(),
        labelMedium = labelMedium.ox(),
        labelSmall = labelSmall.ox(),
    )
}

val Mono = TextStyle(
    fontFamily = OxProto,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 0.3.sp,
)

@Composable
fun FieldwatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Bars are transparent, so their icons must contrast with the app background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
