package com.gabriel.campuslostandfound

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.gabriel.campuslostandfound.state.ItemFormUiState
import com.gabriel.campuslostandfound.validation.itemStatuses

@Composable
internal fun ItemFormScreen(
    state: ItemFormUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var statusMenuOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = onCancel)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (state.isEditing) "Edit Item" else "Add Item",
            style = MaterialTheme.typography.headlineMedium
        )
        val loadError = state.loadError
        when {
            state.loading -> CircularProgressIndicator()
            loadError != null -> FormError(loadError)
            else -> {
                ItemField("Title", draft.title, onTitleChange, state.errors?.title, !state.saving)
                ItemField(
                    if (draft.status == "FOUND") "Description (required)" else "Description (optional)",
                    draft.description,
                    onDescriptionChange,
                    state.errors?.description,
                    !state.saving
                )
                ItemField(
                    "Location", draft.location, onLocationChange, state.errors?.location, !state.saving
                )
                ItemField(
                    "Date (YYYY-MM-DD)", draft.date, onDateChange, state.errors?.date, !state.saving
                )

                Box {
                    TextButton(onClick = { statusMenuOpen = true }, enabled = !state.saving) {
                        Text("Status: ${draft.status}")
                    }
                    DropdownMenu(
                        expanded = statusMenuOpen,
                        onDismissRequest = { statusMenuOpen = false }
                    ) {
                        itemStatuses.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onStatusChange(option)
                                    statusMenuOpen = false
                                }
                            )
                        }
                    }
                }
                state.errors?.status?.let { FormError(it) }

                if (state.categories.isEmpty()) {
                    FormError("No categories are available.")
                } else {
                    Box {
                        TextButton(
                            onClick = { categoryMenuOpen = true },
                            enabled = !state.saving
                        ) {
                            Text(
                                state.categories.firstOrNull { it.id == draft.categoryId }?.name
                                    ?: "Choose a category"
                            )
                        }
                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false }
                        ) {
                            state.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        onCategoryChange(category.id)
                                        categoryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                state.errors?.category?.let { FormError(it) }
                state.saveError?.let { FormError(it) }
                Button(onClick = onSave, enabled = state.canSave) {
                    Text(
                        when {
                            state.saving -> "Saving…"
                            state.isEditing -> "Save Changes"
                            else -> "Save Item"
                        }
                    )
                }
            }
        }

        TextButton(onClick = onCancel, enabled = !state.saving) { Text("Cancel") }
    }
}

@Composable
private fun ItemField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        isError = error != null,
        modifier = Modifier.fillMaxWidth()
    )
    if (error != null) FormError(error)
}

@Composable
private fun FormError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}
