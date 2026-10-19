package com.brainpal.quitscrolling.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsKabaddi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brainpal.quitscrolling.core.haptics.LikhonHaptics
import com.brainpal.quitscrolling.di.AppContainer
import com.brainpal.quitscrolling.ui.screens.battle.BattleScreen
import com.brainpal.quitscrolling.ui.screens.battle.BattleViewModel
import com.brainpal.quitscrolling.ui.screens.home.HomeScreen
import com.brainpal.quitscrolling.ui.screens.home.HomeViewModel
import com.brainpal.quitscrolling.ui.screens.stats.StatsScreen
import com.brainpal.quitscrolling.ui.screens.stats.StatsViewModel
import com.brainpal.quitscrolling.ui.theme.AccentGold
import com.brainpal.quitscrolling.ui.theme.ButtonDarkText
import com.brainpal.quitscrolling.ui.theme.CardSurface
import com.brainpal.quitscrolling.ui.theme.ObsidianBackground
import com.brainpal.quitscrolling.ui.theme.TextSecondaryMuted

enum class BrainPalDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    YOU(
        route = "you",
        label = "You",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        testTag = "nav_tab_you"
    ),
    STATS(
        route = "stats",
        label = "Stats",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_tab_stats"
    ),
    BATTLE(
        route = "battle",
        label = "Battle",
        selectedIcon = Icons.Filled.SportsKabaddi,
        unselectedIcon = Icons.Outlined.SportsKabaddi,
        testTag = "nav_tab_battle"
    )
}

@Composable
fun BrainPalNavGraph(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(BrainPalDestination.YOU) }

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(
            scrollRepository = appContainer.scrollRepository,
            userPreferencesRepository = appContainer.userPreferencesRepository
        )
    )
    val statsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.provideFactory(
            scrollRepository = appContainer.scrollRepository
        )
    )
    val battleViewModel: BattleViewModel = viewModel(
        factory = BattleViewModel.provideFactory(
            scrollRepository = appContainer.scrollRepository,
            userPreferencesRepository = appContainer.userPreferencesRepository
        )
    )

    if (currentTab != BrainPalDestination.YOU) {
        BackHandler {
            LikhonHaptics.softTap(context)
            currentTab = BrainPalDestination.YOU
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = CardSurface,
                tonalElevation = 8.dp
            ) {
                BrainPalDestination.entries.forEach { destination ->
                    val selected = currentTab == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            LikhonHaptics.softTap(context)
                            currentTab = destination
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ButtonDarkText,
                            selectedTextColor = AccentGold,
                            indicatorColor = AccentGold,
                            unselectedIconColor = TextSecondaryMuted,
                            unselectedTextColor = TextSecondaryMuted
                        ),
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            BrainPalDestination.YOU -> {
                HomeScreen(
                    viewModel = homeViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            BrainPalDestination.STATS -> {
                StatsScreen(
                    viewModel = statsViewModel,
                    onBackClick = {
                        LikhonHaptics.softTap(context)
                        currentTab = BrainPalDestination.YOU
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            BrainPalDestination.BATTLE -> {
                BattleScreen(
                    viewModel = battleViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
