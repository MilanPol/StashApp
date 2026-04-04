package com.stashapp.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.android.ui.components.InventoryItemCard
import com.stashapp.shared.domain.InventoryEntry
import com.stashapp.shared.domain.InventoryEntryRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiringItemsDialog(
    entries: List<InventoryEntry>,
    repository: InventoryEntryRepository,
    onDismiss: () -> Unit,
    onNavigateToDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
        title = {
            Text(stringResource(R.string.dialog_expiring_items_title))
        },
        text = {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.no_items_found))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        InventoryItemCard(
                            entry = entry,
                            onUpdate = { scope.launch { repository.updateEntry(it) } },
                            onDelete = { scope.launch { repository.removeEntry(entry.id) } },
                            onDetailsClick = { 
                                onNavigateToDetails(entry.id)
                                onDismiss()
                            },
                            showActions = false,
                            onCardClick = {
                                onNavigateToDetails(entry.id)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    )
}
