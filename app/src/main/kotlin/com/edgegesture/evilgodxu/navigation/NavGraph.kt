package com.edgegesture.evilgodxu.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.edgegesture.evilgodxu.screens.backtap.BackTapScreen
import com.edgegesture.evilgodxu.screens.blacklist.AppBlacklistScreen
import com.edgegesture.evilgodxu.screens.expandpanel.ExpandPanelScreen
import com.edgegesture.evilgodxu.screens.gesture.EdgeGestureConfigScreen
import com.edgegesture.evilgodxu.screens.gesture.EdgeType
import com.edgegesture.evilgodxu.screens.gesture.GestureSettingsScreen
import com.edgegesture.evilgodxu.screens.launchblock.LaunchBlockScreen
import com.edgegesture.evilgodxu.screens.settings.DataConfigScreen
import com.edgegesture.evilgodxu.screens.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object GestureRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object DataConfigRoute : NavKey

@Serializable
data object BlacklistRoute : NavKey

@Serializable
data object LaunchBlockRoute : NavKey

@Serializable
data object BackTapRoute : NavKey

@Serializable
data object LeftEdgeConfigRoute : NavKey

@Serializable
data object RightEdgeConfigRoute : NavKey

@Serializable
data object BottomEdgeConfigRoute : NavKey

@Serializable
data object ExpandPanelRoute : NavKey

/** 封装返回栈导航操作，供各页面回调调用 */
class Navigator(private val backStack: NavBackStack<NavKey>) {

    fun navigate(route: NavKey) {
        backStack.add(route)
    }

    fun goBack() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    startDestination: GestureRoute = GestureRoute,
) {
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(startDestination)
    val navigator = remember { Navigator(backStack) }

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<GestureRoute> {
                GestureSettingsScreen(
                    onNavigateToSettings = { navigator.navigate(SettingsRoute) },
                    onNavigateToBlacklist = { navigator.navigate(BlacklistRoute) },
                    onNavigateToLaunchBlock = { navigator.navigate(LaunchBlockRoute) },
                    onNavigateToBackTap = { navigator.navigate(BackTapRoute) },
                    onNavigateToLeftEdge = { navigator.navigate(LeftEdgeConfigRoute) },
                    onNavigateToRightEdge = { navigator.navigate(RightEdgeConfigRoute) },
                    onNavigateToBottomEdge = { navigator.navigate(BottomEdgeConfigRoute) },
                    onNavigateToExpandPanel = { navigator.navigate(ExpandPanelRoute) }
                )
            }

            entry<SettingsRoute> {
                SettingsScreen(
                    onNavigateBack = { navigator.goBack() },
                    onNavigateToDataConfig = { navigator.navigate(DataConfigRoute) }
                )
            }

            entry<DataConfigRoute> {
                DataConfigScreen(
                    onNavigateBack = { navigator.goBack() }
                )
            }

            entry<BlacklistRoute> {
                AppBlacklistScreen(
                    onNavigateBack = { navigator.goBack() }
                )
            }

            entry<LaunchBlockRoute> {
                LaunchBlockScreen(
                    onNavigateBack = { navigator.goBack() }
                )
            }

            entry<BackTapRoute> {
                BackTapScreen(
                    onNavigateBack = { navigator.goBack() }
                )
            }

            entry<LeftEdgeConfigRoute> {
                EdgeGestureConfigScreen(
                    edgeType = EdgeType.LEFT,
                    onNavigateBack = { navigator.goBack() }
                )
            }

            entry<RightEdgeConfigRoute> {
                EdgeGestureConfigScreen(
                    edgeType = EdgeType.RIGHT,
                    onNavigateBack = { navigator.goBack() }
                )
            }

            entry<BottomEdgeConfigRoute> {
                EdgeGestureConfigScreen(
                    edgeType = EdgeType.BOTTOM,
                    onNavigateBack = { navigator.goBack() }
                )
            }

            entry<ExpandPanelRoute> {
                ExpandPanelScreen(
                    onNavigateBack = { navigator.goBack() }
                )
            }
        }
    )
}