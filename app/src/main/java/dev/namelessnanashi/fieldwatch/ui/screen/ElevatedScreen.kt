package dev.namelessnanashi.fieldwatch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.namelessnanashi.fieldwatch.R
import dev.namelessnanashi.fieldwatch.domain.AppSettings
import dev.namelessnanashi.fieldwatch.domain.LanguageText
import dev.namelessnanashi.fieldwatch.elevated.DeviceAdmin
import dev.namelessnanashi.fieldwatch.elevated.ElevatedItem
import dev.namelessnanashi.fieldwatch.elevated.ElevatedItems
import dev.namelessnanashi.fieldwatch.elevated.ShizukuShell
import dev.namelessnanashi.fieldwatch.elevated.VectorBridge
import dev.namelessnanashi.fieldwatch.elevated.xposed.XposedTargets
import dev.namelessnanashi.fieldwatch.ui.ElevatedBackend
import dev.namelessnanashi.fieldwatch.ui.FieldwatchViewModel
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchActionButton
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchFilterChip
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchSwitch
import dev.namelessnanashi.fieldwatch.ui.component.SectionCard
import dev.namelessnanashi.fieldwatch.ui.theme.OxProto

/** A change waiting for the user's confirmation. */
private sealed interface Pending {
    data class Item(val item: ElevatedItem, val undo: Boolean) : Pending
    data object AdminOff : Pending
}

/**
 * Settings → Elevated access. Every change is listed on its own with its exact command, and
 * runs only after the user confirms it. Nothing here runs by itself.
 */
