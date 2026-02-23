package com.smartcheck.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartcheck.app.ui.screens.AdminLoginScreen
import com.smartcheck.app.ui.screens.AdminScreen
import com.smartcheck.app.ui.screens.EmployeeEnrollScreen
import com.smartcheck.app.ui.screens.FaceTestScreen
import com.smartcheck.app.ui.screens.HandCheckScreen
import com.smartcheck.app.ui.screens.HandTestScreen
import com.smartcheck.app.ui.screens.HomeScreen
import com.smartcheck.app.viewmodel.AdminAuthViewModel

/**
 * 应用导航配置
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "hand_check"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateAdmin = { navController.navigate("admin") }
            )
        }
        
        composable("hand_check") {
            HandCheckScreen()
        }
        
        composable("hand_test") {
            HandTestScreen()
        }

        composable("face_test") {
            FaceTestScreen(
                onNavigateHome = { navController.navigate("home") }
            )
        }
        
        composable("admin") {
            val authViewModel: AdminAuthViewModel = hiltViewModel()
            val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

            if (isLoggedIn) {
                AdminScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = { authViewModel.logout() },
                    onNavigateEmployeeEnroll = { navController.navigate("employee_enroll") }
                )
            } else {
                AdminLoginScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = authViewModel
                )
            }
        }

        composable("employee_enroll") {
            val authViewModel: AdminAuthViewModel = hiltViewModel()
            val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

            if (isLoggedIn) {
                EmployeeEnrollScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                AdminLoginScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = authViewModel
                )
            }
        }
    }
}
