package com.gabriel.campuslostandfound

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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

    val isReviewer by session.isReviewer.collectAsState()
    val reports by session.pendingReports.collectAsState()
    val loading by session.reviewLoading.collectAsState()
    val error by session.reviewError.collectAsState()
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val submitting by session.reviewSubmitting.collectAsState()
    val decisionError by session.reviewDecisionError.collectAsState()
    var selectedReportId by rememberSaveable { mutableStateOf<String?>(null) }
    var decision by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }

    if (queueOpen && isReviewer) {
        BackHandler {
            if (!submitting) queueOpen = false
        }

        LaunchedEffect(session) {
            session.loadReviewQueue()
        }

        LazyColumn(modifier = modifier.fillMaxSize().padding(24.dp)) {
            item {
                TextButton(
                    onClick = { queueOpen = false },
                    enabled = !submitting
                ) {
                    Text("Back to My Items")
                }
                Text("Review Queue")
            }

            when {
                loading -> item { CircularProgressIndicator() }
                error != null -> item {
                    Text(error.orEmpty())
                    TextButton(onClick = session::loadReviewQueue) { Text("Retry") }
                }
                reports.isEmpty() -> item { Text("No pending reports.") }
                else -> items(reports, key = { it.id }) { report ->
                    Text(report.title, modifier = Modifier.padding(top = 16.dp))
                    Text("${report.status} · ${report.location} · ${report.date}")
                    Text(report.description)

                    TextButton(
                        enabled = !submitting,
                        onClick = {
                            session.clearReviewDecisionError()
                            decision = "CONFIRMED"
                            reason = ""
                            selectedReportId = report.id
                        }
                    ) {
                        Text("Confirm")
                    }

                    TextButton(
                        enabled = !submitting,
                        onClick = {
                            session.clearReviewDecisionError()
                            decision = "REJECTED"
                            reason = ""
                            selectedReportId = report.id
                        }
                    ) {
                        Text("Reject")
                    }
                }
            }
        }

        val selectedReport = reports.firstOrNull { it.id == selectedReportId }

        LaunchedEffect(loading, error, selectedReportId, selectedReport) {
            if (!loading && error == null && selectedReport == null) {
                selectedReportId = null
            }
        }

        if (selectedReport != null) {
            val rejecting = decision == "REJECTED"

            AlertDialog(
                onDismissRequest = {
                    if (!submitting) selectedReportId = null
                },
                title = {
                    Text(
                        if (rejecting) "Reject ${selectedReport.title}?"
                        else "Confirm ${selectedReport.title}?"
                    )
                },
                text = {
                    androidx.compose.foundation.layout.Column {
                        if (rejecting) {
                            OutlinedTextField(
                                value = reason,
                                onValueChange = { reason = it },
                                label = { Text("Reason (required, max 500 characters)") },
                                enabled = !submitting
                            )
                        } else {
                            Text("This report will be marked as confirmed.")
                        }
                        decisionError?.let { Text(it) }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !submitting,
                        onClick = { selectedReportId = null }
                    ) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !submitting && (!rejecting || reason.trim().length in 1..500),
                        onClick = {
                            session.submitReviewDecision(selectedReport.id, decision, reason)
                        }
                    ) {
                        Text(
                            if (submitting) "Saving…"
                            else if (rejecting) "Reject"
                            else "Confirm"
                        )
                    }
                }
            )
        }
        return
    }
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
            modifier = modifier,
            onOpenReviewQueue = if (isReviewer) ({ queueOpen = true }) else null
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
