package com.rork.tasktitan.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.components.BrandMark
import com.rork.tasktitan.ui.screens.CelebrationOverlay
import com.rork.tasktitan.ui.screens.ChoresScreen
import com.rork.tasktitan.ui.screens.ConsentScreen
import com.rork.tasktitan.ui.screens.HomeScreen
import com.rork.tasktitan.ui.screens.HouseBuilderScreen
import com.rork.tasktitan.ui.screens.HouseholdScreen
import com.rork.tasktitan.ui.screens.ProScreen
import com.rork.tasktitan.ui.screens.RecapScreen
import com.rork.tasktitan.ui.screens.RewardsScreen
import com.rork.tasktitan.ui.screens.SettingsScreen
import com.rork.tasktitan.ui.theme.TT

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home),
    Tab("chores", "Chores", Icons.Rounded.CheckBox),
    Tab("household", "Household", Icons.Rounded.Groups),
    Tab("rewards", "Rewards", Icons.Rounded.CardGiftcard),
)

@Composable
fun AppNavigation(vm: AppViewModel) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val celebration by vm.celebration.collectAsStateWithLifecycle()
    if (!loaded) {
        Box(Modifier.fillMaxSize().background(TT.c.canvas), contentAlignment = Alignment.Center) { BrandMark(72.dp) }
        return
    }
    val data = vm.data.value
    val start = remember {
        when {
            !data.settings.consentGiven -> "consent"
            !data.setupDone -> "setup"
            else -> "home"
        }
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    fun goTab(r: String) {
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = TT.c.canvas,
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = TT.c.surface, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { goTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TT.c.tangerine,
                                    selectedTextColor = TT.c.tangerine,
                                    indicatorColor = TT.c.tangerineSoft,
                                    unselectedIconColor = TT.c.muted,
                                    unselectedTextColor = TT.c.muted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { inner ->
            NavHost(
                navController = nav,
                startDestination = start,
                modifier = Modifier.padding(bottom = if (showBar) inner.calculateBottomPadding() else 0.dp),
            ) {
                composable("consent") {
                    ConsentScreen(vm) {
                        nav.navigate("setup") { popUpTo("consent") { inclusive = true } }
                    }
                }
                composable("setup") {
                    HouseBuilderScreen(vm, onboarding = true, onDone = {
                        nav.navigate("home") { popUpTo("setup") { inclusive = true } }
                    }, onClose = null)
                }
                composable("edit_house") {
                    HouseBuilderScreen(vm, onboarding = false, onDone = { nav.popBackStack() }, onClose = { nav.popBackStack() })
                }
                composable("home") {
                    HomeScreen(
                        vm,
                        onEditHouse = { nav.navigate("edit_house") },
                        onSettings = { nav.navigate("settings") },
                        onSeeChores = { goTab("chores") },
                        onRecap = { nav.navigate("recap") },
                    )
                }
                composable("chores") { ChoresScreen(vm) }
                composable("household") {
                    HouseholdScreen(vm, onRecap = { nav.navigate("recap") }, onSettings = { nav.navigate("settings") }, onPro = { nav.navigate("pro") })
                }
                composable("rewards") { RewardsScreen(vm, onPro = { nav.navigate("pro") }) }
                composable("settings") {
                    SettingsScreen(vm, onBack = { nav.popBackStack() }, onPro = { nav.navigate("pro") }, onEditHouse = { nav.navigate("edit_house") }, onDeleted = {
                        nav.navigate("consent") { popUpTo(0) { inclusive = true } }
                    })
                }
                composable("recap") { RecapScreen(vm, onBack = { nav.popBackStack() }) }
                composable("pro") { ProScreen(onBack = { nav.popBackStack() }) }
            }
        }
        celebration?.let { CelebrationOverlay(it) { vm.dismissCelebration() } }
    }
}

