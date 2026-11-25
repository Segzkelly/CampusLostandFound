package com.gabriel.campuslostandfound.validation

internal fun validateCredentials(email: String, password: String, registering: Boolean): String? =
    when {
        email.isBlank() || password.isBlank() -> "Enter your email and password."
        registering && password.length < 6 -> "Password must contain at least 6 characters."
        else -> null
    }
