package com.stashapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.stashapp.android.ui.screens.DashboardScreen
import com.stashapp.android.ui.screens.GroupListScreen
import com.stashapp.android.ui.screens.ItemDetailScreen
import com.stashapp.android.ui.theme.StashAppTheme
import com.stashapp.shared.data.SqlDelightInventoryRepository
import com.stashapp.shared.db.StashDatabase
import com.stashapp.shared.domain.Category
import com.stashapp.shared.domain.StorageLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: SqlDelightInventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val driver = AndroidSqliteDriver(StashDatabase.Schema, applicationContext, "stashapp.db")
        val database = StashDatabase(driver)
        repository = SqlDelightInventoryRepository(database)

        lifecycleScope.launch {
            if (repository.getStorageLocations().first().isEmpty()) {
                repository.addStorageLocation(StorageLocation(name = "Fridge", icon = "❄️"))
                repository.addStorageLocation(StorageLocation(name = "Freezer", icon = "🧊"))
                repository.addStorageLocation(StorageLocation(name = "Pantry", icon = "🥫"))
            }
            if (repository.getCategories().first().isEmpty()) {
                repository.addCategory(Category(name = "Dairy", icon = "🥛"))
                repository.addCategory(Category(name = "Produce", icon = "🍎"))
                repository.addCategory(Category(name = "Pantry Staples", icon = "🍚"))
            }
        }

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
                                },
                                onNavigateToGroup = { type, id ->
                                    navController.navigate("group/${type.name}/$id")
                                }
                            )
                        }
                        composable("group/{type}/{groupId}") { backStackEntry ->
                            val type = backStackEntry.arguments?.getString("type") ?: ""
                            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                            GroupListScreen(
                                repository = repository,
                                groupType = type,
                                groupId = groupId,
                                onNavigateBack = { navController.popBackStack() },
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
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToGroup = { type, id ->
                                    navController.navigate("group/$type/$id")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
