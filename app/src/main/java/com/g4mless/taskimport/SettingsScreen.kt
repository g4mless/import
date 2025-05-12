package com.g4mless.taskimport

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Ikon panah kembali
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.verticalScroll

// --- Composable untuk Layar Settings ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    taskViewModel: TaskViewModel, // Terima TaskViewModel
    authViewModel: AuthViewModel = viewModel(), // Dapatkan AuthViewModel
    onNavigateBack: () -> Unit, // Lambda untuk kembali
    onNavigateToAuth: () -> Unit // <-- Parameter baru untuk navigasi ke AuthScreen
) {
    // State untuk dialog konfirmasi
    var showClearCompletedDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf<Uri?>(null) }
    var importFileType by remember { mutableStateOf("") } // Hanya JSON
    val scrollState = rememberScrollState()

    // Amati status login dari AuthViewModel
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Amati hasil operasi dari TaskViewModel (untuk clear, import, export)
    val operationResult by taskViewModel.operationResult.collectAsStateWithLifecycle()

    // Tampilkan Snackbar saat ada hasil operasi baru dari TaskViewModel
    LaunchedEffect(operationResult) {
        operationResult?.let { message ->
            scope.launch { snackbarHostState.showSnackbar(message = message) }
            taskViewModel.clearOperationResult()
        }
    }

    // --- Activity Result Launchers (Hanya JSON) ---

    // Launcher untuk EXPORT JSON
    val createJsonFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            uri?.let { targetUri ->
                val fileContent: String? = taskViewModel.prepareJsonExportData()
                if (fileContent != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                                outputStream.write(fileContent.toByteArray())
                                outputStream.flush()
                            } ?: throw Exception("Failed to open output stream")
                            withContext(Dispatchers.Main) {
                                snackbarHostState.showSnackbar("Export successful!")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                snackbarHostState.showSnackbar("Export failed: ${e.message}")
                            }
                        }
                    }
                } else {
                    scope.launch{ snackbarHostState.showSnackbar("Failed to prepare export data.") }
                }
            }
        }
    )

    // Launcher untuk IMPORT JSON
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { sourceUri ->
                val type = context.contentResolver.getType(sourceUri)
                if (type == "application/json" || sourceUri.path?.endsWith(".json", ignoreCase = true) == true) {
                    importFileType = "json" // Set tipe (meski hanya JSON)
                    showImportConfirmDialog = sourceUri // Tampilkan dialog konfirmasi
                } else {
                    scope.launch{ snackbarHostState.showSnackbar("Unsupported file type (JSON only).") }
                }
            }
        }
    )

    // --- UI SettingsScreen ---
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // --- Bagian Akun / Autentikasi ---
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (currentUser == null) {
                // Tampilan jika belum login
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Login or Sign Up to sync your tasks across devices.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigateToAuth() }, // <-- Panggil lambda navigasi
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Login / Sign Up")
                        }
                    }
                }
            } else {
                // Tampilan jika sudah login
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Logged in as:", style = MaterialTheme.typography.labelSmall)
                            Text(currentUser?.email ?: "Unknown Email", style = MaterialTheme.typography.bodyMedium)
                            if (currentUser?.isEmailVerified == false) {
                                Text(" (Email not verified)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Button(
                            onClick = { authViewModel.signOut() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Logout")
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text("Data Management", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { showClearCompletedDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear Completed Tasks")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showClearAllDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear All Tasks")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text("Export / Import", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "tasks_export_$timeStamp.json"
                    createJsonFileLauncher.launch(fileName) // Panggil launcher JSON
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Tasks to JSON")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    openFileLauncher.launch(arrayOf("application/json")) // Hanya minta file JSON
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Tasks from JSON")
            }
            // --- Akhir Bagian Export / Import ---
        }
    }

    // --- Dialog Konfirmasi ---
    // Dialog Clear Completed
    if (showClearCompletedDialog) {
        AlertDialog(
            onDismissRequest = { showClearCompletedDialog = false },
            title = { Text("Confirm Action") },
            text = { Text("Are you sure you want to delete all completed tasks?") },
            confirmButton = {
                TextButton(onClick = {
                    taskViewModel.clearCompletedTasks()
                    showClearCompletedDialog = false
                }) { Text("Yes, Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCompletedDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog Clear All
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Confirm Deletion") },
            text = { Text("WARNING: This will permanently delete ALL tasks. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        taskViewModel.clearAllTasks()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Yes, Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog Konfirmasi IMPORT JSON
    showImportConfirmDialog?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = null },
            title = { Text("Confirm Import") },
            text = { Text("This will replace all current tasks with data from the selected JSON file. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val content = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                            reader.readText()
                                        }
                                    } ?: throw Exception("Failed to open input stream for URI: $uri")
                                }
                                // Panggil import JSON
                                taskViewModel.importTasksFromJson(content)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                scope.launch { snackbarHostState.showSnackbar("Import failed: ${e.message}") }
                            } finally {
                                showImportConfirmDialog = null
                            }
                        }
                    }
                ) { Text("Yes, Import") }
            },
            dismissButton = { TextButton(onClick = { showImportConfirmDialog = null }) { Text("Cancel") } }
        )
    }
}
