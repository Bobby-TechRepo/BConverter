package io.bsoft.bconverter.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.bsoft.bconverter.feature.audioextractor.AudioExtractorScreen
import io.bsoft.bconverter.feature.converter.ConverterScreen
import io.bsoft.bconverter.feature.home.HomeDestination
import io.bsoft.bconverter.feature.home.HomeScreen
import io.bsoft.bconverter.feature.home.HomeViewModel
import io.bsoft.bconverter.feature.placeholder.PlaceholderScreen

private const val HOME_ROUTE = "home"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            val vm: HomeViewModel = hiltViewModel()
            HomeScreen(
                state = vm.uiState.collectAsState().value,
                onDestinationClick = { navController.navigate(it.route) },
            )
        }
        HomeDestination.entries.forEach { d ->
            composable(d.route) {
                val back: () -> Unit = { navController.popBackStack() }
                when (d) {
                    HomeDestination.CONVERT_VIDEO -> ConverterScreen(back)
                    HomeDestination.EXTRACT_AUDIO -> AudioExtractorScreen(back)
                    else -> PlaceholderScreen(d.titleRes, back)
                }
            }
        }
    }
}
