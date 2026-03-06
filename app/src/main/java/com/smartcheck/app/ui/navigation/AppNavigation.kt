package com.smartcheck.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.smartcheck.app.ui.screens.AdminLoginScreen
import com.smartcheck.app.ui.screens.AdminScreen
import com.smartcheck.app.ui.screens.DashboardScreen
import com.smartcheck.app.ui.screens.EmployeeDetailScreen
import com.smartcheck.app.ui.screens.EmployeeEnrollScreen
import com.smartcheck.app.ui.screens.EmployeeListScreen
import com.smartcheck.app.ui.screens.EmployeeCloudImportScreen
import com.smartcheck.app.ui.screens.HomeScreen
import com.smartcheck.app.ui.screens.ReportExportScreen
import com.smartcheck.app.ui.screens.SettingsScreen
import com.smartcheck.app.ui.screens.RecordDetailScreen
import com.smartcheck.app.viewmodel.AdminAuthViewModel

/**
 * 应用导航配置
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            val authViewModel: AdminAuthViewModel = hiltViewModel()
            AdminLoginScreen(
                onNavigateBack = { },
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        composable("dashboard") {
            DashboardScreen(
                onNavigateCheck = { navController.navigate("check") },
                onNavigateEmployees = { navController.navigate("employees") },
                onNavigateRecords = { navController.navigate("records") },
                onNavigateExport = { navController.navigate("export") },
                onNavigateSettings = { navController.navigate("settings") }
            )
        }

        composable("check") {
            HomeScreen(
                onNavigateAdmin = { navController.navigate("settings") },
                onNavigateBackToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("check") { inclusive = true }
                    }
                }
            )
        }

        composable("employees") {
            EmployeeListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateEmployeeDetail = { employeeId ->
                    navController.navigate("employee_detail/$employeeId")
                },
                onNavigateEmployeeNew = { navController.navigate("employee_new") },
                onNavigateCloudImport = { navController.navigate("employee_cloud_import") }
            )
        }

        composable("employee_cloud_import") {
            EmployeeCloudImportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "employee_detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) {
            EmployeeDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("employee_new") {
            EmployeeDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("records") {
            AdminScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateExport = { navController.navigate("export") },
                onNavigateRecordDetail = { recordId ->
                    navController.navigate("record_detail/$recordId")
                }
            )
        }

        composable(
            route = "record_detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) {
            RecordDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("export") {
            ReportExportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            val authViewModel: AdminAuthViewModel = hiltViewModel()
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }
    }
}
