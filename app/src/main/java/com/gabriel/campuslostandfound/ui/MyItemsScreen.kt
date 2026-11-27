package com.gabriel.campuslostandfound.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gabriel.campuslostandfound.ItemSortOrder
import com.gabriel.campuslostandfound.state.MyItemsUiState

@Composable
internal fun MyItemsScreen(
    state: MyItemsUiState,
    onSignOut: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSort: (ItemSortOrder) -> Unit,
    onRequestDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("My Items", style = MaterialTheme.typography.headlineMedium) }
        item { TextButton(onClick = onSignOut, enabled = !state.deleting) { Text("Sign Out") } }
        item { Button(onClick = onAddItem, enabled = !state.deleting) { Text("Add Item") } }
        item {
            Box {
                TextButton(
                    onClick = { sortMenuOpen = true },
                    enabled = !state.deleting
                ) {
                    Text("Sort: ${state.sortOrder.label}")
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false }
                ) {
                    ItemSortOrder.values().forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            onClick = {
                                onSort(order)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                label = { Text("Search by title or location") },
                singleLine = true,
                enabled = !state.deleting,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            TextButton(
                onClick = { onSearch("") },
                enabled = state.searchQuery.isNotEmpty() && !state.deleting
            ) { Text("Clear") }
        }

        if (state.deleteFailed) {
            item {
                Text(
                    "Could not delete the item. Please try again.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        when {
            state.loadFailed -> item {
                Text(
                    "Could not load your items. Sign out and back in to retry.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.isLoading -> item { CircularProgressIndicator() }
            !state.hasItems -> item { Text("No items reported yet. Use Add Item to report your first item.") }
            state.visibleItems.isEmpty() -> item { Text("No items match your search.") }
            else -> items(state.visibleItems, key = { it.id }) { item ->
                Column {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable(
                            enabled = item.canEdit && !state.deleting,
                            onClick = { onEditItem(item.id) }
                        )
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text("${item.status} · ${item.location}")

                        val reviewLabel = when (item.reviewStatus) {
                            "PENDING" -> "Pending"
                            "CONFIRMED" -> "Confirmed"
                            "REJECTED" -> "Rejected"
                            else -> "Unknown"
                        }
                        Text("Review: $reviewLabel")
                    }

                    if (item.canEdit) {
                        TextButton(onClick = { onEditItem(item.id) }, enabled = !state.deleting) {
                            Text("Edit")
                        }
                    }
                    TextButton(
                        onClick = { onRequestDelete(item.id) },
                        enabled = !state.deleting
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete item?") },
            text = { Text("Delete \"${item.title}\"? This cannot be undone.") },
            dismissButton = {
                TextButton(onClick = onCancelDelete, enabled = !state.deleting) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, enabled = !state.deleting) {
                    Text(if (state.deleting) "Deleting…" else "Delete")
                }
            }
        )
    }
}
