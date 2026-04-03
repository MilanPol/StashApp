package com.stashapp.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.shared.domain.InventoryEntry
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InventoryItemCard(
    entry: InventoryEntry,
    onUpdate: (InventoryEntry) -> Unit,
    onDelete: () -> Unit,
    onDetailsClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showConsumeDialog by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotation")
    val isExpired = entry.expirationDate?.isExpired(LocalDate.now()) == true

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${entry.quantity.amount} ${entry.quantity.unit.translated().lowercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isExpired) {
                    Text(stringResource(R.string.label_expired), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                } else if (entry.isOpen) {
                    Text(stringResource(R.string.label_opened), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.expand),
                    modifier = Modifier.rotate(rotation)
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    
                    if (entry.expirationDate != null) {
                        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        Text(
                            stringResource(R.string.expires_on, entry.expirationDate?.date?.format(formatter) ?: ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDetailsClick) { Text(stringResource(R.string.action_view_details)) }
                            
                            Row {
                                if (!entry.isOpen) {
                                    OutlinedButton(onClick = { onUpdate(entry.open(Instant.now())) }) {
                                        Text(stringResource(R.string.action_open))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Button(
                                    onClick = { showConsumeDialog = true }
                                ) {
                                    Text(stringResource(R.string.action_consume))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showConsumeDialog) {
        ConsumeDialog(
            entry = entry,
            onDismiss = { showConsumeDialog = false },
            onConsumeFull = {
                onDelete()
                showConsumeDialog = false
            },
            onConsumePartial = { amount ->
                val updatedEntry = entry.consume(amount, Instant.now())
                if (updatedEntry.quantity.amount <= BigDecimal.ZERO) {
                    onDelete()
                } else {
                    onUpdate(updatedEntry)
                }
                showConsumeDialog = false
            }
        )
    }
}

@Composable
fun ConsumeDialog(
    entry: InventoryEntry,
    onDismiss: () -> Unit,
    onConsumeFull: () -> Unit,
    onConsumePartial: (BigDecimal) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_consume_title, entry.name)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_consume_description, entry.quantity.amount.toString(), entry.quantity.unit.translated().lowercase()))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.label_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (amount > BigDecimal.ZERO) {
                    onConsumePartial(amount)
                }
            }) {
                Text(stringResource(R.string.action_consume))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onConsumeFull,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_consume_entirely))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}
