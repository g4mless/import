package com.g4mless.taskimport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Import collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authViewModel: AuthViewModel = viewModel() // Dapatkan instance AuthViewModel
) {
    // State lokal untuk input pengguna
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) } // State untuk toggle Login/Sign Up

    // Amati state dari AuthViewModel
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val authResult by authViewModel.authResult.collectAsStateWithLifecycle()
    // currentUser tidak perlu diamati di sini, karena navigasi akan ditangani di level atas

    // Snackbar host state untuk menampilkan error
    val snackbarHostState = remember { SnackbarHostState() }

    // Tampilkan Snackbar jika ada error dari authResult
    LaunchedEffect(authResult) {
        authResult?.let { result ->
            if (!result.success && result.errorMessage != null) {
                snackbarHostState.showSnackbar(message = result.errorMessage)
                authViewModel.clearAuthResult() // Bersihkan hasil setelah ditampilkan
            }
            // Jika sukses, navigasi akan ditangani oleh observer currentUser di level atas
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isLoginMode) "Login" else "Sign Up",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Input Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Input Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(), // Sembunyikan teks password
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Tombol Aksi (Login atau Sign Up)
            Button(
                onClick = {
                    if (isLoginMode) {
                        authViewModel.signIn(email, password)
                    } else {
                        authViewModel.signUp(email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank() // Nonaktifkan saat loading atau input kosong
            ) {
                Text(if (isLoginMode) "Login" else "Sign Up")
            }

            // Tampilkan loading indicator jika sedang proses
            if (isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tombol untuk ganti mode (Login <-> Sign Up)
            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(
                    if (isLoginMode) "Don't have an account? Sign Up"
                    else "Already have an account? Login"
                )
            }
        }
    }
}
