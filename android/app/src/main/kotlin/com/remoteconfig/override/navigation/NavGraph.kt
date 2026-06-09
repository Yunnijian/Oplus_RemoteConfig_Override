package com.remoteconfig.override.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.remoteconfig.override.ui.screens.ConfigEditorScreen
import com.remoteconfig.override.ui.screens.HomeContent
import com.remoteconfig.override.ui.screens.ConfigListContent
import com.remoteconfig.override.viewmodel.MainViewModel

object Routes {
    const val MAIN = "main"
    const val CONFIG_EDITOR = "config_editor/{packageName}"
    fun configEditor(packageName: String) = "config_editor/$packageName"
}

@Composable
private fun RowScope.AnimatedNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String
) {
    val iconOffset by animateDpAsState(
        targetValue = if (selected) (-4).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "iconOffset"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "labelAlpha"
    )
    val iconSize by animateDpAsState(
        targetValue = if (selected) 28.dp else 24.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "iconSize"
    )

    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(Modifier.offset(y = iconOffset), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = label,
                    modifier = Modifier.size(iconSize)
                )
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.alpha(labelAlpha)
            )
        },
        alwaysShowLabel = true
    )
}

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: MainViewModel
) {
    var currentTab by remember { mutableStateOf(0) } // 0=首页, 1=配置

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar {
                        AnimatedNavItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            selectedIcon = Icons.Filled.Home,
                            unselectedIcon = Icons.Outlined.Home,
                            label = "首页"
                        )
                        AnimatedNavItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            selectedIcon = Icons.Filled.List,
                            unselectedIcon = Icons.Outlined.List,
                            label = "配置"
                        )
                    }
                }
            ) { innerPadding ->
                AnimatedContent(
                    modifier = Modifier.padding(innerPadding),
                    targetState = currentTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
                        } else {
                            slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
                        }
                    },
                    label = "pageTransition"
                ) { tab ->
                    when (tab) {
                        0 -> HomeContent(
                            viewModel = viewModel,
                            isActive = tab == 0,
                            onNavigateConfig = { currentTab = 1 }
                        )
                        1 -> ConfigListContent(
                            viewModel = viewModel,
                            isActive = tab == 1,
                            onBack = { currentTab = 0 },
                            onGameClick = { packageName ->
                                viewModel.loadConfig(packageName)
                                navController.navigate(Routes.configEditor(packageName))
                            },
                            onNewConfig = { packageName ->
                                viewModel.createNewConfig(packageName)
                                navController.navigate(Routes.configEditor(packageName))
                            }
                        )
                    }
                }
            }
        }

        composable(
            route = Routes.CONFIG_EDITOR,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            ),
            enterTransition = {
                slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
            }
        ) {
            ConfigEditorScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.clearEditingConfig()
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                }
            )
        }
    }
}
