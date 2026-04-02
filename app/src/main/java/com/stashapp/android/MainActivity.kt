package com.stashapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stashapp.android.ui.screens.DashboardScreen
import com.stashapp.android.ui.screens.ItemDetailScreen
import com.stashapp.android.ui.theme.StashAppTheme
import com.stashapp.shared.data.InMemoryInventoryRepository

class MainActivity : ComponentActivity() {
    // Single instance for the prototype
    private val repository = InMemoryInventoryRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StashAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                repository = repository,
                                onNavigateToDetails = { itemId ->
                                    navController.navigate("details/$itemId")
                                }
                            )
                        }
                        composable("details/{itemId}") { backStackEntry ->
                            val itemId = backStackEntry.arguments?.getString("itemId")
                            ItemDetailScreen(
                                repository = repository,
                                itemId = itemId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
