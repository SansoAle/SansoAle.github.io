package it.sansoale.uscite.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import it.sansoale.uscite.data.settings.AppSettings
import it.sansoale.uscite.ui.detail.TripDetailScreen
import it.sansoale.uscite.ui.edit.TripEditScreen
import it.sansoale.uscite.ui.gallery.GalleryScreen
import it.sansoale.uscite.ui.globalmap.GlobalMapScreen
import it.sansoale.uscite.ui.home.HomeScreen
import it.sansoale.uscite.ui.settings.SettingsScreen
import it.sansoale.uscite.ui.stats.StatsScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object HomeRoute
@Serializable object GlobalMapRoute
@Serializable object StatsRoute
@Serializable object SettingsRoute

@Serializable data class DetailRoute(val tripId: Long)

/** Creazione (tripId = 0) o modifica di un'uscita; [importUri] è un GPX da importare subito. */
@Serializable data class EditRoute(val tripId: Long = 0, val importUri: String? = null)

@Serializable data class GalleryRoute(val tripId: Long, val index: Int = 0)

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(HomeRoute, HomeRoute::class, "Uscite", Icons.AutoMirrored.Filled.List),
    TopLevel(GlobalMapRoute, GlobalMapRoute::class, "Mappa", Icons.Filled.Map),
    TopLevel(StatsRoute, StatsRoute::class, "Statistiche", Icons.Filled.BarChart),
    TopLevel(SettingsRoute, SettingsRoute::class, "Impostazioni", Icons.Filled.Settings),
)

@Composable
fun AppNavHost(
    settings: AppSettings,
    pendingImport: String?,
    onImportConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = topLevel.any { tl -> destination?.hierarchy?.any { it.hasRoute(tl.routeClass) } == true }

    LaunchedEffect(pendingImport) {
        if (pendingImport != null) {
            navController.navigate(EditRoute(importUri = pendingImport))
            onImportConsumed()
        }
    }

    Scaffold(
        // Gli insets di sistema sono gestiti dalle singole schermate (ognuna ha la sua top bar).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    units = settings.unitSystem,
                    onTripClick = { navController.navigate(DetailRoute(it)) },
                    onNewTrip = { navController.navigate(EditRoute()) },
                )
            }
            composable<GlobalMapRoute> {
                GlobalMapScreen(
                    settings = settings,
                    onTripClick = { navController.navigate(DetailRoute(it)) },
                )
            }
            composable<StatsRoute> {
                StatsScreen(
                    units = settings.unitSystem,
                    onTripClick = { navController.navigate(DetailRoute(it)) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<DetailRoute> {
                TripDetailScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(EditRoute(tripId = id)) },
                    onOpenPhoto = { tripId, index -> navController.navigate(GalleryRoute(tripId, index)) },
                )
            }
            composable<EditRoute> {
                TripEditScreen(
                    units = settings.unitSystem,
                    onClose = { navController.popBackStack() },
                    onSaved = { id, isNew ->
                        navController.popBackStack()
                        if (isNew) navController.navigate(DetailRoute(id))
                    },
                )
            }
            composable<GalleryRoute> {
                GalleryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
