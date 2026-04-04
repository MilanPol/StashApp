package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.android.data.SettingsManager
import com.stashapp.shared.domain.CategoryRepository
import com.stashapp.shared.domain.Category
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    categoryRepository: CategoryRepository,
    onNavigateBack: () -> Unit
) {
    val globalLeadDays by settingsManager.globalLeadDays.collectAsState(initial = 2)
    val categories by categoryRepository.getCategories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Global Settings
            item {
                Column {
                    Text(
                        text = stringResource(R.string.label_global_lead_days),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_notification_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    LeadDaysPicker(
                        currentDays = globalLeadDays,
                        onDaysChanged = { scope.launch { settingsManager.setGlobalLeadDays(it) } }
                    )
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_category_overrides),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(categories) { category ->
                CategoryLeadDaysItem(
                    category = category,
                    globalLeadDays = globalLeadDays,
                    onUpdate = { newDays ->
                        scope.launch {
                            categoryRepository.updateCategory(category.copy(defaultLeadDays = newDays))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LeadDaysPicker(
    currentDays: Int,
    onDaysChanged: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FilledTonalButton(
            onClick = { if (currentDays > 0) onDaysChanged(currentDays - 1) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Text("-")
        }
        
        Text(
            text = "$currentDays ${stringResource(R.string.label_days_before)}",
            style = MaterialTheme.typography.headlineSmall
        )

        FilledTonalButton(
            onClick = { onDaysChanged(currentDays + 1) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Text("+")
        }
    }
}

@Composable
fun CategoryLeadDaysItem(
    category: Category,
    globalLeadDays: Int,
    onUpdate: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val effectiveDays = category.defaultLeadDays ?: globalLeadDays

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "${category.icon} ${category.name}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (category.defaultLeadDays == null) stringResource(R.string.settings_using_global_default, globalLeadDays) else stringResource(R.string.settings_overridden_days, effectiveDays),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Switch(
                    checked = category.defaultLeadDays != null,
                    onCheckedChange = { isOverridden ->
                        if (isOverridden) onUpdate(globalLeadDays)
                        else onUpdate(null)
                    }
                )
            }

            if (category.defaultLeadDays != null) {
                Spacer(Modifier.height(8.dp))
                LeadDaysPicker(
                    currentDays = category.defaultLeadDays!!,
                    onDaysChanged = { onUpdate(it) }
                )
            }
        }
    }
}
