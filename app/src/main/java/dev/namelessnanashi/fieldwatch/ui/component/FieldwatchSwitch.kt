package dev.namelessnanashi.fieldwatch.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import dev.namelessnanashi.fieldwatch.ui.theme.AccentActive

@Composable
fun FieldwatchSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = spectreTileFill()
    val edge = spectreTileEdge()
    val active = AccentActive
    val knob = scheme.onSurface
    val onActive = scheme.onPrimary
    Box(
        modifier = modifier
            .requiredSize(width = 40.dp, height = 24.dp)
            .clip(RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.scale(0.72f),
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = onActive,
            checkedTrackColor = active,
            checkedBorderColor = edge,
            uncheckedThumbColor = knob,
            uncheckedTrackColor = fill,
            uncheckedBorderColor = edge,
            disabledCheckedThumbColor = onActive.copy(alpha = 0.38f),
            disabledCheckedTrackColor = active.copy(alpha = 0.38f),
            disabledCheckedBorderColor = edge.copy(alpha = 0.4f),
            disabledUncheckedThumbColor = knob.copy(alpha = 0.38f),
            disabledUncheckedTrackColor = fill.copy(alpha = 0.4f),
            disabledUncheckedBorderColor = edge.copy(alpha = 0.4f),
        ),
    )
    }
}
