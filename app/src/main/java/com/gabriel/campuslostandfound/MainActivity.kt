
package com.gabriel.campuslostandfound

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gabriel.campuslostandfound.ui.theme.CampusLostAndFoundTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        setContent {
            CampusLostAndFoundTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AuthScreen(
                        auth = auth,
                        modifier = Modifier.padding(innerPadding),
                        onSignedIn = { email ->
                            Toast.makeText(
                                this,
                                "Welcome, $email!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
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
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Campus Lost and Found",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isRegistering) {
                "Create your student account"
            } else {
                "Sign in to find or report lost items"
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Email address") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                        auth.createUserWithEmailAndPassword(
                            cleanEmail,
                            password
                        )
                    } else {
                        auth.signInWithEmailAndPassword(
                            cleanEmail,
                            password
                        )
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
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text(if (isRegistering) "Create Account" else "Sign In")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = {
                isRegistering = !isRegistering
                errorMessage = null
            },
            enabled = !isLoading
        ) {
            Text(
                if (isRegistering) {
                    "Already have an account? Sign in"
                } else {
                    "New here? Create an account"
                }
            )
        }

        if (auth.currentUser != null) {
            TextButton(
                onClick = {
                    auth.signOut()
                    email = ""
                    password = ""
                    errorMessage = null
                }
            ) {
                Text("Sign Out")
            }
        }
    }
}
