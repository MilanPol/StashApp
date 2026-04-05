package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.shared.domain.IngredientMatch
import com.stashapp.shared.domain.MatchStatus
import com.stashapp.android.ui.components.translated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookSessionScreen(
    viewModel: CookSessionViewModel,
    onNavigateBack: () -> Unit
) {
    val recipe by viewModel.recipe.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val multiplier by viewModel.servingMultiplier.collectAsState()
    val cookingDone by viewModel.cookingDone.collectAsState()

    if (cookingDone) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            title = { Text(stringResource(R.string.cook_done_title)) },
            text = { Text(stringResource(R.string.cook_done_message)) },
            confirmButton = {
                Button(onClick = onNavigateBack) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val missingCount = matches.count { it.status == MatchStatus.MISSING }
                    if (missingCount > 0) {
                        Text(
                            text = stringResource(R.string.cook_warning_missing, missingCount),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Button(
                        onClick = { viewModel.confirmDeductions() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = recipe != null && matches.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.cook_deduct_confirm))
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.recipe_servings_hint), style = MaterialTheme.typography.titleMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = ( (recipe?.servings ?: 4) * multiplier ).toInt().toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = multiplier.toFloat(),
                                onValueChange = { viewModel.setServingMultiplier(it.toDouble()) },
                                valueRange = 0.25f..4f,
                                steps = 14, // 0.25 steps from 0.25 to 4
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                Text(stringResource(R.string.cook_review_title), style = MaterialTheme.typography.titleLarge)
            }

            items(matches) { match ->
                IngredientMatchItem(match, multiplier)
            }
        }
    }
}

@Composable
fun IngredientMatchItem(match: IngredientMatch, multiplier: Double) {
    val ingredient = match.recipeIngredient
    val requiredAmount = (ingredient.quantity ?: 0.0) * multiplier
    
    val (icon, color, label) = when (match.status) {
        MatchStatus.AVAILABLE -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), stringResource(R.string.cook_status_available))
        MatchStatus.LOW -> Triple(Icons.Default.Warning, Color(0xFFFF9800), stringResource(R.string.cook_status_low))
        MatchStatus.MISSING -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, stringResource(R.string.cook_status_missing))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(ingredient.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (ingredient.quantity != null) {
                Text(
                    text = "$requiredAmount ${ingredient.unit.translated().lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            if (match.status == MatchStatus.LOW) {
                Text(
                    text = stringResource(R.string.cook_available_quantity, match.availableQuantity.toString()),
                    style = MaterialTheme.typography.labelExtraSmall,
                    color = color
                )
            }
        }
    }
}

// Extension property for layout
private val Typography.labelExtraSmall: TextStyle @Composable get() = labelSmall.copy(fontSize = labelSmall.fontSize * 0.8)
