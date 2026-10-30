package dev.namelessnanashi.fieldwatch.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.namelessnanashi.fieldwatch.data.PathTiles
import dev.namelessnanashi.fieldwatch.domain.GpsSample
import dev.namelessnanashi.fieldwatch.domain.HuntHeat
import dev.namelessnanashi.fieldwatch.domain.HuntHeatRaster
import dev.namelessnanashi.fieldwatch.domain.FlockCredit
import dev.namelessnanashi.fieldwatch.domain.FlockIndex
import dev.namelessnanashi.fieldwatch.domain.LanguageText
import dev.namelessnanashi.fieldwatch.domain.MapFrame
import dev.namelessnanashi.fieldwatch.domain.PayloadLocation
import dev.namelessnanashi.fieldwatch.domain.SitPathPlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val You = Color(0xFF37D6FF)

/**
 * Map of this hunt. North stays up until the compass is set to follow travel.
 * One field, colored by a smooth ramp from poor to powerful. The ramp blends
 * where hears differ. An edge fades out in that same color, so a weak edge stays blue.
 */
@Composable
fun HuntHeatMap(
    trail: List<GpsSample>,
    hereLat: Double?,
    hereLon: Double?,
    tiles: List<PathTiles.Tile>,
    position: String?,
    hint: String?,
    viewport: SitPathPlot.Viewport?,
    onViewport: (SitPathPlot.Viewport?) -> Unit,
    modifier: Modifier = Modifier,
    /** Circle around the dot, in meters. Zero hides it. */
    uncertaintyM: Double = 0.0,
    /** Amber circle while steps are paused. */
    holdFlat: Boolean = false,
    /** Degrees clockwise from north. NaN keeps this frame north-up. */
    courseDeg: Double = Double.NaN,
    /** [MapFrame]. The hunt follow stays its own close window in every frame. */
    mapFrame: Int = MapFrame.NORTH,
    /** Null hides the button and keeps the drawn N. */
    onRotate: (() -> Unit)? = null,
    /** Double-tap. The canvas also clears a held view. */
    onToggleClose: (() -> Unit)? = null,
    flock: Boolean = false,
    flockIndex: FlockIndex? = null,
    flockRevision: Int = 0,
    /** South, north, west, east of this picture, including a finger-held view. */
    onTileWindow: ((DoubleArray) -> Unit)? = null,
) {
    val here = if (PayloadLocation.validCoord(hereLat, hereLon)) {
        listOf(GpsSample(0L, hereLat!!, hereLon!!))
    } else {
        emptyList()
    }
    val model = remember(trail, hereLat, hereLon) {
        HuntHeat.followModel(hereLat, hereLon, trail)
    }
    val track = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val labelStyle = TextStyle(fontSize = 10.sp, color = muted)
    val flockCountStyle = TextStyle(fontSize = 9.sp, color = Color.Black)
    val flockGen = if (flock && flockIndex != null) flockRevision else -1
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val appliedCourse = if (onRotate == null) {
        Double.NaN
    } else {
        MapFrame.drawnCourse(
            mapFrame,
            if (viewport == null) null else viewport.heldCourseDeg,
            courseDeg,
        )
    }
    // Drawn angle turns through the gap between GPS courses. Tiles stay on the heading reached.
    val drawnCourse = rememberEasedCourse(appliedCourse)
    val layout = remember(model, boxSize, viewport, drawnCourse) {
        if (boxSize.width < 8 || boxSize.height < 8) null
        else SitPathPlot.layout(
            model,
            boxSize.width.toFloat(),
            boxSize.height.toFloat(),
            viewport = viewport,
            minHalfM = HuntHeat.PINCH_HALF_M,
            courseDeg = drawnCourse,
        )
    }
    val tileLayout = remember(model, boxSize, viewport, appliedCourse) {
        if (boxSize.width < 8 || boxSize.height < 8) null
        else SitPathPlot.layout(
            model,
            boxSize.width.toFloat(),
            boxSize.height.toFloat(),
            viewport = viewport,
            minHalfM = HuntHeat.PINCH_HALF_M,
            courseDeg = appliedCourse,
        )
    }
    val onTileWindowRef = remember { mutableStateOf(onTileWindow) }
    onTileWindowRef.value = onTileWindow
    LaunchedEffect(tileLayout) {
        val window = tileLayout?.tileWindow() ?: return@LaunchedEffect
        onTileWindowRef.value?.invoke(window)
    }
    // Finish a field even while readings arrive, then take only the latest pending trail.
    // The caller keys this composition by hunt session/placement so resets cancel old work.
    val currentTrail by rememberUpdatedState(trail)
    val heat by produceState<HuntHeatField?>(initialValue = null) {
        HuntHeatRaster.collect(
            snapshotFlow { currentTrail },
            render = { withContext(Dispatchers.Default) { rasterHuntHeat(it) } },
            onField = { value = it },
        )
    }
    val layoutRef = remember { mutableStateOf(layout) }
    val viewportRef = remember { mutableStateOf(viewport) }
    val onViewportRef = remember { mutableStateOf(onViewport) }
    val courseRef = remember { mutableStateOf(Double.NaN) }
    val onToggleRef = remember { mutableStateOf(onToggleClose) }
    val lastTapMs = remember { mutableLongStateOf(0L) }
    layoutRef.value = layout
    viewportRef.value = viewport
    onViewportRef.value = onViewport
    courseRef.value = drawnCourse
    onToggleRef.value = onToggleClose
    val gestures = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var armed = false
            var zoomAccum = 1f
            var panAccum = Offset.Zero
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val focus = event.calculateCentroid(useCurrent = true)
                if (!armed) {
                    zoomAccum *= zoom
                    panAccum += pan
                    val past = abs(1f - zoomAccum) > 0.08f || panAccum.getDistance() > slop
                    if (past) {
                        armed = true
                        moveHuntCamera(
                            zoomAccum, panAccum, focus, layoutRef, viewportRef, onViewportRef, courseRef,
                        )
                        event.changes.forEach { it.consume() }
                    }
                } else {
                    moveHuntCamera(zoom, pan, focus, layoutRef, viewportRef, onViewportRef, courseRef)
                    event.changes.forEach { it.consume() }
                }
            }
            if (!armed) {
                val now = down.uptimeMillis
                if (now - lastTapMs.longValue <= 280L) {
                    lastTapMs.longValue = 0L
                    viewportRef.value = null
                    onViewportRef.value(null)
                    onToggleRef.value?.invoke()
                } else {
                    lastTapMs.longValue = now
                }
            }
        }
    }
    val ramp = Array(25) { i ->
        val t = i / 24f
        t to Color(HuntHeat.colorArgb(t))
    }
    Box(modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
        Canvas(Modifier.fillMaxSize().then(gestures)) {
            val lay = layout ?: return@Canvas
            clipRect(lay.plotLeft, lay.plotTop, lay.plotRight, lay.plotBottom) {
                drawMapTiles(tiles, lay.project, alpha = 0.55f)
                if (flockGen >= 0 && flockIndex != null) {
                    drawFlockMarks(lay, flockIndex, measurer, flockCountStyle)
                }
                heat?.let { field ->
                    drawMapRaster(
                        field.image,
                        field.north,
                        field.west,
                        field.south,
                        field.east,
                        lay.project,
                        filter = FilterQuality.High,
                    )
                }
                val heard = HuntHeat.loudest(trail)
                val markLat = heat?.hotLat ?: heard?.lat
                val markLon = heat?.hotLon ?: heard?.lon
                if (markLat != null && markLon != null) {
                    val pt = lay.project(markLat, markLon)
                    val at = Offset(pt.x, pt.y)
                    drawCircle(Color.Black.copy(alpha = 0.85f), radius = 8f, center = at, style = Stroke(3.5f))
                    drawCircle(Color.White, radius = 6.2f, center = at, style = Stroke(1.8f))
                }
                if (here.isNotEmpty()) {
                    val pt = lay.project(here[0].lat, here[0].lon)
                    val at = Offset(pt.x, pt.y)
                    if (uncertaintyM > 0.0 && lay.metersPerPx > 0f) {
                        val radiusPx = (uncertaintyM / lay.metersPerPx).toFloat()
                        val ring = if (holdFlat) Color(0xFFFFC107) else You
                        drawCircle(ring, radius = radiusPx, center = at, style = Stroke(2.5f))
                    }
                    drawCircle(Color.White, radius = 8f, center = at)
                    drawCircle(You, radius = 5.6f, center = at)
                }
            }
            val barW = (size.width - 32f) * lay.scaleBarFrac
            val barLabel = SitPathPlot.scaleLabel(lay.scaleBarM)
            val measured = measurer.measure(barLabel, labelStyle)
            val north = measurer.measure("N", labelStyle)
            val groupW = barW + 8f + measured.size.width
            val barX = ((size.width - groupW) / 2f).coerceAtLeast(8f)
            val barY = size.height - 16f
            drawLine(muted, Offset(barX, barY), Offset(barX + barW, barY), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(muted, Offset(barX, barY - 5f), Offset(barX, barY + 5f), strokeWidth = 3f)
            drawLine(muted, Offset(barX + barW, barY - 5f), Offset(barX + barW, barY + 5f), strokeWidth = 3f)
            drawText(measured, topLeft = Offset(barX + barW + 8f, barY - measured.size.height / 2f))
            if (onRotate == null) {
                drawText(north, topLeft = Offset(size.width - north.size.width - 10f, barY - north.size.height / 2f))
            }
        }
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .widthIn(max = 220.dp)
                .background(surface.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Brush.horizontalGradient(*ramp), RoundedCornerShape(4.dp)),
            )
            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                LegendWord(LanguageText.text("hunt.heat.poor"), TextAlign.Start)
                LegendWord(LanguageText.text("hunt.heat.moderate"), TextAlign.Center)
                LegendWord(LanguageText.text("hunt.heat.strong"), TextAlign.Center)
                LegendWord(LanguageText.text("hunt.heat.powerful"), TextAlign.End)
            }
        }
        if (viewport != null) {
            MapRecenterButton(
                onClick = { onViewport(null) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
        if (!hint.isNullOrBlank()) {
            Text(
                hint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(surface.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        val mapTile = tiles.firstOrNull()
        val layForCredit = layout
        if (layForCredit != null && boxSize.height > 0 && (mapTile != null || flockGen >= 0)) {
            val mapCredit = mapTile?.basemap
            val bottomPad = with(density) { (boxSize.height - layForCredit.plotBottom).toDp() + 2.dp }
            val half = with(density) {
                ((layForCredit.plotRight - layForCredit.plotLeft) / 2f).toDp() - 4.dp
            }.coerceAtLeast(0.dp)
            val lines = if (mapCredit != null) {
                mapCredit.shownCreditLines(
                    mapCredit.cautionKey?.let { LanguageText.text(it) },
                    mapTile.overlays,
                    flockGen >= 0,
                )
            } else {
                listOf(FlockCredit.LINE)
            }
            val besideCompass = onRotate != null && lines.size == 1
            val endPad = with(density) { (boxSize.width - layForCredit.plotRight).toDp() + 2.dp } +
                if (besideCompass) 52.dp else 0.dp
            val lift = if (onRotate != null && lines.size > 1) 52.dp else 0.dp
            val link = mapCredit?.creditUrl ?: if (flockGen >= 0) FlockCredit.URL else null
            MapCreditChip(
                lines = lines,
                link = link,
                onOpen = { runCatching { uriHandler.openUri(it) } },
                color = track,
                textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                maxWidth = half,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endPad, bottom = bottomPad + lift),
            )
        }
        if (onRotate != null) {
            MapCompassButton(
                frame = mapFrame,
                northDeg = drawnCourse,
                onClick = onRotate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
            )
        }
        if (!position.isNullOrBlank() && layForCredit != null && boxSize.height > 0) {
            val bottomPad = with(density) { (boxSize.height - layForCredit.plotBottom).toDp() + 2.dp }
            val startPad = with(density) { layForCredit.plotLeft.toDp() + 2.dp }
            val half = with(density) {
                ((layForCredit.plotRight - layForCredit.plotLeft) / 2f).toDp() - 4.dp
            }.coerceAtLeast(0.dp)
            Text(
                position,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = startPad, bottom = bottomPad)
                    .widthIn(max = half)
                    .background(surface.copy(alpha = 0.84f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                color = track,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.LegendWord(text: String, align: TextAlign) {
    Text(
        text,
        modifier = Modifier.weight(1f),
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private class HuntHeatField(
    val image: ImageBitmap,
    val north: Double,
    val west: Double,
    val south: Double,
    val east: Double,
    val hotLat: Double?,
    val hotLon: Double?,
)

/** Bitmap creation stays on the same worker as the platform-independent raster. */
private suspend fun rasterHuntHeat(trail: List<GpsSample>): HuntHeatField? {
    val field = HuntHeatRaster.render(trail) ?: return null
    currentCoroutineContext().ensureActive()
    val bitmap = Bitmap.createBitmap(field.cols, field.rows, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(field.pixels, 0, field.cols, 0, 0, field.cols, field.rows)
    return HuntHeatField(
        bitmap.asImageBitmap(), field.north, field.west, field.south, field.east,
        field.hotLat, field.hotLon,
    )
}

private fun moveHuntCamera(
    zoom: Float,
    pan: Offset,
    focus: Offset,
    layoutRef: MutableState<SitPathPlot.Layout?>,
    viewportRef: MutableState<SitPathPlot.Viewport?>,
    onViewportRef: MutableState<(SitPathPlot.Viewport?) -> Unit>,
    courseRef: MutableState<Double>,
) {
    val lay = layoutRef.value ?: return
    if ((zoom == 1f || zoom <= 0f) && pan == Offset.Zero) return
    var next = viewportRef.value ?: SitPathPlot.viewportOf(lay, courseRef.value)
    if (zoom > 0f && zoom != 1f && focus != Offset.Unspecified) {
        val (lat, lon) = lay.unproject(focus.x, focus.y)
        next = next.zoomAbout(zoom, lat, lon, minHalfM = HuntHeat.PINCH_HALF_M)
    }
    if (pan != Offset.Zero) {
        next = next.panScreen(pan.x, pan.y, lay.metersPerPx, courseRef.value)
    }
    viewportRef.value = next
    onViewportRef.value(next)
}
