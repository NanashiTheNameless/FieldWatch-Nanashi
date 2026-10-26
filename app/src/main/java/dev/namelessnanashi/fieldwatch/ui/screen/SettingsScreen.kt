package dev.namelessnanashi.fieldwatch.ui.screen

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.namelessnanashi.fieldwatch.alert.TtsEngines
import dev.namelessnanashi.fieldwatch.alert.VoiceOption
import dev.namelessnanashi.fieldwatch.domain.AlertOtherAudio
import dev.namelessnanashi.fieldwatch.domain.AlertVoiceWhat
import dev.namelessnanashi.fieldwatch.domain.AppSettings
import dev.namelessnanashi.fieldwatch.domain.CatalogSource
import dev.namelessnanashi.fieldwatch.domain.GnssAlertFloor
import dev.namelessnanashi.fieldwatch.domain.GnssSensitivity
import dev.namelessnanashi.fieldwatch.domain.LanguageText
import dev.namelessnanashi.fieldwatch.domain.MapBasemap
import dev.namelessnanashi.fieldwatch.domain.MapFooter
import dev.namelessnanashi.fieldwatch.domain.MapKey
import dev.namelessnanashi.fieldwatch.domain.ScanIntensity
import dev.namelessnanashi.fieldwatch.domain.TakDefaults
import dev.namelessnanashi.fieldwatch.domain.TakFeedStatus
import dev.namelessnanashi.fieldwatch.domain.TakFloodSend
import dev.namelessnanashi.fieldwatch.domain.TakGnssHold
import dev.namelessnanashi.fieldwatch.domain.TakGnssSend
import dev.namelessnanashi.fieldwatch.domain.TakInstall
import dev.namelessnanashi.fieldwatch.domain.TakPublish
import dev.namelessnanashi.fieldwatch.domain.TakUdpPreset
import dev.namelessnanashi.fieldwatch.radio.ScanService
import dev.namelessnanashi.fieldwatch.radio.WifiRadio
import dev.namelessnanashi.fieldwatch.ui.FieldwatchUi
import dev.namelessnanashi.fieldwatch.ui.FieldwatchViewModel
import dev.namelessnanashi.fieldwatch.ui.NestedTabInsets
import dev.namelessnanashi.fieldwatch.ui.NestedTopBar
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchActionButton
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchDropdownField
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchFilterChip
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchOutlinedField
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchSlider
import dev.namelessnanashi.fieldwatch.ui.component.FieldwatchSwitch
import dev.namelessnanashi.fieldwatch.ui.component.SectionCard
import dev.namelessnanashi.fieldwatch.ui.component.StableCaption
import dev.namelessnanashi.fieldwatch.ui.component.StickyHeight
import dev.namelessnanashi.fieldwatch.ui.icons.Icons
import dev.namelessnanashi.fieldwatch.ui.icons.automirrored.filled.ArrowBack
import dev.namelessnanashi.fieldwatch.ui.icons.automirrored.filled.KeyboardArrowRight
import dev.namelessnanashi.fieldwatch.ui.icons.outlined.Check
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onRadioBookmarks: () -> Unit,
    onScanStatus: () -> Unit,
    onShowLiveTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = state.settings
    val saveSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSignaturesToUri) }
    val importSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSignaturesFromUri) }
    val saveSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSettingsToUri) }
    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSettingsFromUri) }
    var confirmRestore by remember { mutableStateOf(false) }
    val pendingSettingsImport by vm.pendingSettingsImport.collectAsStateWithLifecycle()
    val gnssProfile by vm.gnssProfile.collectAsStateWithLifecycle()
    val gnssCal by vm.gnssCalibration.collectAsStateWithLifecycle()
    val gnssRecordings by vm.gnssRecordings.collectAsStateWithLifecycle()
    val gnssReady = gnssProfile != null
    var topicName by rememberSaveable { mutableStateOf<String?>(null) }
    var mapMenu by rememberSaveable { mutableStateOf(false) }
    var positionMenu by rememberSaveable { mutableStateOf(false) }
    val topic = topicName?.let { name -> SettingsTopic.entries.firstOrNull { it.name == name } }
    BackHandler(enabled = topic != null) { topicName = null }
    val scroll = rememberScrollState()
    LaunchedEffect(topicName) { scroll.scrollTo(0) }
    LaunchedEffect(settings.onlineLookup) {
        if (!settings.onlineLookup) mapMenu = false
    }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = topic?.title() ?: LanguageText.text("tab.settings"),
                navigationIcon = {
                    if (topic != null) {
                        IconButton(onClick = { topicName = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, LanguageText.text("chrome.back"))
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (topic == null) {
                SettingsTopicList(
                    settings = settings,
                    catalogVersion = state.catalogVersion,
                    takInstallTag = state.takInstallTag,
                    gnssReady = gnssReady,
                    namedRadioCount = state.watchlist.count { it.deviceKey != null },
                    onOpen = { topicName = it.name },
                    onNamedRadios = onRadioBookmarks,
                )
            }
            if (topic == SettingsTopic.APPEARANCE) {
            SectionCard(LanguageText.text("settings.topic.appearance"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.keep.screen"), Modifier.weight(1f))
                FieldwatchSwitch(settings.keepScreenOn, { on -> vm.updateSettings { it.copy(keepScreenOn = on) } })
            }
            Text(
                LanguageText.text("ui.screen.stays.awake.while"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.privacy.mode"), Modifier.weight(1f))
                FieldwatchSwitch(settings.demoMode, { on -> vm.updateSettings { it.copy(demoMode = on) } })
            }
            Text(
                LanguageText.text("ui.hides.last.half.each"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            }

            if (topic == SettingsTopic.SCANNING) {
            SectionCard(LanguageText.text("settings.topic.scanning"), showTitle = false) {
            val label = when (settings.intensity) {
                ScanIntensity.SAVER -> LanguageText.text("ui.battery.saver")
                ScanIntensity.BALANCED -> LanguageText.text("ui.balanced.label")
                ScanIntensity.PERFORMANCE -> LanguageText.text("ui.high.performance")
            }
            Text("Scan intensity  ·  $label")
            FieldwatchSlider(
                value = settings.intensity.ordinal.toFloat(),
                onValueChange = { v ->
                    val next = ScanIntensity.entries[v.toInt().coerceIn(0, 2)]
                    vm.updateSettings { it.copy(intensity = next) }
                },
                valueRange = 0f..2f,
                steps = 1,
            )
            Text(
                LanguageText.text("ui.wi.fi.reads.every"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("settings.classic.title"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.classicDiscovery,
                    { on -> vm.updateSettings { it.copy(classicDiscovery = on) } },
                )
            }
            Text(
                LanguageText.text("settings.classic.about"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HCI snoop-log reader (root)", Modifier.weight(1f))
                FieldwatchSwitch(settings.hciSnoopReader, { on ->
                    vm.updateSettings { it.copy(hciSnoopReader = on) }
                })
            }
            Text(
                "Off by default. First enable Bluetooth HCI snoop log in Elevated access. " +
                    "Reads new raw advertisements and Classic inquiry results into the Bluetooth list. " +
                    "May prompt for root. The reader does not turn logging on or off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val snoopStatus by dev.namelessnanashi.fieldwatch.radio.HciSnoopReader.status.collectAsStateWithLifecycle()
            if (settings.hciSnoopReader) Text(snoopStatus, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continue scanning in background", Modifier.weight(1f))
                FieldwatchSwitch(settings.backgroundScanning, { on ->
                    vm.updateSettings { it.copy(backgroundScanning = on) }
                })
            }
            Text(
                "Keeps the scan notification and radios running after dismissing the app from Recents. " +
                    "Use Stop in the notification to stop scanning. Android may restart the service after reclaiming memory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start scanning after boot", Modifier.weight(1f))
                FieldwatchSwitch(settings.scanOnBoot, { on ->
                    vm.updateSettings { it.copy(scanOnBoot = on) }
                })
            }
            Text(
                "Starts after the first unlock and after app updates. No device owner needed. " +
                    "In Elevated access, grant radio permissions and Location permission for boot scanning using Shizuku or root. " +
                    "Allow background running and exempt battery optimization there too if needed. " +
                    "Alternatively, select Allow all the time in Android's location permissions for this app. " +
                    "Shizuku does not need to stay running after granting permissions. Force stop blocks boot starts until you open the app again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("settings.statusicon.title"), Modifier.weight(1f))
                FieldwatchActionButton(onClick = { openScanChannelSettings(context) }) {
                    Text(LanguageText.text("settings.statusicon.open"))
                }
            }
            Text(
                LanguageText.text("settings.statusicon.about"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StableCaption(
                state.throttleHint.ifBlank { " " },
                LanguageText.text("ui.wi.fi.waiting.os"),
                LanguageText.text("ui.wi.fi.scanning"),
                LanguageText.text("ui.wi.fi.next.99s"),
                " ",
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            var osThrottled by remember { mutableStateOf(WifiRadio.osScanThrottled(context)) }
            var backgroundAllowed by remember { mutableStateOf(isBackgroundUsageAllowed(context)) }
            var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
            var needDevOptions by remember { mutableStateOf(false) }
            var batteryGate by remember { mutableStateOf<BatteryAndroidGate?>(null) }
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        osThrottled = WifiRadio.osScanThrottled(context)
                        backgroundAllowed = isBackgroundUsageAllowed(context)
                        unrestricted = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.faster.wi.fi.ap"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = !osThrottled,
                    onCheckedChange = { needDevOptions = true },
                )
            }
            StableCaption(
                when {
                    Build.VERSION.SDK_INT < 30 ->
                        LanguageText.text("ui.phone.older.than.android")
                    !osThrottled ->
                        LanguageText.text("ui.new.access.point.list")
                    else ->
                        LanguageText.text("ui.off.android.limiting.wi")
                },
                LanguageText.text("ui.phone.older.than.android"),
                LanguageText.text("ui.new.access.point.list"),
                LanguageText.text("ui.off.android.limiting.wi"),
            )
            if (needDevOptions) {
                AlertDialog(
                    onDismissRequest = { needDevOptions = false },
                    title = { Text(LanguageText.text("ui.faster.wi.fi.ap")) },
                    text = {
                        Text(
                            if (Build.VERSION.SDK_INT < 30) {
                                LanguageText.text("ui.phone.older.than.android.2")
                            } else if (osThrottled) {
                                LanguageText.text("ui.opens.developer.options.turn")
                            } else {
                                LanguageText.text("ui.opens.developer.options.turn.2")
                            },
                        )
                    },
                    confirmButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(
                                onClick = {
                                    needDevOptions = false
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    }
                                },
                            ) { Text(LanguageText.text("ui.open.developer.options")) }
                        } else {
                            TextButton(onClick = { needDevOptions = false }) { Text(LanguageText.text("ui.ok.label")) }
                        }
                    },
                    dismissButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(onClick = { needDevOptions = false }) { Text(LanguageText.text("ui.not.now")) }
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.allow.background.usage"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = backgroundAllowed,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.BACKGROUND },
                )
            }
            Text(
                LanguageText.text("ui.opens.app.s.battery"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.unrestricted.battery"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = unrestricted,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.UNRESTRICTED },
                )
            }
            Text(
                LanguageText.text("ui.opens.same.battery.page"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (batteryGate != null) {
                val background = batteryGate == BatteryAndroidGate.BACKGROUND
                AlertDialog(
                    onDismissRequest = { batteryGate = null },
                    title = {
                        Text(if (background) LanguageText.text("ui.allow.background.usage") else LanguageText.text("ui.unrestricted.battery"))
                    },
                    text = {
                        Text(
                            if (background) {
                                LanguageText.text("ui.opens.fieldwatch.s.battery")
                            } else {
                                LanguageText.text("ui.choose.unrestricted.some.phones")
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val gate = batteryGate
                                batteryGate = null
                                openAppBatteryPage(
                                    context,
                                    highlightBackground = gate == BatteryAndroidGate.BACKGROUND,
                                )
                            },
                        ) { Text(LanguageText.text("ui.open.android.settings")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { batteryGate = null }) { Text(LanguageText.text("ui.not.now")) }
                    },
                )
            }
            }
            }

            if (topic == SettingsTopic.WATCHLIST) {
            SectionCard(LanguageText.text("settings.topic.watchlist"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.watchlist.alerts"), Modifier.weight(1f))
                FieldwatchSwitch(settings.alertsEnabled, { on -> vm.updateSettings { it.copy(alertsEnabled = on) } })
            }
            Text(
                LanguageText.text("ui.beep.voice.flash.jump"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.beep.watched.signature"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertBeep,
                    { on -> vm.updateSettings { it.copy(alertBeep = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                LanguageText.text("ui.double.pip.media.volume"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AudioWakeupControl(
                valueMs = settings.alertLeadInMs,
                enabled = settings.alertsEnabled,
                onCommit = { ms -> vm.updateSettings { it.copy(alertLeadInMs = ms) } },
            )
            Text(
                LanguageText.text("settings.leadin.about"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(LanguageText.text("settings.otheraudio.title"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertOtherAudio.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.alertOtherAudio == item,
                        onClick = { vm.updateSettings { it.copy(alertOtherAudio = item) } },
                        enabled = (settings.alertsEnabled && (settings.alertBeep || settings.alertVoice)) || settings.gnssBeep || settings.gnssVoice,
                        label = { Text(item.label()) },
                    )
                }
            }
            Text(
                LanguageText.text("settings.otheraudio.about"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AlertSoundPicker(
                settings = settings,
                enabled = settings.alertsEnabled,
                onBuiltIn = vm::useBuiltInAlertSound,
                onPhoneSound = vm::usePhoneAlertSound,
                onFile = vm::importAlertSound,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.voice.watched.signature"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertVoice,
                    { on -> vm.updateSettings { it.copy(alertVoice = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                LanguageText.text("ui.says.class.signature.same"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TtsEnginePicker(
                selected = settings.ttsEngine,
                enabled = settings.alertsEnabled && (settings.alertVoice || settings.gnssVoice),
                onPick = { pkg -> vm.updateSettings { it.copy(ttsEngine = pkg, ttsVoice = "") } },
            )
            TtsVoicePicker(
                vm = vm,
                selected = settings.ttsVoice,
                enabled = settings.alertsEnabled && (settings.alertVoice || settings.gnssVoice),
                onPick = { name -> vm.updateSettings { it.copy(ttsVoice = name) } },
            )
            Text(LanguageText.text("ui.what.say"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertVoiceWhat.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.alertVoiceWhat == item,
                        onClick = { vm.updateSettings { it.copy(alertVoiceWhat = item) } },
                        enabled = settings.alertsEnabled && settings.alertVoice,
                        label = { Text(item.label()) },
                    )
                }
            }
            Text(
                LanguageText.text("ui.class.group.live.such"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::testWatchBeep,
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
            ) { Text(LanguageText.text("ui.test.alert")) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.jump.new.watched.detection"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.snapToBeep,
                    { on -> vm.updateSettings { it.copy(snapToBeep = on) } },
                    enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
                )
            }
            Text(
                LanguageText.text("ui.live.scrolls.new.watched"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.system.notification"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertShade,
                    { on -> vm.updateSettings { it.copy(alertShade = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                LanguageText.text("ui.silent.notification.when.watched"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            }

            if (topic == SettingsTopic.LOCATION) {
            SectionCard(LanguageText.text("settings.topic.location"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.tag.detections.gps"), Modifier.weight(1f))
                FieldwatchSwitch(settings.tagLocation, { on -> vm.updateSettings { it.copy(tagLocation = on) } })
            }
            Text(
                (LanguageText.text("ui.stamps.phone.s.position") + " ") +
                    (LanguageText.text("ui.where.were.not.location") + " ") +
                    (LanguageText.text("ui.use.high.accuracy.location") + " ") +
                    (LanguageText.text("ui.heard.here.tak.pins") + " ") +
                    LanguageText.text("ui.turn.off.keep.position"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.places.label"), Modifier.weight(1f))
                FieldwatchSwitch(settings.placeWatch, { on -> vm.updateSettings { it.copy(placeWatch = on) } })
            }
            Text(
                (LanguageText.text("ui.finder.tag.keeps.its") + " ") +
                    (LanguageText.text("ui.each.line.where.phone") + " ") +
                    (LanguageText.text("ui.return.after.quiet.stretch") + " ") +
                    (LanguageText.text("ui.bluetooth.radio.changes.its") + " ") +
                    LanguageText.text("ui.needs.tag.detections.gps"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.online.place.names.maps"), Modifier.weight(1f))
                FieldwatchSwitch(settings.onlineLookup, { on -> vm.updateSettings { it.copy(onlineLookup = on) } })
            }
            Text(
                (LanguageText.text("ui.internet.debrief.places.turn") + " ") +
                    (LanguageText.text("ui.nothing.sent.fieldwatch.server") + " ") +
                    (LanguageText.text("ui.offline.debrief.keeps.coordinates") + " ") +
                    LanguageText.text("ui.turn.off.leave.streets"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val mapsOn = settings.onlineLookup
            val open = mapMenu && mapsOn
            ExposedDropdownMenuBox(
                expanded = open,
                onExpandedChange = { want ->
                    if (mapsOn) {
                        mapMenu = want
                        if (want) positionMenu = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                FieldwatchDropdownField(
                    label = LanguageText.text("settings.map.choice"),
                    value = LanguageText.text(settings.mapBasemap.labelKey),
                    expanded = open,
                    enabled = mapsOn,
                )
                ExposedDropdownMenu(open, { mapMenu = false }) {
                    MapBasemap.entries.forEach { choice ->
                        val selected = choice == settings.mapBasemap
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .clickable {
                                    mapMenu = false
                                    if (choice != settings.mapBasemap) vm.selectMap(choice)
                                }
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                LanguageText.text(choice.labelKey),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (choice.needsKey) {
                                Text(
                                    LanguageText.text("map.key.mark"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (selected) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
            if (mapsOn && settings.mapBasemap.needsKey) {
                val service = settings.mapBasemap.service
                val stored = vm.mapKey(settings.mapBasemap)
                var draft by remember(service) { mutableStateOf(stored) }
                var skipFirstBlur by remember(service) { mutableStateOf(true) }
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = { next ->
                        if (MapKey.verdict(next) == MapKey.Verdict.DENIED) {
                            vm.finishMapKey(settings.mapBasemap, next)
                        } else {
                            draft = next
                        }
                    },
                    label = LanguageText.text("map.key.label"),
                    supportingText = LanguageText.text("map.key.hint"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { vm.finishMapKey(settings.mapBasemap, draft) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (focus.isFocused) {
                                skipFirstBlur = false
                            } else if (skipFirstBlur) {
                                skipFirstBlur = false
                            } else {
                                vm.finishMapKey(settings.mapBasemap, draft)
                            }
                        },
                )
            }
            Text(
                LanguageText.text("settings.map.about"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!mapsOn) {
                Text(
                    LanguageText.text("settings.map.needs.online"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = positionMenu,
                onExpandedChange = { want ->
                    positionMenu = want
                    if (want) mapMenu = false
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                FieldwatchDropdownField(
                    label = LanguageText.text("settings.map.position"),
                    value = LanguageText.text(settings.mapFooter.labelKey),
                    expanded = positionMenu,
                )
                ExposedDropdownMenu(positionMenu, { positionMenu = false }) {
                    MapFooter.entries.forEach { choice ->
                        val selected = choice == settings.mapFooter
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .clickable {
                                    positionMenu = false
                                    if (choice != settings.mapFooter) {
                                        vm.updateSettings { it.copy(mapFooter = choice) }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                LanguageText.text(choice.labelKey),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
            Text(
                LanguageText.text("settings.map.position.about"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            }

            if (topic == SettingsTopic.GNSS) {
            SectionCard(LanguageText.text("settings.topic.gnss"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.gnss.interference.check"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.gnssMonitor && gnssReady,
                    onCheckedChange = { on ->
                        if (gnssReady) vm.updateSettings { it.copy(gnssMonitor = on) }
                    },
                    enabled = gnssReady && !gnssCal.running,
                )
            }
            Text(
                (LanguageText.text("ui.detects.interference.spoofing.gnss") + " ") +
                    (LanguageText.text("ui.interference.can.also.caused") + " ") +
                    (LanguageText.text("ui.fieldwatch.cannot.tell.source") + " ") +
                    (LanguageText.text("ui.hit.already.under.way") + " ") +
                    LanguageText.text("ui.hit.opens.popup.after"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!gnssReady) {
                Text(
                    LanguageText.text("ui.calibrate.phone.before.turning"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FieldwatchActionButton(
                onClick = { if (gnssCal.running) vm.stopGnssCalibration() else vm.startGnssCalibration() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (gnssCal.running) LanguageText.text("ui.stop.calibration") else LanguageText.text("ui.calibrate.phone")) }
            Text(
                (LanguageText.text("ui.stand.outside.hold.still") + " ") +
                    LanguageText.text("ui.result.stays.phone.until"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (gnssCal.running) {
                Text(
                    "Calibrating… ${gnssCal.leftSec} s left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (gnssCal.rejection.isNotBlank()) {
                Text(
                    gnssCal.rejection,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            gnssProfile?.let { profile ->
                Text(
                    profile.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Calibrated ${profile.calibratedLabel()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(LanguageText.text("ui.sensitivity.label"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GnssSensitivity.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.gnssSensitivity == item,
                        onClick = { vm.updateSettings { it.copy(gnssSensitivity = item) } },
                        label = { Text(item.label()) },
                    )
                }
            }
            Text(
                LanguageText.text("ui.low.waits.deeper.drop"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(LanguageText.text("ui.alert.label.2"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GnssAlertFloor.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.gnssAlertFloor == item,
                        onClick = { vm.updateSettings { it.copy(gnssAlertFloor = item) } },
                        label = { Text(item.label()) },
                    )
                }
            }
            Text(
                LanguageText.text("ui.popup.red.line.beep"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.spoofing.checks"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssSpoofChecks, { on -> vm.updateSettings { it.copy(gnssSpoofChecks = on) } })
            }
            Text(
                LanguageText.text("ui.warns.when.gain.falls"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.beep.label"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssBeep, { on -> vm.updateSettings { it.copy(gnssBeep = on) } })
            }
            Text(
                LanguageText.text("ui.same.pip.watchlist.hit"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.voice.label"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssVoice, { on -> vm.updateSettings { it.copy(gnssVoice = on) } })
            }
            Text(
                LanguageText.text("ui.says.possible.gps.interference"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.system.notification"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssShade, { on -> vm.updateSettings { it.copy(gnssShade = on) } })
            }
            Text(
                LanguageText.text("ui.silent.notification.warning.popup"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.record.raw.gnss.during"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.gnssRawLog,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(gnssRawLog = on) } },
                    enabled = gnssReady && settings.gnssMonitor && !settings.demoMode && !gnssCal.running,
                )
            }
            Text(
                (LanguageText.text("ui.saves.phone.s.raw") + " ") +
                    (LanguageText.text("ui.file.stays.phone.until") + " ") +
                    LanguageText.text("ui.privacy.mode.does.not"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = { gnssRecordings.firstOrNull()?.let { vm.shareGnssRecording(it.name) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = gnssRecordings.isNotEmpty(),
            ) { Text(LanguageText.text("ui.share.last.recording")) }
            if (gnssRecordings.size > 1) {
                gnssRecordings.drop(1).forEach { recording ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            recording.label(),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { vm.shareGnssRecording(recording.name) }) { Text(LanguageText.text("ui.share.label")) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.full.tracking"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssFullTracking, { on -> vm.updateSettings { it.copy(gnssFullTracking = on) } })
            }
            Text(
                LanguageText.text("ui.keeps.gps.receiver.awake"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.fading.together.check"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssCorrelation, { on -> vm.updateSettings { it.copy(gnssCorrelation = on) } })
            }
            Text(
                LanguageText.text("ui.watches.satellites.fading.same"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.invert.gain"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssInvertAgc, { on -> vm.updateSettings { it.copy(gnssInvertAgc = on) } })
            }
            Text(
                LanguageText.text("ui.few.phones.report.gain"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            }

            if (topic == SettingsTopic.TAK) {
            SectionCard(LanguageText.text("settings.topic.tak"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.tak.cot.feed"), Modifier.weight(1f))
                FieldwatchSwitch(settings.takEnabled, { on -> vm.updateSettings { it.copy(takEnabled = on) } })
            }
            Text(
                (LanguageText.text("ui.sends.map.markers.atak") + " ") +
                    (LanguageText.text("ui.radios.hear.pinned.phone") + " ") +
                    (LanguageText.text("ui.walking.away.does.not") + " ") +
                    (LanguageText.text("ui.remote.id.aircraft.use") + " ") +
                    (LanguageText.text("ui.radio.leaves.keeps.its") + " ") +
                    LanguageText.text("ui.not.direction.finding.privacy"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.takEnabled && settings.demoMode) {
                Text(
                    LanguageText.text("ui.privacy.mode.so.feed"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (settings.takEnabled) {
                TakFeedSettings(settings, vm, state.takStatus, state.takInstallTag)
            }
            }
            }

            if (topic == SettingsTopic.LOGGING) {
            SectionCard(LanguageText.text("settings.topic.logging"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.write.detections.disk"), Modifier.weight(1f))
                FieldwatchSwitch(settings.loggingEnabled, { on -> vm.updateSettings { it.copy(loggingEnabled = on) } })
            }
            StableCaption(
                if (settings.loggingEnabled) {
                    LanguageText.text("ui.logging.new.detections.added")
                } else {
                    LanguageText.text("ui.logging.off.scanning.still")
                },
                LanguageText.text("ui.logging.new.detections.added"),
                LanguageText.text("ui.logging.off.scanning.still"),
            )
            Text(
                LanguageText.text("ui.file.disk.one.detection"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var rotateDrag by remember { mutableIntStateOf(settings.logRotateKb) }
            var rotateDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.logRotateKb) {
                if (!rotateDragging) rotateDrag = settings.logRotateKb
            }
            Text("Rotate at $rotateDrag KB")
            FieldwatchSlider(
                value = rotateDrag.toFloat(),
                onValueChange = {
                    rotateDragging = true
                    rotateDrag = it.toInt().coerceIn(128, 4096)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(logRotateKb = rotateDrag) }
                    rotateDragging = false
                },
                valueRange = 128f..4096f,
            )
            Text(
                LanguageText.text("ui.when.log.file.reaches"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var staleDrag by remember { mutableIntStateOf(settings.staleSec) }
            var staleDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.staleSec) {
                if (!staleDragging) staleDrag = settings.staleSec
            }
            Text("Stale after ${staleDrag}s")
            FieldwatchSlider(
                value = staleDrag.toFloat(),
                onValueChange = {
                    staleDragging = true
                    staleDrag = it.toInt().coerceIn(15, 180)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(staleSec = staleDrag) }
                    staleDragging = false
                },
                valueRange = 15f..180f,
            )
            Text(
                LanguageText.text("ui.radio.marked.gone.live"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StickyHeight("log-stats") {
                Text(
                    "${state.logLines} lines this session  ·  ${vm.logBytes() / 1024} KB on disk. " +
                        LanguageText.text("ui.share.save.reset.clear"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            }
            }

            if (topic == SettingsTopic.SIGNATURES) {
            SectionCard(LanguageText.text("settings.topic.signatures"), showTitle = false) {
            Text(
                LanguageText.text("ui.export.shares.catalog.including"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSignatureShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.export.signatures")) }
            FieldwatchActionButton(
                onClick = { saveSignatures.launch(vm.suggestedSignaturesName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.save.signatures.sd.card")) }
            FieldwatchActionButton(
                onClick = {
                    importSignatures.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.import.signatures")) }
            var sourceMenu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = sourceMenu,
                onExpandedChange = { sourceMenu = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                FieldwatchDropdownField(
                    label = LanguageText.text("settings.catalog.source"),
                    value = LanguageText.text(settings.catalogSource.labelKey),
                    expanded = sourceMenu,
                )
                ExposedDropdownMenu(sourceMenu, { sourceMenu = false }) {
                    CatalogSource.entries.forEach { choice ->
                        val selected = choice == settings.catalogSource
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .clickable {
                                    sourceMenu = false
                                    if (choice != settings.catalogSource) {
                                        vm.updateSettings { it.copy(catalogSource = choice) }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                LanguageText.text(choice.labelKey),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
            Text(
                LanguageText.text("settings.catalog.source.about"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::updateStockCatalogFromGitHub,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.update.stock.catalog.github")) }

            FieldwatchActionButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(LanguageText.text("ui.restore.default.signatures.presets"))
            }
            }
            }

            if (topic == SettingsTopic.ELEVATED) {
                ElevatedPage(vm, settings)
            }
            if (topic == SettingsTopic.BACKUP) {
            SectionCard(LanguageText.text("settings.topic.backup"), showTitle = false) {
            Text(
                (LanguageText.text("ui.saves.switches.filters.presets") + " ") +
                    (LanguageText.text("ui.catalog.signatures.import.replaces") + " ") +
                    LanguageText.text("ui.fieldwatch.asks.before.file"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSettingsShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.export.settings")) }
            FieldwatchActionButton(
                onClick = { saveSettings.launch(vm.suggestedSettingsName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.save.settings.sd.card")) }
            FieldwatchActionButton(
                onClick = {
                    importSettings.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.import.settings")) }
            }
            }

            if (topic == null) {
            FieldwatchActionButton(
                onClick = onShowLiveTour,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.show.live.tour")) }
            Text(
                LanguageText.text("ui.shows.live.tour.again"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = onScanStatus,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.diagnostics.label")) }
            Text(
                LanguageText.text("ui.shows.whether.scanning.running"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Fieldwatch ${dev.namelessnanashi.fieldwatch.BuildConfig.VERSION_NAME}  ·  Catalog ${state.catalogVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val footerLifecycle = LocalLifecycleOwner.current
            var ipv4 by remember { mutableStateOf(localIpv4Addresses()) }
            DisposableEffect(footerLifecycle) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) ipv4 = localIpv4Addresses()
                }
                footerLifecycle.lifecycle.addObserver(obs)
                onDispose { footerLifecycle.lifecycle.removeObserver(obs) }
            }
            Text(
                if (ipv4.isEmpty()) {
                    LanguageText.text("ui.phone.s.ipv4.none")
                } else {
                    "This phone's IPv4  ·  ${ipv4.joinToString("  ·  ")}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            CreditFooter()
            }
        }
    }
    pendingSettingsImport?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::dismissPendingSettingsImport,
            title = { Text(LanguageText.text("ui.import.settings.2")) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(onClick = vm::confirmPendingSettingsImport) { Text(LanguageText.text("language.import.action")) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPendingSettingsImport) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(LanguageText.text("ui.restore.defaults")) },
            text = {
                Text(
                    (LanguageText.text("ui.replaces.stock.catalog.colors") + " ") +
                        (LanguageText.text("ui.signatures.filters.added.deleted") + " ") +
                        LanguageText.text("ui.cannot.undone"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        vm.restoreDefaults()
                    },
                ) { Text(LanguageText.text("ui.restore.label")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
}

private enum class SettingsTopic(val titleKey: String) {
    APPEARANCE("settings.topic.appearance"),
    SCANNING("settings.topic.scanning"),
    WATCHLIST("settings.topic.watchlist"),
    LOCATION("settings.topic.location.maps"),
    GNSS("settings.topic.gnss"),
    TAK("settings.topic.tak"),
    LOGGING("settings.topic.logging"),
    SIGNATURES("settings.topic.signatures"),
    ELEVATED("settings.topic.elevated"),
    BACKUP("settings.topic.backup"),
    ;

    fun title(): String = LanguageText.text(titleKey)
}

@Composable
private fun SettingsTopicList(
    settings: AppSettings,
    catalogVersion: Int,
    takInstallTag: String,
    gnssReady: Boolean,
    namedRadioCount: Int,
    onOpen: (SettingsTopic) -> Unit,
    onNamedRadios: () -> Unit,
) {
    val scanLabel = when (settings.intensity) {
        ScanIntensity.SAVER -> LanguageText.text("ui.battery.saver")
        ScanIntensity.BALANCED -> LanguageText.text("ui.balanced.label")
        ScanIntensity.PERFORMANCE -> LanguageText.text("ui.high.performance")
    }
    fun onOff(on: Boolean) = if (on) "on" else "off"
    val rows: List<Pair<SettingsTopic?, String>> = listOf(
        SettingsTopic.APPEARANCE to "Privacy ${onOff(settings.demoMode)}",
        SettingsTopic.SCANNING to scanLabel,
        SettingsTopic.WATCHLIST to "Alerts ${onOff(settings.alertsEnabled)}",
        null to when (namedRadioCount) {
            0 -> LanguageText.text("display.none")
            1 -> LanguageText.text("ui.1.radio")
            else -> "$namedRadioCount radios"
        },
        SettingsTopic.LOCATION to "GPS ${onOff(settings.tagLocation)} · Places ${onOff(settings.placeWatch)}",
        SettingsTopic.GNSS to when {
            !gnssReady -> LanguageText.text("ui.needs.calibration")
            settings.gnssMonitor -> LanguageText.text("ui.on.label")
            else -> LanguageText.text("ui.off.label")
        },
        SettingsTopic.TAK to when {
            settings.takEnabled && settings.demoMode -> LanguageText.text("ui.paused.label")
            settings.takEnabled -> "On · ${TakInstall.callsign(settings.takCallsign, takInstallTag)}"
            else -> LanguageText.text("ui.off.label")
        },
        SettingsTopic.LOGGING to if (settings.loggingEnabled) LanguageText.text("ui.on.label") else LanguageText.text("ui.off.label"),
        SettingsTopic.SIGNATURES to "Catalog $catalogVersion",
        SettingsTopic.ELEVATED to "Root · Shizuku · Admin · Vector",
        SettingsTopic.BACKUP to LanguageText.text("ui.export.import"),
    )
    rows.forEach { (item, value) ->
        if (item == null) {
            SettingsTopicRow(LanguageText.text("settings.named.radios"), value, onNamedRadios)
        } else {
            SettingsTopicRow(item.title(), value) { onOpen(item) }
        }
    }
}

@Composable
private fun SettingsTopicRow(title: String, value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreditFooter() {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            LanguageText.text("ui.support.fork.title"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            LanguageText.text("ui.support.fork.body"),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SUPPORT_LINKS.forEach { (label, url) ->
                SupportChip(label) { openUrl(context, url) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            LanguageText.text("ui.copyright.c.2026.off"),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}

private val SUPPORT_LINKS = listOf(
    "GitHub Sponsors" to "https://github.com/sponsors/NanashiTheNameless",
    "Buy Me a Coffee" to "https://buymeacoffee.com/NamelessNanashi",
    "Ko-fi" to "https://ko-fi.com/NanashiTheNameless",
    "Liberapay" to "https://liberapay.com/NamelessNanashi",
    "Throne" to "https://throne.com/NamelessNanashi",
)

@Composable
private fun SupportChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakFeedSettings(
    settings: AppSettings,
    vm: FieldwatchViewModel,
    status: TakFeedStatus,
    installTag: String,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hostText by remember { mutableStateOf(settings.takHost) }
    var portText by remember { mutableStateOf(settings.takPort.toString()) }
    LaunchedEffect(settings.takHost) { hostText = settings.takHost }
    LaunchedEffect(settings.takPort) { portText = settings.takPort.toString() }
    val preset = TakPublish.udpPreset(settings.takHost, settings.takPort)
    val phoneName = TakInstall.callsign(settings.takCallsign, installTag)
    var callsignText by remember { mutableStateOf(settings.takCallsign) }
    LaunchedEffect(settings.takCallsign) { callsignText = settings.takCallsign }
    Text(LanguageText.text("ui.phone.label"), style = MaterialTheme.typography.labelLarge)
    FieldwatchOutlinedField(
        value = callsignText,
        onValueChange = { value ->
            val next = TakInstall.storedCallsign(value)
            callsignText = next
            vm.updateSettings { it.copy(takCallsign = next) }
        },
        label = LanguageText.text("ui.tak.callsign"),
        placeholder = phoneName,
        supportingText = "Blank uses $phoneName. A name you type stays if you make a new tag, and it is copied with settings.",
        enabled = !settings.demoMode,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Install tag  $installTag", Modifier.weight(1f))
        TextButton(
            onClick = vm::regenerateTakTag,
            enabled = !settings.demoMode && installTag.isNotEmpty(),
        ) { Text(LanguageText.text("ui.new.tag")) }
    }
    Text(
        (LanguageText.text("ui.each.phone.has.its") + " ") +
            (LanguageText.text("ui.new.tag.gives.phone") + " ") +
            LanguageText.text("ui.if.feed.markers.phone"),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(LanguageText.text("ui.destination.label"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.THIS_PHONE,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.THIS_PHONE)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.phone.label")) },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.LAN_MULTICAST,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.LAN_MULTICAST)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.lan.multicast")) },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.CUSTOM,
            onClick = {
                if (preset != TakUdpPreset.CUSTOM) {
                    val (host, port) = TakPublish.applyPreset(TakUdpPreset.CUSTOM)
                    vm.updateSettings { it.copy(takHost = host, takPort = port) }
                }
            },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.custom.label")) },
        )
    }
    Text(
        "This phone is ATAK on this handset, at ${TakDefaults.LOOPBACK}:${TakDefaults.PORT}. " +
            "LAN multicast is ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT}, for other ATAK apps on this Wi-Fi. " +
            (LanguageText.text("ui.custom.one.address.udp") + " ") +
            "If This phone shows no pins, use Custom with the Wi-Fi address at the bottom of Settings and port ${TakDefaults.PORT}.",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    FieldwatchOutlinedField(
        value = hostText,
        onValueChange = { value ->
            hostText = value
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) {
                vm.updateSettings { it.copy(takHost = trimmed) }
            }
        },
        label = LanguageText.text("ui.host.label"),
        placeholder = TakDefaults.HOST,
        enabled = !settings.demoMode,
    )
    FieldwatchOutlinedField(
        value = portText,
        onValueChange = { value ->
            val filtered = value.filter { it.isDigit() }.take(5)
            portText = filtered
            filtered.toIntOrNull()?.let { n ->
                if (n in 1..65_535) {
                    vm.updateSettings { it.copy(takPort = n) }
                }
            }
        },
        label = LanguageText.text("ui.port.label"),
        placeholder = TakDefaults.PORT.toString(),
        supportingText = "UDP. This phone uses ${TakDefaults.PORT}. LAN multicast uses ${TakDefaults.SA_PORT}. Not TCP 8087.",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !settings.demoMode,
    )
    Text(takStatusLine(status), style = MaterialTheme.typography.bodySmall, color = muted)
    Text(LanguageText.text("ui.what.send"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = settings.takAttention,
            onClick = { vm.updateSettings { it.copy(takAttention = !it.takAttention) } },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("chrome.extra.attention")) },
        )
        FieldwatchFilterChip(
            selected = settings.takPayloadFix,
            onClick = { vm.updateSettings { it.copy(takPayloadFix = !it.takPayloadFix) } },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.payload.location")) },
        )
        FieldwatchFilterChip(
            selected = settings.takWatchlist,
            onClick = { vm.updateSettings { it.copy(takWatchlist = !it.takWatchlist) } },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("settings.topic.watchlist")) },
        )
        FieldwatchFilterChip(
            selected = settings.takAllSignatures,
            onClick = { vm.updateSettings { it.copy(takAllSignatures = !it.takAllSignatures) } },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.all.signatures")) },
        )
    }
    Text(
        (LanguageText.text("ui.turn.any.combination.extra") + " ") +
            (LanguageText.text("ui.payload.location.sends.remote") + " ") +
            (LanguageText.text("ui.watchlist.sends.bookmarked.signatures") + " ") +
            (LanguageText.text("ui.all.signatures.sends.every") + " ") +
            LanguageText.text("ui.unmatched.radios.never.sent"),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(LanguageText.text("ui.how.long.spot.stays"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssHold.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takRadioHold == item,
                onClick = { vm.updateSettings { it.copy(takRadioHold = item) } },
                enabled = !settings.demoMode,
                label = { Text(item.label()) },
            )
        }
    }
    Text(
        (LanguageText.text("ui.after.radio.leaves.spot") + " ") +
            (LanguageText.text("ui.until.deleted.stays.until") + " ") +
            LanguageText.text("ui.phone.s.marker.stays"),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text("GNSS", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssSend.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takGnss == item,
                onClick = { vm.updateSettings { it.copy(takGnss = item) } },
                enabled = !settings.demoMode,
                label = { Text(item.label()) },
            )
        }
    }
    Text(
        (LanguageText.text("ui.mark.map.where.phone") + " ") +
            (LanguageText.text("ui.short.hit.pin.hit") + " ") +
            (LanguageText.text("ui.mark.does.not.say") + " ") +
            (LanguageText.text("ui.off.sends.nothing.while") + " ") +
            (LanguageText.text("ui.red.line.also.keeps") + " ") +
            (LanguageText.text("ui.any.hit.also.sends") + " ") +
            LanguageText.text("ui.phone.needs.gps.fix"),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(LanguageText.text("ui.how.long.mark.stays"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssHold.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takGnssHold == item,
                onClick = { vm.updateSettings { it.copy(takGnssHold = item) } },
                enabled = !settings.demoMode && settings.takGnss != TakGnssSend.OFF,
                label = { Text(item.label()) },
            )
        }
    }
    Text(
        LanguageText.text("ui.time.starts.last.update"),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(LanguageText.text("ui.floods.label"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakFloodSend.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takFlood == item,
                onClick = { vm.updateSettings { it.copy(takFlood = item) } },
                enabled = !settings.demoMode,
                label = { Text(item.label()) },
            )
        }
    }
    Text(
        (LanguageText.text("ui.adds.note.phone.s") + " ") +
            (LanguageText.text("ui.does.not.change.red") + " ") +
            LanguageText.text("ui.note.does.not.say"),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}

private fun takStatusLine(status: TakFeedStatus): String {
    if (status.paused) return LanguageText.text("ui.feed.status.paused.privacy")
    if (status.error != null) {
        val whenAt = takStatusWhen(status.at)
        return LanguageText.text("ui.feed.status") + "  ·  error: ${status.error}" +
            if (whenAt.isNotEmpty()) "  ·  $whenAt" else ""
    }
    if (status.at <= 0L) {
        return LanguageText.text("ui.feed.status.no.send")
    }
    val bits = ArrayList<String>(5)
    bits += LanguageText.text("ui.feed.on", mapOf("n" to status.onFeed.toString()))
    bits += LanguageText.text("ui.feed.sent", mapOf("n" to status.sent.toString()))
    if (status.gone > 0) {
        bits += LanguageText.text("ui.n.gone", mapOf("n" to status.gone.toString()))
    }
    if (status.dest.isNotBlank()) bits += status.dest
    val whenAt = takStatusWhen(status.at)
    if (whenAt.isNotEmpty()) bits += whenAt
    val head = LanguageText.text("ui.feed.status") + "  ·  ${bits.joinToString("  ·  ")}"
    return if (status.detail.isNotBlank() && status.sent == 0 && status.gone == 0) {
        "$head  ·  ${status.detail}"
    } else {
        head
    }
}

private fun takStatusWhen(at: Long): String {
    if (at <= 0L) return ""
    return java.time.Instant.ofEpochMilli(at)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}

private fun localIpv4Addresses(): List<String> {
    val found = LinkedHashSet<String>()
    val nifs = runCatching {
        java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
    }.getOrDefault(emptyList())
    for (nif in nifs) {
        if (!nif.isUp || nif.isLoopback) continue
        for (addr in java.util.Collections.list(nif.inetAddresses)) {
            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                addr.hostAddress?.let { found += it }
            }
        }
    }
    return found.toList()
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

internal fun isBackgroundUsageAllowed(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted != true

private enum class BatteryAndroidGate { BACKGROUND, UNRESTRICTED }

/**
 * The Scanning channel's system page, where the user can minimize it to drop
 * the status-bar icon. Falls back to the app's notification page.
 */
private fun openScanChannelSettings(context: Context) {
    ScanService.ensureChannel(context)
    val attempts = listOf(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ScanService.CHANNEL),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
    for (intent in attempts) {
        if (intent.resolveActivity(context.packageManager) == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

/**
 * Fieldwatch's per-app Battery page. Samsung keeps Allow background usage and
 * Unrestricted on this same screen. [highlightBackground] asks Settings to
 * focus the background-usage switch when the OEM supports it.
 */
private fun openAppBatteryPage(context: Context, highlightBackground: Boolean) {
    val pkgUri = Uri.fromParts("package", context.packageName, null)
    val attempts = listOf(
        Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
            data = pkgUri
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("request_ignore_background_restriction", highlightBackground)
            if (!highlightBackground) {
                putExtra(":settings:fragment_args_key", "unrestricted_pref")
            }
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = pkgUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    for (intent in attempts) {
        if (intent.resolveActivity(context.packageManager) == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsEnginePicker(selected: String, enabled: Boolean, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val engines = remember { TtsEngines.all(context) }
    val systemDefault = LanguageText.text("settings.tts.default")
    val notInstalled = LanguageText.text("settings.tts.notinstalled")
    val current = if (selected.isBlank()) {
        systemDefault
    } else {
        engines.firstOrNull { it.packageName == selected }?.label ?: selected
    }
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { if (enabled) open = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        FieldwatchDropdownField(
            label = LanguageText.text("settings.tts.engine"),
            value = current,
            expanded = open,
            enabled = enabled,
        )
        ExposedDropdownMenu(open, { open = false }) {
            val rows = listOf(Triple("", systemDefault, true)) +
                engines.map { Triple(it.packageName, if (it.installed) it.label else "${it.label} · $notInstalled", it.installed) }
            rows.forEach { (pkg, label, installed) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .clickable {
                            open = false
                            if (!installed) {
                                // Not installed: open its download page instead of selecting it.
                                TtsEngines.known(pkg)?.let { openUrl(context, it.url) }
                            } else if (pkg != selected) {
                                onPick(pkg)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (installed) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (pkg == selected) Icon(Icons.Outlined.Check, contentDescription = null)
                }
            }
        }
    }
    val engineApp = selected.takeIf { it.isNotBlank() }?.let { TtsEngines.launchIntent(context, it) }
    if (engineApp != null) {
        FieldwatchActionButton(
            onClick = { runCatching { context.startActivity(engineApp) } },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(LanguageText.text("settings.tts.open", mapOf("engine" to current))) }
    }
    Text(
        LanguageText.text("settings.tts.about"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AlertSoundPicker(
    settings: AppSettings,
    enabled: Boolean,
    onBuiltIn: () -> Unit,
    onPhoneSound: (Uri?) -> Unit,
    onFile: (Uri) -> Unit,
) {
    val phonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (result.resultCode == Activity.RESULT_OK) onPhoneSound(picked)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onFile)
    }
    val current = if (settings.alertSoundUri.isBlank()) {
        LanguageText.text("settings.sound.builtin")
    } else {
        settings.alertSoundName.ifBlank { settings.alertSoundUri }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(LanguageText.text("settings.sound.title"), Modifier.weight(1f))
        Text(current, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchActionButton(onClick = onBuiltIn, enabled = enabled && settings.alertSoundUri.isNotBlank()) {
            Text(LanguageText.text("settings.sound.builtin"))
        }
        FieldwatchActionButton(
            onClick = {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                        RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE,
                    )
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, LanguageText.text("settings.sound.title"))
                if (settings.alertSoundUri.startsWith("content:")) {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(settings.alertSoundUri))
                }
                runCatching { phonePicker.launch(intent) }
            },
            enabled = enabled,
        ) { Text(LanguageText.text("settings.sound.system")) }
        FieldwatchActionButton(onClick = { filePicker.launch(arrayOf("audio/*")) }, enabled = enabled) {
            Text(LanguageText.text("settings.sound.file"))
        }
    }
    Text(
        LanguageText.text("settings.sound.about"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsVoicePicker(
    vm: FieldwatchViewModel,
    selected: String,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    val voices by vm.ttsVoices.collectAsStateWithLifecycle()
    LaunchedEffect(enabled) { if (enabled) vm.loadTtsVoices() }
    val engineDefault = LanguageText.text("settings.voice.default")
    fun label(v: VoiceOption) = buildString {
        append(v.language).append(" · ").append(v.name)
        if (v.network) append(" · ").append(LanguageText.text("settings.voice.network"))
    }
    val choices = listOf("" to engineDefault) + voices.map { it.name to label(it) }
    val current = choices.firstOrNull { it.first == selected }?.second ?: selected.ifBlank { engineDefault }
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { if (enabled) open = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        FieldwatchDropdownField(
            label = LanguageText.text("settings.voice.title"),
            value = current,
            expanded = open,
            enabled = enabled,
        )
        ExposedDropdownMenu(open, { open = false }) {
            choices.forEach { (name, text) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .clickable {
                            open = false
                            if (name != selected) onPick(name)
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (name == selected) Icon(Icons.Outlined.Check, contentDescription = null)
                }
            }
        }
    }
    if (enabled && voices.isEmpty()) {
        Text(
            LanguageText.text("settings.voice.none"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        LanguageText.text("settings.voice.about"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Dragging only recomposes this control; the settings store is updated once on release. */
@Composable
private fun AudioWakeupControl(valueMs: Int, enabled: Boolean, onCommit: (Int) -> Unit) {
    var sliderValue by remember { mutableFloatStateOf(valueMs.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(valueMs) {
        if (!dragging) sliderValue = valueMs.toFloat()
    }
    val roundedMs = (sliderValue / 50f).roundToInt() * 50
    Text(
        "${LanguageText.text("settings.leadin.title")}: $roundedMs ms",
        style = MaterialTheme.typography.bodyMedium,
    )
    FieldwatchSlider(
        value = sliderValue,
        onValueChange = { sliderValue = it; dragging = true },
        onValueChangeFinished = {
            val committedMs = (sliderValue / 50f).roundToInt() * 50
            dragging = false
            sliderValue = committedMs.toFloat()
            if (committedMs != valueMs) onCommit(committedMs)
        },
        valueRange = 0f..1000f,
        enabled = enabled,
    )
}
