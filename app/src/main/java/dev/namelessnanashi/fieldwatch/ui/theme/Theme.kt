package dev.namelessnanashi.fieldwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B7A48),
    onPrimary = Color.White,
    secondary = Color(0xFF9A6400),
    tertiary = Color(0xFF0277BD),
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF12171C),
    surface = Color.White,
    onSurface = Color(0xFF12171C),
    surfaceVariant = Color(0xFFE6EBEF),
    onSurfaceVariant = Color(0xFF3F4A55),
    outline = Color(0xFFC5CDD4),
    error = Color(0xFFB00020),
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
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
