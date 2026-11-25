package com.gabriel.campuslostandfound

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gabriel.campuslostandfound.data.model.Category
import com.gabriel.campuslostandfound.data.model.LostFoundItem
import com.gabriel.campuslostandfound.data.repository.LostFoundRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class ItemFormValues(
    val title: String,
    val description: String,
    val location: String,
    val date: String,
    val status: String,
    val categoryId: String
)

@Composable
internal fun AddItemScreen(
    repository: LostFoundRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ItemFormScreen(
        repository = repository,
        onClose = onClose,
        modifier = modifier
    ) { values ->
        repository.addItem(
            title = values.title,
            description = values.description,
            location = values.location,
            date = values.date,
            status = values.status,
            categoryId = values.categoryId
        )
    }
}

@Composable
internal fun EditItemScreen(
    repository: LostFoundRepository,
    item: LostFoundItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSave: suspend (itemId: String, values: ItemFormValues) -> Unit
) {
    key(item.id) {
        ItemFormScreen(
            repository = repository,
            onClose = onClose,
            modifier = modifier,
            existingItem = item
        ) { values ->
            onSave(item.id, values)
        }
    }
}

@Composable
private fun ItemFormScreen(
    repository: LostFoundRepository,
    onClose: () -> Unit,
    modifier: Modifier,
    existingItem: LostFoundItem? = null,
    onSave: suspend (ItemFormValues) -> Unit
) {
    val isEditing = existingItem != null
    val statuses = listOf("LOST", "FOUND", "RESOLVED")

    var title by rememberSaveable { mutableStateOf(existingItem?.title.orEmpty()) }
    var description by rememberSaveable {
        mutableStateOf(existingItem?.description.orEmpty())
    }
    var location by rememberSaveable { mutableStateOf(existingItem?.location.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(existingItem?.date.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(existingItem?.status ?: "LOST") }
    var categoryId by rememberSaveable {
        mutableStateOf(existingItem?.categoryRef?.id.orEmpty())
    }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var categoryError by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var statusMenuOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler {
        if (!saving) onClose()
    }

    LaunchedEffect(repository) {
        categories = null
        categoryError = false
        try {
            categories = repository.getCategories()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            categoryError = true
        }
    }

    val availableCategories = categories.orEmpty()
    val selectedCategory = availableCategories.firstOrNull { it.id == categoryId }
    val titleError = if (title.isBlank()) "Enter an item title." else null
    val locationError = if (location.isBlank()) "Enter a location." else null
    val dateError = if (!isValidItemDate(date)) {
        "Enter a valid date (YYYY-MM-DD)."
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isEditing) "Edit Item" else "Add Item",
            style = MaterialTheme.typography.headlineMedium
        )

        ItemField("Title", title, { title = it }, titleError.takeIf { showErrors }, !saving)
        ItemField("Description (optional)", description, { description = it }, null, !saving)
        ItemField(
            "Location", location, { location = it },
            locationError.takeIf { showErrors }, !saving
        )
        ItemField(
            "Date (YYYY-MM-DD)", date, { date = it },
            dateError.takeIf { showErrors }, !saving
        )

        Column {
            TextButton(onClick = { statusMenuOpen = true }, enabled = !saving) {
                Text("Status: $status")
            }
            DropdownMenu(
                expanded = statusMenuOpen,
                onDismissRequest = { statusMenuOpen = false }
            ) {
                statuses.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            status = option
                            statusMenuOpen = false
                        }
                    )
                }
            }
            if (showErrors && status !in statuses) FormError("Choose a valid status.")
        }

        when {
            categoryError -> FormError("Could not load categories. Cancel and try again.")
            categories == null -> CircularProgressIndicator()
            availableCategories.isEmpty() -> FormError("No categories are available.")
            else -> Column {
                TextButton(onClick = { categoryMenuOpen = true }, enabled = !saving) {
                    Text(selectedCategory?.name ?: "Choose a category")
                }
                DropdownMenu(
                    expanded = categoryMenuOpen,
                    onDismissRequest = { categoryMenuOpen = false }
                ) {
                    availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                categoryId = category.id
                                categoryMenuOpen = false
                            }
                        )
                    }
                }
                if (showErrors && selectedCategory == null) {
                    FormError("Choose a category.")
                }
            }
        }

        if (saveError) FormError("Could not save the item. Please try again.")

        Button(
            enabled = !saving && availableCategories.isNotEmpty(),
            onClick = {
                showErrors = true
                saveError = false

                if (!saving && titleError == null && locationError == null &&
                    dateError == null && status in statuses && selectedCategory != null
                ) {
                    val values = ItemFormValues(
                        title = title.trim(),
                        description = description.trim(),
                        location = location.trim(),
                        date = date.trim(),
                        status = status,
                        categoryId = selectedCategory.id
                    )
                    saving = true
                    scope.launch {
                        try {
                            onSave(values)
                            onClose()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            saveError = true
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        ) {
            Text(
                when {
                    saving -> "Saving…"
                    isEditing -> "Save Changes"
                    else -> "Save Item"
                }
            )
        }

        TextButton(onClick = onClose, enabled = !saving) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ItemField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
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

private fun isValidItemDate(value: String): Boolean {
    val date = value.trim()
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date)) return false

    val (year, month, day) = date.split('-').map { it.toInt() }
    if (year == 0 || month !in 1..12) return false

    val leapYear = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
    val daysInMonth = when (month) {
        2 -> if (leapYear) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..daysInMonth
}