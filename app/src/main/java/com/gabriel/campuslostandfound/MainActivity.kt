package com.gabriel.campuslostandfound

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.gabriel.campuslostandfound.state.CampusAppState
import com.gabriel.campuslostandfound.state.SignedInSession
import com.gabriel.campuslostandfound.ui.AuthScreen
import com.gabriel.campuslostandfound.ui.MyItemsScreen
import com.gabriel.campuslostandfound.ui.theme.CampusLostAndFoundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appState = ViewModelProvider(this, CampusAppStateFactory)[CampusAppState::class.java]
        setContent { CampusApp(appState) }
    }
}

@Composable
private fun CampusApp(appState: CampusAppState) {
    val auth by appState.auth.state.collectAsState()
    val session by appState.session.collectAsState()
    ShowToast(auth.notice, appState.auth::consumeNotice)

    CampusLostAndFoundTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            val modifier = Modifier.padding(padding)
            val signedIn = session
            when {
                auth.userId == null -> AuthScreen(
                    state = auth,
                    onEmailChange = appState.auth::changeEmail,
                    onPasswordChange = appState.auth::changePassword,
                    onToggleRegistration = appState.auth::toggleRegistration,
                    onSubmit = appState.auth::submit,
                    modifier = modifier
                )

                signedIn != null && signedIn.userId == auth.userId -> key(signedIn.userId) {
                    SignedInScreen(signedIn, appState.auth::signOut, modifier)
                }

                else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun SignedInScreen(
    session: SignedInSession,
    onSignOut: () -> Unit,
    modifier: Modifier
) {
    val list by session.list.state.collectAsState()
    val form by session.form.collectAsState()
    ShowToast(list.deleteNotice, session.list::consumeDeleteNotice)

    val activeForm = form
    if (activeForm == null) {
        MyItemsScreen(
            state = list,
            onSignOut = onSignOut,
            onAddItem = session::addItem,
            onEditItem = session::editItem,
            onSearch = session.list::search,
            onSort = session.list::sort,
            onRequestDelete = session.list::requestDelete,
            onCancelDelete = session.list::cancelDelete,
            onConfirmDelete = session.list::confirmDelete,
            modifier = modifier
        )
    } else {
        key(activeForm) {
            val formState by activeForm.state.collectAsState()
            ItemFormScreen(
                state = formState,
                onTitleChange = activeForm::changeTitle,
                onDescriptionChange = activeForm::changeDescription,
                onLocationChange = activeForm::changeLocation,
                onDateChange = activeForm::changeDate,
                onStatusChange = activeForm::changeStatus,
                onCategoryChange = activeForm::changeCategory,
                onSave = activeForm::save,
                onCancel = session::cancelForm,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ShowToast(message: String?, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onShown()
        }
    }
}
