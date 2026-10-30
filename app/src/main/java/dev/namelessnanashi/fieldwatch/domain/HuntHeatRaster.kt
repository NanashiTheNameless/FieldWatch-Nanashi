package dev.namelessnanashi.fieldwatch.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Bounded raster of the chronological walk and nearby parts of it, in meters. */
internal object HuntHeatRaster {
    const val CELL_VISIT_BUDGET = 900_000L
    private const val MAX_HEAT_PX = 640
    private const val CELL_M = 0.18

    /** Finish the current field, then render the newest pending trail. Leaving the
     * hunt (or changing session/placement) cancels this collection and its worker.
     */
    suspend fun <T> collect(
        updates: Flow<List<GpsSample>>,
        render: suspend (List<GpsSample>) -> T,
        onField: (T) -> Unit,
    ) {
        updates.conflate().collect { onField(render(it)) }
    }

    data class Field(
        val pixels: IntArray,
        val cols: Int,
        val rows: Int,
        val cellM: Double,
        val visitedCells: Long,
        val strokeCount: Int,
        val north: Double,
        val west: Double,
        val south: Double,
        val east: Double,
        val hotLat: Double?,
        val hotLon: Double?,
    )

    internal data class Stroke(val a: Int, val b: Int)

    /** Preserve the walk, and bridge to the closest neighbor in each of eight directions.
     * Repeated visits cannot create a quadratic number of redundant painted bridges.
     */
    internal fun strokes(east: DoubleArray, north: DoubleArray, check: () -> Unit = {}): List<Stroke> {
        val out = ArrayList<Stroke>()
        val linked = BooleanArray(east.size)
        val seen = HashSet<Long>()
        fun add(a: Int, b: Int) {
            val lo = min(a, b)
            val hi = max(a, b)
            val key = (lo.toLong() shl 32) or hi.toLong()
            if (seen.add(key)) out.add(Stroke(lo, hi))
        }
        for (h in 1 until east.size) {
            check()
            if (!HuntHeat.connects(hypot(east[h] - east[h - 1], north[h] - north[h - 1]))) continue
            add(h - 1, h)
            linked[h] = true
            linked[h - 1] = true
        }
        for (h in east.indices) if (!linked[h]) add(h, h)
        val close = HuntHeat.CLOSE_M
        val bins = HashMap<Long, MutableList<Int>>()
        fun key(x: Int, y: Int) = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
        for (h in east.indices) {
            val bx = floor(east[h] / close).toInt()
            val by = floor(north[h] / close).toInt()
            bins.getOrPut(key(bx, by)) { ArrayList() }.add(h)
        }
        for (h in east.indices) {
            check()
            val nearest = IntArray(8) { -1 }
            val distance = DoubleArray(8) { Double.POSITIVE_INFINITY }
            val bx = floor(east[h] / close).toInt()
            val by = floor(north[h] / close).toInt()
            for (oy in -1..1) for (ox in -1..1) {
                for (j in bins[key(bx + ox, by + oy)].orEmpty()) {
                    if (j == h || (abs(j - h) == 1 && linked[max(h, j)])) continue
                    val dx = east[j] - east[h]
                    val dy = north[j] - north[h]
                    val d2 = dx * dx + dy * dy
                    if (d2 < 0.8 * 0.8 || d2 > close * close) continue
                    val sector = floor((atan2(dy, dx) + PI) * 4.0 / PI).toInt().coerceIn(0, 7)
                    if (d2 < distance[sector]) {
                        nearest[sector] = j
                        distance[sector] = d2
                    }
                }
            }
            for (j in nearest) if (j >= 0) add(h, j)
        }
        return out
    }

