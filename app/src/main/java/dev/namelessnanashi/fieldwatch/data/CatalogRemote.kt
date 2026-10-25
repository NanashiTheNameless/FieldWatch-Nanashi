package dev.namelessnanashi.fieldwatch.data

import dev.namelessnanashi.fieldwatch.domain.CatalogSource
import java.net.HttpURLConnection
import java.net.URL

/** HTTPS GET of a stock signature pack on GitHub. No account. Fails to the caller. */
object CatalogRemote {
    /** Built from this fork's DefaultCatalog; hosted on the separate Data-Assets release. */
    const val FORK_PACK_URL =
        "https://github.com/NanashiTheNameless/FieldWatch-Nanashi/releases/download/Data-Assets/fieldwatch-signatures-v2.json"

    /** Upstream Fieldwatch's published pack. */
    const val UPSTREAM_PACK_URL =
        "https://raw.githubusercontent.com/OffGridPete/Fieldwatch/main/dist/fieldwatch-signatures-v2.json"

    fun urlFor(source: CatalogSource): String = when (source) {
        CatalogSource.FORK -> FORK_PACK_URL
        CatalogSource.UPSTREAM -> UPSTREAM_PACK_URL
    }

    fun fetch(
        url: String,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000,
        userAgent: String = "Fieldwatch",
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("GitHub returned HTTP $code")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
