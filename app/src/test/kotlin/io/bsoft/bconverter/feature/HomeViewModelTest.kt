package io.bsoft.bconverter.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test fun exposes_the_six_home_destinations_in_spec_order() {
        val routes = HomeViewModel().uiState.value.destinations.map { it.route }
        assertEquals(
            listOf("convert", "extract_audio", "editor", "projects", "settings", "about"),
            routes,
        )
    }
}
