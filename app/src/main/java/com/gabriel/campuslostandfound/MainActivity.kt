package com.gabriel.campuslostandfound

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gabriel.campuslostandfound.data.model.LostFoundItem
import com.gabriel.campuslostandfound.data.repository.FirestoreLostFoundRepository
import com.gabriel.campuslostandfound.data.repository.LostFoundRepository
import com.gabriel.campuslostandfound.ui.theme.CampusLostAndFoundTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val repository = FirestoreLostFoundRepository(auth = auth)

        setContent {
            var userId by remember(auth) { mutableStateOf(auth.currentUser?.uid) }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener {
                    userId = it.currentUser?.uid
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
            }

            CampusLostAndFoundTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val uid = userId
                    val screenModifier = Modifier.padding(innerPadding)

                    if (uid == null) {
                        AuthScreen(auth = auth, modifier = screenModifier) {
                            Toast.makeText(
                                this@MainActivity,
                                "Welcome!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        key(uid) {
                            SignedInScreen(
                                repository = repository,
                                onSignOut = { auth.signOut() },
                                modifier = screenModifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthScreen(
    auth: FirebaseAuth,
    modifier: Modifier = Modifier,
    onSignedIn: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Campus Lost and Found", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (isRegistering) "Create your student account"
            else "Sign in to find or report lost items",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Email address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val cleanEmail = email.trim()
                if (cleanEmail.isBlank() || password.isBlank()) {
                    errorMessage = "Enter your email and password."
                } else if (isRegistering && password.length < 6) {
                    errorMessage = "Password must contain at least 6 characters."
                } else {
                    isLoading = true
                    errorMessage = null

                    val task = if (isRegistering) {
                        auth.createUserWithEmailAndPassword(cleanEmail, password)
                    } else {
                        auth.signInWithEmailAndPassword(cleanEmail, password)
                    }

                    task.addOnCompleteListener { result ->
                        isLoading = false
                        if (result.isSuccessful) {
                            onSignedIn(cleanEmail)
                        } else {
                            errorMessage = result.exception?.localizedMessage
                                ?: "Authentication failed. Please try again."
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator()
            else Text(if (isRegistering) "Create Account" else "Sign In")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                isRegistering = !isRegistering
                errorMessage = null
            },
            enabled = !isLoading
        ) {
            Text(
                if (isRegistering) "Already have an account? Sign in"
                else "New here? Create an account"
            )
        }
    }
}

@Composable
private fun SignedInScreen(
    repository: LostFoundRepository,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var addingItem by rememberSaveable { mutableStateOf(false) }
    var editingItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var myItems by remember { mutableStateOf<List<LostFoundItem>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        repository.observeMyItems()
            .catch { loadFailed = true }
            .collect { myItems = it }
    }

    val editId = editingItemId
    val itemToEdit = myItems?.firstOrNull { it.id == editId }

    when {
        addingItem -> AddItemScreen(
            repository = repository,
            onClose = { addingItem = false },
            modifier = modifier
        )

        editId != null && itemToEdit != null -> EditItemScreen(
            repository = repository,
            item = itemToEdit,
            onClose = { editingItemId = null },
            modifier = modifier
        ) { itemId, values ->
            repository.updateItem(
                itemId = itemId,
                title = values.title,
                description = values.description,
                location = values.location,
                date = values.date,
                status = values.status,
                categoryId = values.categoryId
            )
        }

        editId != null -> {
            BackHandler { editingItemId = null }
            Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
                when {
                    loadFailed -> Text("Could not load the item.")
                    myItems == null -> CircularProgressIndicator()
                    else -> Text("This item is no longer available.")
                }
                TextButton(onClick = { editingItemId = null }) { Text("Back") }
            }
        }

        else -> MyItemsScreen(
            myItems = myItems,
            loadFailed = loadFailed,
            onSignOut = onSignOut,
            onAddItem = { addingItem = true },
            onEditItem = { editingItemId = it },
            onDeleteItem = { itemId -> repository.deleteItem(itemId) },
            modifier = modifier
        )
    }
}

@Composable
private fun MyItemsScreen(
    myItems: List<LostFoundItem>?,
    loadFailed: Boolean,
    onSignOut: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (String) -> Unit,
    onDeleteItem: suspend (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<LostFoundItem?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var sortOrder by rememberSaveable { mutableStateOf(ItemSortOrder.NEWEST) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleItems = searchItems(myItems.orEmpty(), searchQuery)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("My Items", style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onSignOut, enabled = !deleting) { Text("Sign Out") }
        Button(onClick = onAddItem, enabled = !deleting) { Text("Add Item") }

        Box {
            TextButton(
                onClick = { sortMenuOpen = true },
                enabled = !deleting
            ) {
                Text("Sort: ${sortOrder.label}")
            }

            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false }
            ) {
                ItemSortOrder.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            sortOrder = option
                            sortMenuOpen = false
                        }
                    )
                }
            }
        }


        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by title or location") },
            singleLine = true,
            enabled = !deleting,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = { searchQuery = "" },
            enabled = searchQuery.isNotEmpty() && !deleting
        ) {
            Text("Clear")
        }

        if (deleteError) {
            Text(
                "Could not delete the item. Please try again.",
                color = MaterialTheme.colorScheme.error
            )
        }

        when {
            loadFailed -> Text(
                "Could not load your items. Sign out and back in to retry.",
                color = MaterialTheme.colorScheme.error
            )
            myItems == null -> CircularProgressIndicator()
            myItems.isEmpty() -> Text("No items reported yet.")
            visibleItems.isEmpty() -> Text("No items match your search.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sortItems(visibleItems, sortOrder), key = { it.id }) { item ->
                    Column {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text("${item.status} · ${item.location}")

                        if (item.createdAt != null) {
                            TextButton(
                                onClick = { onEditItem(item.id) },
                                enabled = !deleting
                            ) {
                                Text("Edit")
                            }
                        }

                        TextButton(
                            onClick = {
                                deleteError = false
                                pendingDelete = item
                            },
                            enabled = !deleting
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text("Delete item?") },
            text = { Text("Delete \"${item.title}\"? This cannot be undone.") },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    enabled = !deleting
                ) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        if (!deleting) {
                            deleting = true
                            scope.launch {
                                try {
                                    onDeleteItem(item.id)
                                    pendingDelete = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    pendingDelete = null
                                    deleteError = true
                                } finally {
                                    deleting = false
                                }
                            }
                        }
                    }
                ) {
                    Text(if (deleting) "Deleting…" else "Delete")
                }
            }
        )
    }
}
