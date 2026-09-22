package com.aditya.splitup.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.aditya.splitup.ui.screens.create.CreateGroupScreen
import com.aditya.splitup.ui.screens.create.CreateGroupViewModel
import com.aditya.splitup.ui.screens.group.GroupDetailScreen
import com.aditya.splitup.ui.screens.group.GroupViewModel
import com.aditya.splitup.ui.screens.group.GroupViewModelFactory
import com.aditya.splitup.ui.screens.home.HomeScreen
import com.aditya.splitup.ui.screens.home.HomeViewModel
import com.aditya.splitup.ui.screens.join.JoinGroupScreen
import com.aditya.splitup.ui.screens.join.JoinGroupViewModel
import com.aditya.splitup.ui.screens.settings.GroupSettingsScreen
import com.aditya.splitup.ui.screens.settings.GroupSettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class GroupRoute(val groupId: Long)

@Serializable
data class GroupSettingsRoute(val groupId: Long)

@Serializable
object CreateGroupRoute

@Serializable
object JoinGroupRoute

@Composable
fun SplitTrackerNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext as android.app.Application

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(350)
            ) + fadeIn(animationSpec = tween(350))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(350)
            ) + fadeOut(animationSpec = tween(350))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(350)
            ) + fadeIn(animationSpec = tween(350))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(350)
            ) + fadeOut(animationSpec = tween(350))
        }
    ) {
        composable<HomeRoute> {
            val viewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToGroup = { groupId ->
                    navController.navigate(GroupRoute(groupId))
                },
                onNavigateToCreateGroup = {
                    navController.navigate(CreateGroupRoute)
                },
                onNavigateToJoinGroup = {
                    navController.navigate(JoinGroupRoute)
                }
            )
        }

        composable<GroupRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GroupRoute>()
            val viewModel: GroupViewModel = viewModel(
                factory = GroupViewModelFactory(context, route.groupId)
            )
            GroupDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { groupId ->
                    navController.navigate(GroupSettingsRoute(groupId))
                }
            )
        }

        composable<GroupSettingsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GroupSettingsRoute>()
            val viewModel: GroupSettingsViewModel = viewModel()
            LaunchedEffect(route.groupId) {
                viewModel.loadGroup(route.groupId)
            }
            GroupSettingsScreen(
                groupId = route.groupId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<CreateGroupRoute> {
            val viewModel: CreateGroupViewModel = viewModel()
            CreateGroupScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { groupId ->
                    navController.popBackStack()
                    navController.navigate(GroupRoute(groupId))
                }
            )
        }

        composable<JoinGroupRoute> {
            val viewModel: JoinGroupViewModel = viewModel()
            JoinGroupScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onGroupJoined = { groupId ->
                    navController.popBackStack()
                    navController.navigate(GroupRoute(groupId))
                }
            )
        }
    }
}
