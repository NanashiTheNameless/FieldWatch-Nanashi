package dev.namelessnanashi.fieldwatch.ui

import dev.namelessnanashi.fieldwatch.domain.LanguageText

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import dev.namelessnanashi.fieldwatch.ui.icons.Icons
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.BugReport
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Category
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.DeviceThermostat
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.DirectionsCar
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Face
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Flight
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Headphones
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Home
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.LocalPolice
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Hub
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.LocalHospital
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Lock
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.PhotoCamera
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.QuestionMark
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Router
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Sell
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Smartphone
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Storefront
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Videocam
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Watch
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Bluetooth
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Wifi
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.namelessnanashi.fieldwatch.domain.RadioKind
import dev.namelessnanashi.fieldwatch.domain.SignatureClass

object ClassGlyphs {
    val unmatched: ImageVector get() = Icons.Outlined.QuestionMark

    fun of(kind: SignatureClass?): ImageVector = when (kind) {
        null -> unmatched
        SignatureClass.FINDER -> Icons.Outlined.Sell
        SignatureClass.BEACON -> Icons.Outlined.WifiTethering
        SignatureClass.SIGNAGE -> Icons.Outlined.Storefront
        SignatureClass.WEARABLE -> Icons.Outlined.Watch
        SignatureClass.SURVEILLANCE -> Icons.Outlined.Videocam
        SignatureClass.DRONE -> Icons.Outlined.Flight
        SignatureClass.HACKING -> Icons.Outlined.BugReport
        SignatureClass.BODYWORN -> Icons.Outlined.Watch
        SignatureClass.LAW_ENFORCEMENT -> Icons.Outlined.LocalPolice
        SignatureClass.VEHICLE -> Icons.Outlined.DirectionsCar
        SignatureClass.GLASSES -> Icons.Outlined.Face
        SignatureClass.AUDIO -> Icons.Outlined.Headphones
        SignatureClass.CAMERA -> Icons.Outlined.PhotoCamera
        SignatureClass.THERMOSTAT -> Icons.Outlined.DeviceThermostat
        SignatureClass.LOCK -> Icons.Outlined.Lock
        SignatureClass.HEALTH -> Icons.Outlined.LocalHospital
        SignatureClass.HOME -> Icons.Outlined.Home
        SignatureClass.ISP -> Icons.Outlined.Router
        SignatureClass.MESH -> Icons.Outlined.Hub
        SignatureClass.PHONE -> Icons.Outlined.Smartphone
        SignatureClass.OTHER -> Icons.Outlined.Category
    }
}

@Composable
fun RadioClassBadge(
    classKind: SignatureClass?,
    accent: Color,
    compact: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.18f),
        modifier = Modifier.size(if (compact) 26.dp else 28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                ClassGlyphs.of(classKind),
                contentDescription = classKind?.label() ?: LanguageText.text("ui.unmatched.label"),
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                tint = accent,
            )
        }
    }
}

@Composable
fun RadioKindMark(
    kind: RadioKind,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    Icon(
        if (kind == RadioKind.WIFI) Icons.Outlined.Wifi else Icons.Outlined.Bluetooth,
        contentDescription = if (kind == RadioKind.WIFI) LanguageText.text("ui.wi.fi.access.point.2") else LanguageText.text("ui.ble.advertiser"),
        modifier = modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