    suspend fun render(trail: List<GpsSample>): Field? {
        val context = currentCoroutineContext()
        context.ensureActive()
        val hears = ArrayList<Pair<GpsSample, Float>>(trail.size)
        for (sample in trail.takeLast(HuntHeat.CAP)) {
            context.ensureActive()
            if (!PayloadLocation.validCoord(sample.lat, sample.lon)) continue
            val level = HuntHeat.level(sample.rssi) ?: continue
            hears.add(sample to level)
        }
        if (hears.isEmpty()) return null
        var lat0 = 0.0
        var lon0 = 0.0
        for ((sample, _) in hears) {
            lat0 += sample.lat
            lon0 += sample.lon
        }
        lat0 /= hears.size
        lon0 /= hears.size
        val cos0 = cos(Math.toRadians(lat0)).coerceAtLeast(0.2)
        val mLon = 111_320.0 * cos0
        val mLat = 110_540.0
        var minE = Double.POSITIVE_INFINITY
        var maxE = Double.NEGATIVE_INFINITY
        var minN = Double.POSITIVE_INFINITY
        var maxN = Double.NEGATIVE_INFINITY
        val east = DoubleArray(hears.size)
        val north = DoubleArray(hears.size)
        for (i in hears.indices) {
            val sample = hears[i].first
            val e = (sample.lon - lon0) * mLon
            val n = (sample.lat - lat0) * mLat
            east[i] = e
            north[i] = n
            if (e < minE) minE = e
            if (e > maxE) maxE = e
            if (n < minN) minN = n
            if (n > maxN) maxN = n
        }
        val pad = HuntHeat.RIBBON_M
        minE -= pad
        maxE += pad
        minN -= pad
        maxN += pad
        val spanE = (maxE - minE).coerceAtLeast(1.0)
        val spanN = (maxN - minN).coerceAtLeast(1.0)
        val strokes = strokes(east, north) { context.ensureActive() }
        var cell = max(CELL_M, max(spanE, spanN) / MAX_HEAT_PX)
        // Count the bounding-box visits for both passes, including nearby bridges.
        // Coarsen only until those visits fit; sample count alone misses dense overlap.
        fun visits(cellM: Double): Long = strokes.sumOf { stroke ->
            val colLo = ((min(east[stroke.a], east[stroke.b]) - pad - minE) / cellM).toInt()
            val colHi = ((max(east[stroke.a], east[stroke.b]) + pad - minE) / cellM).toInt()
            val rowLo = ((maxN - max(north[stroke.a], north[stroke.b]) - pad) / cellM).toInt()
            val rowHi = ((maxN - min(north[stroke.a], north[stroke.b]) + pad) / cellM).toInt()
            2L * (colHi - colLo + 1) * (rowHi - rowLo + 1)
        }
        while (visits(cell) > CELL_VISIT_BUDGET) {
            context.ensureActive()
            cell *= 1.2
        }
        val cols = ceil(spanE / cell).toInt().coerceIn(1, MAX_HEAT_PX)
        val rows = ceil(spanN / cell).toInt().coerceIn(1, MAX_HEAT_PX)
        val count = cols * rows
        val sumW = FloatArray(count)
        val sumHw = FloatArray(count)
        val sumHl = FloatArray(count)
        val peak = FloatArray(count)
        val minDist = FloatArray(count) { Float.POSITIVE_INFINITY }
        val blob = HuntHeat.RIBBON_M
        var visited = 0L
        for (pass in 0..1) {
            for (stroke in strokes) {
                context.ensureActive()
                val e0 = east[stroke.a]
                val n0 = north[stroke.a]
                val e1 = east[stroke.b]
                val n1 = north[stroke.b]
                val l0 = hears[stroke.a].second
                val l1 = hears[stroke.b].second
                val dx = e1 - e0
                val dy = n1 - n0
                val len2 = dx * dx + dy * dy
                val colLo = (((min(e0, e1) - blob) - minE) / cell).toInt().coerceIn(0, cols - 1)
                val colHi = (((max(e0, e1) + blob) - minE) / cell).toInt().coerceIn(0, cols - 1)
                val rowLo = ((maxN - (max(n0, n1) + blob)) / cell).toInt().coerceIn(0, rows - 1)
                val rowHi = ((maxN - (min(n0, n1) - blob)) / cell).toInt().coerceIn(0, rows - 1)
                for (row in rowLo..rowHi) {
                    context.ensureActive()
                    val cn = maxN - (row + 0.5) * cell
                    val base = row * cols
                    for (col in colLo..colHi) {
                        visited++
                        val ce = minE + (col + 0.5) * cell
                        val t = if (len2 < 1e-4) 0.0 else ((ce - e0) * dx + (cn - n0) * dy) / len2
                        val u = t.coerceIn(0.0, 1.0)
                        val de = ce - (e0 + u * dx)
                        val dn = cn - (n0 + u * dy)
                        val dist2 = de * de + dn * dn
                        if (dist2 >= blob * blob) continue
                        val dist = sqrt(dist2)
                        val i = base + col
                        if (pass == 0) {
                            if (dist < minDist[i]) minDist[i] = dist.toFloat()
                        } else {
                            val w = HuntHeat.ribbonWeight(dist)
                            val heard = HuntHeat.alongLevel(l0, l1, u)
                            val extra = (dist - minDist[i]).coerceAtLeast(0.0)
                            val hw = HuntHeat.mixWeight(HuntHeat.shadeWeight(extra), heard)
                            sumW[i] += w
                            sumHw[i] += hw
                            sumHl[i] += hw * heard
                            if (w > peak[i]) peak[i] = w
                        }
                    }
                }
            }
        }
        val pixels = IntArray(count)
        for (i in 0 until count) {
            val weight = sumW[i]
            if (weight < 0.02f) continue
            val fade = peak[i]
            if (fade < 0.04f) continue
            val a = (fade * 208f).toInt().coerceIn(0, 208)
            if (a == 0) continue
            val heatW = sumHw[i]
            val level = if (heatW > 1e-4f) sumHl[i] / heatW else 0f
            val argb = HuntHeat.colorArgb(level)
            val pr = ((argb ushr 16) and 0xFF) * a / 255
            val pg = ((argb ushr 8) and 0xFF) * a / 255
            val pb = (argb and 0xFF) * a / 255
            pixels[i] = (a shl 24) or (pr shl 16) or (pg shl 8) or pb
        }
        context.ensureActive()
        val widthM = cols * cell
        val heightM = rows * cell
        val spot = HuntHeat.hotSpot(sumHw, sumHl, peak, cols)
        val hotLat = spot?.let { lat0 + (maxN - it.second * cell) / mLat }
        val hotLon = spot?.let { lon0 + (minE + it.first * cell) / mLon }
        return Field(
            pixels = pixels,
            cols = cols,
            rows = rows,
            cellM = cell,
            visitedCells = visited,
            strokeCount = strokes.size,
            north = lat0 + maxN / mLat,
            west = lon0 + minE / mLon,
            south = lat0 + (maxN - heightM) / mLat,
            east = lon0 + (minE + widthM) / mLon,
            hotLat = hotLat,
            hotLon = hotLon,
        )
    }
}