@Composable
fun ElevatedPage(vm: FieldwatchViewModel, settings: AppSettings) {
    val ui by vm.elevated.collectAsStateWithLifecycle()
    val vectorService by VectorBridge.connected.collectAsState()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pending?>(null) }
    // Re-read on return from the device admin screen, Shizuku, Vector, or the root manager.
    LifecycleResumeEffect(vectorService) {
        vm.refreshElevated()
        onPauseOrDispose { }
    }
    val status = ui.status
    val busy = ui.busy
    val shizukuReady = status?.shizuku == ShizukuShell.State.READY
    val rootReady = ui.rootAvailable == true
    fun canRun(item: ElevatedItem) = !busy && if (item.rootOnly || ui.backend == ElevatedBackend.ROOT) {
        rootReady
    } else {
        shizukuReady
    }

    SectionCard(LanguageText.text("settings.topic.elevated"), showTitle = false) {
        Muted(LanguageText.text("elevated.about"))
    }

    SectionCard(LanguageText.text("elevated.access")) {
        LabelValue(
            LanguageText.text("elevated.shizuku"),
            when (status?.shizuku) {
                ShizukuShell.State.NOT_INSTALLED -> LanguageText.text("elevated.shizuku.notinstalled")
                ShizukuShell.State.NOT_RUNNING -> LanguageText.text("elevated.shizuku.notrunning")
                ShizukuShell.State.NO_PERMISSION -> LanguageText.text("elevated.shizuku.nopermission")
                ShizukuShell.State.READY -> LanguageText.text("elevated.shizuku.ready")
                null -> LanguageText.text("elevated.value.unknown")
            },
        )
        if (status?.shizuku == ShizukuShell.State.NO_PERMISSION) {
            Action(LanguageText.text("elevated.shizuku.allow"), !busy, vm::requestShizukuPermission)
        }
        LabelValue(
            LanguageText.text("elevated.root"),
            when (ui.rootAvailable) {
                true -> LanguageText.text("elevated.root.ok")
                false -> LanguageText.text("elevated.root.no")
                null -> LanguageText.text("elevated.root.unknown")
            },
        )
        Action(LanguageText.text("elevated.root.check"), !busy, vm::checkRoot)
        Text(LanguageText.text("elevated.backend"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldwatchFilterChip(
                selected = ui.backend == ElevatedBackend.SHIZUKU,
                onClick = { vm.setElevatedBackend(ElevatedBackend.SHIZUKU) },
                label = { Text(LanguageText.text("elevated.shizuku")) },
            )
            FieldwatchFilterChip(
                selected = ui.backend == ElevatedBackend.ROOT,
                onClick = { vm.setElevatedBackend(ElevatedBackend.ROOT) },
                label = { Text(LanguageText.text("elevated.root")) },
            )
        }
        Muted(LanguageText.text("elevated.backend.about"))
    }

    val allItems = vm.elevatedItems()
    allItems.filterNot { it.id in ElevatedItems.VECTOR_CARD_ITEMS }.forEach { item ->
        val done = status?.items?.get(item.id)
        SectionCard(item.title) {
            Value(
                when (done) {
                    true -> LanguageText.text("elevated.item.done")
                    false -> LanguageText.text("elevated.item.notdone")
                    null -> LanguageText.text("elevated.value.unknown")
                },
            )
            Muted(item.about)
            if (item.rootOnly) Muted(LanguageText.text("elevated.item.rootonly"))
            if (item.id == ElevatedItems.HCI_SNOOP && done == null) Muted(LanguageText.text("elevated.snoop.hidden"))
            Command(item.apply)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchActionButton(
                    onClick = { pending = Pending.Item(item, undo = false) },
                    enabled = canRun(item),
                    modifier = Modifier.weight(1f),
                ) { Text(LanguageText.text("elevated.item.apply")) }
                if (item.undo != null) {
                    FieldwatchActionButton(
                        onClick = { pending = Pending.Item(item, undo = true) },
                        enabled = canRun(item),
                        modifier = Modifier.weight(1f),
                    ) { Text(LanguageText.text("elevated.item.undo")) }
                }
            }
            if (item.id == ElevatedItems.SCAN_THROTTLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LanguageText.text("elevated.keep"), Modifier.weight(1f))
                    FieldwatchSwitch(
                        settings.keepScanThrottleOff,
                        { on -> vm.updateSettings { it.copy(keepScanThrottleOff = on) } },
                    )
                }
                Muted(LanguageText.text("elevated.keep.about"))
            }
        }
    }

    SectionCard(LanguageText.text("elevated.admin")) {
        val active = status?.adminActive == true
        LabelValue(
            LanguageText.text("elevated.admin"),
            LanguageText.text(if (active) "elevated.admin.active" else "elevated.admin.inactive"),
        )
        LabelValue(LanguageText.text("elevated.row.bucket"), status?.standbyBucketName ?: "")
        Muted(LanguageText.text("elevated.admin.about"))
        if (!active) {
            // Android shows its own confirmation screen before anything changes.
            Action(LanguageText.text("elevated.admin.on"), !busy) {
                runCatching {
                    context.startActivity(
                        DeviceAdmin.activationIntent(context, context.getString(R.string.device_admin_explanation)),
                    )
                }
            }
        } else {
            Action(LanguageText.text("elevated.admin.off"), !busy) { pending = Pending.AdminOff }
        }
    }

    SectionCard(LanguageText.text("elevated.xposed")) {
        val vector = status?.vector
        LabelValue(
            LanguageText.text("elevated.xposed"),
            if (vector == null) {
                LanguageText.text("elevated.xposed.inactive")
            } else {
                "${vector.framework} ${vector.version}"
            },
        )
        Muted(LanguageText.text("elevated.xposed.about"))
        if (vector != null) {
            LabelValue(
                LanguageText.text("elevated.xposed.scope"),
                LanguageText.text(if (vector.bluetoothInScope) "elevated.value.yes" else "elevated.value.no"),
            )
            if (!vector.bluetoothInScope) {
                // Vector shows its own approval prompt.
                Action(LanguageText.text("elevated.xposed.requestscope"), !busy, vm::requestVectorScope)
            }
            HookSwitch(vm, XposedTargets.HOOK_TOO_FREQUENT, "elevated.hook.frequent", busy)
            HookSwitch(vm, XposedTargets.HOOK_TOO_LONG, "elevated.hook.long", busy)
            HookSwitch(vm, XposedTargets.HOOK_SCREEN_OFF, "elevated.hook.screenoff", busy)
            Muted(LanguageText.text("elevated.xposed.restart"))
            allItems.firstOrNull { it.id == ElevatedItems.RESTART_BLUETOOTH }?.let { restart ->
                Action(restart.title, canRun(restart)) { pending = Pending.Item(restart, undo = false) }
                if (!shizukuReady && !rootReady) Muted(LanguageText.text("elevated.xposed.restart.needs"))
            }
        }
    }

    if (busy || ui.log.isNotEmpty()) {
        SectionCard(LanguageText.text("elevated.log")) {
            if (busy) Muted(LanguageText.text("elevated.busy"))
            ui.log.forEach { line -> Command(line) }
        }
    }

    pending?.let { p ->
        val via = LanguageText.text(if (ui.backend == ElevatedBackend.ROOT) "elevated.root" else "elevated.shizuku")
        val (title, body) = when (p) {
            is Pending.Item -> {
                val shell = if (p.item.rootOnly) LanguageText.text("elevated.root") else via
                val command = if (p.undo) p.item.undo.orEmpty() else p.item.apply
                LanguageText.text("elevated.confirm.title", mapOf("via" to shell)) to
                    "${if (p.undo) "Undo: " else ""}${p.item.title}\n\n$command"
            }
            Pending.AdminOff -> LanguageText.text("elevated.admin.off") to LanguageText.text("elevated.admin.about")
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = { Text(body, style = MaterialTheme.typography.bodySmall.copy(fontFamily = OxProto)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    when (p) {
                        is Pending.Item -> vm.runElevatedItem(p.item.id, p.undo)
                        Pending.AdminOff -> vm.deactivateDeviceAdmin()
                    }
                }) { Text(LanguageText.text("elevated.confirm.run")) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
}

@Composable
private fun HookSwitch(vm: FieldwatchViewModel, key: String, labelKey: String, busy: Boolean) {
    // Read on each draw; the switch itself is the user's explicit choice.
    val on = VectorBridge.hookEnabled(key)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(LanguageText.text(labelKey), Modifier.weight(1f))
        FieldwatchSwitch(on, { want -> vm.setVectorHook(key, want) }, enabled = !busy)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    // Each side gets up to half the row, so a long value wraps instead of crushing the label.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun Action(label: String, enabled: Boolean, onClick: () -> Unit) {
    FieldwatchActionButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Command(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = OxProto),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Muted(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
