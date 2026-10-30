package dev.namelessnanashi.fieldwatch.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test

class HuntHeatRasterTest {
    private fun sample(e: Double, n: Double, rssi: Int = -60, at: Long = 1L) =
        GpsSample(at, 36.2 + n / 110_540.0, -115.08 + e / 89_828.0, rssi)

    @Test
    fun denseAndMaximumSizeTrailsStayInsideTheRealWorkBudget() = runBlocking {
        for (count in listOf(1, 30, 300, HuntHeat.CAP)) {
            val trail = List(count) { sample((it % 30) * 0.9, (it / 30) * 0.9, -85 + it % 35) }
            val field = HuntHeatRaster.render(trail)!!
            assertTrue("$count samples visited ${field.visitedCells}", field.visitedCells <= HuntHeatRaster.CELL_VISIT_BUDGET)
            assertTrue(field.strokeCount <= count * 10)
            assertTrue(field.pixels.any { it != 0 })
            assertEquals(field.cols * field.rows, field.pixels.size)
            assertTrue(field.north > field.south && field.east > field.west)
        }
    }

    @Test
    fun chronologicalLinesRemainAndFarGapsStayEmpty() = runBlocking {
        val e = doubleArrayOf(0.0, 8.0, 100.0, 108.0)
        val n = DoubleArray(4)
        val strokes = HuntHeatRaster.strokes(e, n)
        assertTrue(strokes.contains(HuntHeatRaster.Stroke(0, 1)))
        assertTrue(strokes.contains(HuntHeatRaster.Stroke(2, 3)))
        assertTrue(strokes.none { it.a <= 1 && it.b >= 2 })
        val field = HuntHeatRaster.render(e.map { sample(it, 0.0) })!!
        val middle = field.cols / 2
        assertTrue((0 until field.rows).all { field.pixels[it * field.cols + middle] == 0 })
    }

    @Test
    fun nearbyParallelPassesStillFillTheGroundBetweenThem() = runBlocking {
        val trail = listOf(sample(0.0, 0.0), sample(10.0, 0.0), sample(10.0, 8.0), sample(0.0, 8.0))
        val field = HuntHeatRaster.render(trail)!!
        val center = (field.rows / 2) * field.cols + field.cols / 2
        assertTrue(field.pixels[center] != 0)
        assertNotNull(field.hotLat)
        assertNotNull(field.hotLon)
    }

    @Test
    fun weakAndStrongEndsKeepTheirColors() = runBlocking {
        val field = HuntHeatRaster.render(listOf(sample(0.0, 0.0, -100), sample(20.0, 0.0, -50)))!!
        fun pixelAt(e: Double): Int {
            val col = ((e + HuntHeat.RIBBON_M) / field.cellM).toInt().coerceIn(0, field.cols - 1)
            return field.pixels[(field.rows / 2) * field.cols + col]
        }
        val weak = pixelAt(0.0)
        val strong = pixelAt(20.0)
        assertTrue((weak and 255) > ((weak ushr 16) and 255))
        assertTrue(((strong ushr 16) and 255) > (strong and 255))
    }

    @Test
    fun invalidAndUnmeasuredReadingsDoNotCreateAField() = runBlocking {
        assertNull(HuntHeatRaster.render(emptyList()))
        assertNull(HuntHeatRaster.render(listOf(sample(0.0, 0.0, 127))))
        assertNull(HuntHeatRaster.render(listOf(GpsSample(0L, Double.NaN, -115.0, -55))))
    }

    @Test
    fun leavingTheHuntCancelsRasterWork() = runBlocking {
        val job = Job().apply { cancel() }
        try {
            withContext(job) { HuntHeatRaster.render(List(1200) { sample(it.toDouble(), 0.0) }) }
            fail("cancelled raster completed")
        } catch (_: CancellationException) { }
    }

    @Test
    fun aCancelledSessionCannotPublishItsPendingField() = runBlocking {
        val updates = MutableSharedFlow<List<GpsSample>>()
        val entered = CompletableDeferred<Unit>()
        val published = mutableListOf<Long>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            HuntHeatRaster.collect<Long>(updates, render = {
                entered.complete(Unit)
                awaitCancellation()
            }, onField = { published.add(it) })
        }
        yield()
        updates.emit(listOf(sample(0.0, 0.0)))
        entered.await()
        job.cancelAndJoin()
        assertTrue(published.isEmpty())
    }

    @Test
    fun arrivingReadingsCannotStarveTheFirstFieldAndOnlyTheLatestWaits() = runBlocking {
        val updates = MutableSharedFlow<List<GpsSample>>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fields = mutableListOf<Long>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            HuntHeatRaster.collect(updates, render = { trail ->
                if (trail.single().at == 1L) { entered.complete(Unit); release.await() }
                trail.single().at
            }, onField = { fields.add(it) })
        }
        yield()
        updates.emit(listOf(sample(0.0, 0.0, at = 1L)))
        entered.await()
        for (at in 2L..100L) updates.emit(listOf(sample(0.0, 0.0, at = at)))
        release.complete(Unit)
        repeat(4) { yield() }
        assertEquals(listOf(1L, 100L), fields)
        job.cancelAndJoin()
    }
}
