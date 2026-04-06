package com.stashapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import com.stashapp.android.data.SettingsManager
import com.stashapp.android.ui.screens.*
import com.stashapp.android.ui.theme.StashAppTheme
import com.stashapp.shared.data.SqlDelightInventoryRepository
import com.stashapp.shared.data.SqlDelightRecipeRepository
import com.stashapp.shared.domain.Category
import com.stashapp.shared.domain.StorageLocation
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.stashapp.android.worker.CatalogImportWorker
import com.stashapp.android.worker.DailyExpiryWorker
import com.stashapp.shared.util.IngestCatalog
import com.stashapp.shared.domain.*
import com.stashapp.shared.domain.usecase.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var repository: SqlDelightInventoryRepository
    private lateinit var recipeRepository: SqlDelightRecipeRepository
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as StashApp
        repository = app.repository
        recipeRepository = app.recipeRepository
        settingsManager = SettingsManager(applicationContext)

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

        scheduleDailyExpiryWorker()

        setContent {
            StashAppTheme {
                val isCatalogImported by settingsManager.isCatalogImported.collectAsState(initial = true)
                var ingestionProgress by remember { mutableStateOf(0f) }

                // Observe Background Import Progress
                val workManager = WorkManager.getInstance(applicationContext)
                val importWorkInfo by workManager.getWorkInfosForUniqueWorkLiveData("catalog_import")
                    .observeAsState(initial = emptyList())
                
                val currentImport = importWorkInfo.firstOrNull()
                val isWorkerRunning = currentImport?.state == WorkInfo.State.RUNNING || currentImport?.state == WorkInfo.State.ENQUEUED
                val workerProgress = currentImport?.progress?.getFloat("progress", 0f) ?: 0f

                LaunchedEffect(isCatalogImported) {
                    if (!isCatalogImported) {
                        val request = OneTimeWorkRequestBuilder<CatalogImportWorker>()
                            .addTag("catalog_import")
                            .build()
                        workManager.enqueueUniqueWork("catalog_import", androidx.work.ExistingWorkPolicy.KEEP, request)
                    }
                }

                // Smoothly update progress
                LaunchedEffect(workerProgress) {
                    ingestionProgress = workerProgress
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isWorkerRunning) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_hamster),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                CircularProgressIndicator(progress = ingestionProgress)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Loading Product Catalog... ${(ingestionProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        val navController = rememberNavController()
                        val globalLeadDays by settingsManager.globalLeadDays.collectAsState(initial = 2)

                        val intentScreen = intent.getStringExtra("SCREEN")
                        LaunchedEffect(intentScreen) {
                            if (intentScreen == "expiring_soon") {
                                navController.navigate("expiring_soon")
                            }
                        }

                        NavHost(navController = navController, startDestination = "dashboard") {
                            composable("dashboard") {
                                val dashboardViewModel: DashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = DashboardViewModel.Factory(
                                        entryRepository = repository,
                                        locationRepository = repository,
                                        categoryRepository = repository,
                                        catalogRepository = repository,
                                        settingsManager = settingsManager
                                    )
                                )
                                DashboardScreen(
                                    viewModel = dashboardViewModel,
                                    onNavigateToDetails = { itemId ->
                                        navController.navigate("details/$itemId")
                                    },
                                    onNavigateToGroup = { type, id ->
                                        navController.navigate("group/${type.name}/$id")
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    },
                                    onNavigateToRecipes = {
                                        navController.navigate("recipes")
                                    },
                                    onNavigateToShoppingList = {
                                        navController.navigate("shopping_list")
                                    }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    settingsManager = settingsManager,
                                    categoryRepository = repository,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("group/{type}/{groupId}") { backStackEntry ->
                                val type = backStackEntry.arguments?.getString("type") ?: ""
                                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                                GroupListScreen(
                                    entryRepository = repository,
                                    locationRepository = repository,
                                    categoryRepository = repository,
                                    catalogRepository = repository,
                                    groupType = type,
                                    groupId = groupId,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetails = { itemId ->
                                        navController.navigate("details/$itemId")
                                    }
                                )
                            }
                            composable("details/{itemId}") { backStackEntry ->
                                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                                val detailViewModel: ItemDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = ItemDetailViewModel.Factory(
                                        entryRepository = repository,
                                        locationRepository = repository,
                                        categoryRepository = repository,
                                        catalogRepository = repository,
                                        itemId = itemId
                                    )
                                )
                                ItemDetailScreen(
                                    viewModel = detailViewModel,
                                    globalLeadDays = globalLeadDays,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToGroup = { type, id ->
                                        navController.navigate("group/$type/$id")
                                    }
                                )
                            }
                            composable("expiring_soon") {
                                ExpiringItemsScreen(
                                    entryRepository = repository,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetails = { itemId ->
                                        navController.navigate("details/$itemId")
                                    }
                                )
                            }
                            composable("recipes") {
                                val recipeListViewModel: RecipeListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = RecipeListViewModel.Factory(
                                        recipeRepository = recipeRepository
                                    )
                                )
                                RecipeListScreen(
                                    viewModel = recipeListViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToRecipe = { recipeId ->
                                        navController.navigate("edit_recipe/$recipeId")
                                    },
                                    onNavigateToNewRecipe = {
                                        navController.navigate("new_recipe")
                                    },
                                    onCookRecipe = { recipeId ->
                                        navController.navigate("cook/$recipeId")
                                    }
                                )
                            }
                            composable("new_recipe") {
                                AddEditRecipeScreen(
                                    recipeRepository = recipeRepository,
                                    inventoryRepository = repository,
                                    recipeId = null,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("edit_recipe/{recipeId}") { backStackEntry ->
                                val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
                                AddEditRecipeScreen(
                                    recipeRepository = recipeRepository,
                                    inventoryRepository = repository,
                                    recipeId = recipeId,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("cook/{recipeId}") { backStackEntry ->
                                val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
                                val cookViewModel: CookSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = CookSessionViewModel.Factory(
                                        recipeRepository = recipeRepository,
                                        entryRepository = repository,
                                        recipeId = recipeId
                                    )
                                )
                                CookSessionScreen(
                                    viewModel = cookViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("shopping_list") {
                                val shoppingViewModel: ShoppingListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = ShoppingListViewModel.Factory(
                                        repository = repository
                                    )
                                )
                                ShoppingListScreen(
                                    viewModel = shoppingViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onRestockRequested = { isManual ->
                                        if (isManual) {
                                            lifecycleScope.launch {
                                                val purchasedItems = repository.getPurchasedItems().first()
                                                val restockItems = purchasedItems.map { shopItem ->
                                                    RestockItem(
                                                        sessionId = "", // Repo handles
                                                        name = shopItem.name,
                                                        quantity = shopItem.quantity ?: Quantity(java.math.BigDecimal.ONE, MeasurementUnit.PIECES),
                                                        shoppingListItemId = shopItem.id,
                                                        catalogEan = shopItem.catalogEan
                                                    )
                                                }
                                                val sessionId = repository.createSession(restockItems)
                                                withContext(Dispatchers.Main) {
                                                    navController.navigate("restock_dashboard/$sessionId")
                                                }
                                            }
                                        } else {
                                            navController.navigate("receipt_scan")
                                        }
                                    }
                                )
                            }
                            composable("receipt_scan") {
                                val scanViewModel: ReceiptScanViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = ReceiptScanViewModel.Factory(
                                        matchReceiptUseCase = com.stashapp.shared.domain.usecase.MatchReceiptUseCase(repository, repository)
                                    )
                                )
                                ReceiptScanScreen(
                                    viewModel = scanViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onConfirmScan = { result: ReceiptScanResult ->
                                        lifecycleScope.launch {
                                            val items = result.matches.map { match: ReceiptMatch ->
                                                RestockItem(
                                                    sessionId = "", // Placeholder, repo handles actual linking
                                                    name = match.matchedCatalogProduct?.name ?: match.receiptLine.name,
                                                    quantity = match.receiptLine.quantity,
                                                    shoppingListItemId = match.matchedShoppingItem?.id,
                                                    catalogEan = match.matchedCatalogProduct?.ean
                                                )
                                            }
                                            val sessionId = repository.createSession(items)
                                            withContext(Dispatchers.Main) {
                                                navController.navigate("restock_dashboard/$sessionId")
                                            }
                                        }
                                    }
                                )
                            }
                            composable("restock_dashboard/{sessionId}") { backStackEntry ->
                                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                                val restockViewModel: RestockDashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = RestockDashboardViewModel.Factory(
                                        restockRepository = repository,
                                        locationRepository = repository,
                                        entryRepository = repository,
                                        catalogRepository = repository,
                                        sessionId = sessionId
                                    )
                                )
                                RestockDashboardScreen(
                                    viewModel = restockViewModel,
                                    onNavigateBack = { navController.navigate("dashboard") { 
                                        popUpTo("dashboard") { inclusive = true } 
                                    } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleDailyExpiryWorker() {
        val request = PeriodicWorkRequestBuilder<DailyExpiryWorker>(1, TimeUnit.DAYS)
            .addTag("daily_expiry_check")
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "daily_expiry_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
