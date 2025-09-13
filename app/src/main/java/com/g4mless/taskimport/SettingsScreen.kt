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
// import androidx.lifecycle.viewmodel.compose.viewModel // No longer needed for AuthViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    taskViewModel: TaskViewModel,
    onNavigateBack: () -> Unit
    // Removed AuthViewModel and onNavigateToAuth
) {
    var showClearCompletedDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf<Uri?>(null) }
    var importFileType by remember { mutableStateOf("") } // Keep for JSON import logic
    val scrollState = rememberScrollState()

    // Removed currentUser and authViewModel related state

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val operationResult by taskViewModel.operationResult.collectAsStateWithLifecycle()

    LaunchedEffect(operationResult) {
        operationResult?.let { message ->
            scope.launch { snackbarHostState.showSnackbar(message = message) }
            taskViewModel.clearOperationResult()
        }
    }

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

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { sourceUri ->
                val type = context.contentResolver.getType(sourceUri)
                if (type == "application/json" || sourceUri.path?.endsWith(".json", ignoreCase = true) == true) {
                    importFileType = "json" // Used for the import confirmation dialog logic
                    showImportConfirmDialog = sourceUri
                } else {
                    scope.launch{ snackbarHostState.showSnackbar("Unsupported file type (JSON only).") }
                }
            }
        }
    )

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
            // Removed Account section as it was Firebase dependent
            // HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp)) // Also remove divider if Account section was the only one above

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
                    createJsonFileLauncher.launch(fileName)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Tasks to JSON")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    openFileLauncher.launch(arrayOf("application/json"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Tasks from JSON")
            }
        }
    }

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
