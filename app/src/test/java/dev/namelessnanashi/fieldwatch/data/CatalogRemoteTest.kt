package dev.namelessnanashi.fieldwatch.data

import dev.namelessnanashi.fieldwatch.domain.AppSettings
import dev.namelessnanashi.fieldwatch.domain.CatalogFormat
import dev.namelessnanashi.fieldwatch.domain.CatalogSource
import dev.namelessnanashi.fieldwatch.domain.SettingsExchange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRemoteTest {
    @Test
    fun eachSourceAndFormatHasItsOwnFile() {
        val urls = CatalogSource.entries.flatMap { source ->
            CatalogFormat.entries.map { format -> CatalogRemote.urlFor(source, format) }
        }
        assertEquals(urls.size, urls.toSet().size)
        assertTrue(CatalogRemote.urlFor(CatalogSource.FORK, CatalogFormat.RSI).contains("/Data-Assets/fieldwatch-signatures.rsi"))
        assertTrue(CatalogRemote.urlFor(CatalogSource.FORK, CatalogFormat.LEGACY_JSON).contains("/Data-Assets/fieldwatch-signatures-v2.json"))
        assertTrue(CatalogRemote.urlFor(CatalogSource.UPSTREAM, CatalogFormat.RSI).endsWith("/dist/fieldwatch-signatures.rsi"))
        assertTrue(CatalogRemote.urlFor(CatalogSource.UPSTREAM, CatalogFormat.LEGACY_JSON).endsWith("/dist/fieldwatch-signatures-v2.json"))
    }

    @Test
    fun missingFormatInSavedSettingsIsRsi() {
        val settings = SettingsExchange.json.decodeFromString(AppSettings.serializer(), "{}")
        assertEquals(CatalogSource.FORK, settings.catalogSource)
        assertEquals(CatalogFormat.RSI, settings.catalogFormat)
    }
}
