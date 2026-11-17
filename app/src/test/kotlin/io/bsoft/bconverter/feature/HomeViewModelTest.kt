package io.bsoft.bconverter.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test fun exposes_the_home_tiles_without_settings_and_fonts() {
        val routes = HomeViewModel().uiState.value.destinations.map { it.route }
        assertEquals(
            listOf("convert", "extract_audio", "extract_subtitles", "editor", "projects", "about"),
            routes,
        )
    }
}
