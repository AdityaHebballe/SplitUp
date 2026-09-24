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
import androidx.navigation.NavHostController
import androidx.navigation.navDeepLink
import androidx.lifecycle.Lifecycle
import kotlinx.serialization.Serializable

/**
 * Google-recommended safe navigation extension:
 * 1. Checks that the current destination is fully RESUMED before allowing navigation,
 *    preventing rapid double-tap race conditions from pushing duplicate destinations.
 * 2. Uses `launchSingleTop = true` to guarantee at most one instance of a destination on the back stack.
 */
fun NavHostController.navigateSafely(route: Any) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route) {
            launchSingleTop = true
        }
    }
}

@Serializable
object HomeRoute

@Serializable
data class GroupRoute(val groupId: Long)

@Serializable
data class GroupSettingsRoute(val groupId: Long)

@Serializable
object CreateGroupRoute

@Serializable
data class JoinGroupRoute(val code: String? = null)

@Composable
fun SplitTrackerNavGraph(navController: NavHostController = rememberNavController()) {
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
                    navController.navigateSafely(GroupRoute(groupId))
                },
                onNavigateToCreateGroup = {
                    navController.navigateSafely(CreateGroupRoute)
                },
                onNavigateToJoinGroup = {
                    navController.navigateSafely(JoinGroupRoute())
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
                    navController.navigateSafely(GroupSettingsRoute(groupId))
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
                onNavigateBack = { navController.popBackStack() },
                onGroupDeleted = {
                    navController.popBackStack<HomeRoute>(inclusive = false)
                }
            )
        }

        composable<CreateGroupRoute> {
            val viewModel: CreateGroupViewModel = viewModel()
            CreateGroupScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { groupId ->
                    navController.navigate(GroupRoute(groupId)) {
                        popUpTo<CreateGroupRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<JoinGroupRoute>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "splitup://join/{code}" },
                navDeepLink { uriPattern = "splitup://join?code={code}" },
                navDeepLink { uriPattern = "splitup://join" },
                navDeepLink { uriPattern = "https://splitup-e3d86.web.app/join/{code}" },
                navDeepLink { uriPattern = "https://splitup-e3d86.web.app/join?code={code}" },
                navDeepLink { uriPattern = "https://splitup-e3d86.web.app/join" },
                navDeepLink { uriPattern = "https://splitup-e3d86.firebaseapp.com/join/{code}" },
                navDeepLink { uriPattern = "https://splitup-e3d86.firebaseapp.com/join?code={code}" },
                navDeepLink { uriPattern = "https://adityahebballe.github.io/SplitUp/join/{code}" },
                navDeepLink { uriPattern = "https://adityahebballe.github.io/SplitUp/join?code={code}" }
            )
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<JoinGroupRoute>()
            val viewModel: JoinGroupViewModel = viewModel()
            JoinGroupScreen(
                viewModel = viewModel,
                initialCode = route.code,
                onNavigateBack = { navController.popBackStack() },
                onGroupJoined = { groupId ->
                    navController.navigate(GroupRoute(groupId)) {
                        popUpTo<JoinGroupRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
