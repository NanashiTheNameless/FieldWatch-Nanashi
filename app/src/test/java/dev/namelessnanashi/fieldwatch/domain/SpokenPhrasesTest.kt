package dev.namelessnanashi.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenPhrasesTest {
    @Test
    fun everyDistinctAlertIsSpokenInOrder() {
        assertEquals(
            listOf("Finder tag", "Drone", "Body camera"),
            groupSpokenPhrases(listOf("Finder tag", "Drone", "Body camera")),
        )
    }

    @Test
    fun repeatsAreSpokenOnceWithACount() {
        assertEquals(
            listOf("Apple AirTags, 3 of them", "Drone"),
            groupSpokenPhrases(listOf("Apple AirTags", "Drone", "Apple AirTags", "Apple AirTags")),
        )
    }

    @Test
    fun blankPhrasesAreDropped() {
        assertEquals(listOf("Drone"), groupSpokenPhrases(listOf(" ", "Drone ", "")))
        assertEquals(emptyList<String>(), groupSpokenPhrases(emptyList()))
    }
}
