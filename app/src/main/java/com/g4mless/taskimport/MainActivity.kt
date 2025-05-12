package com.g4mless.taskimport

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings // Import ikon Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel 
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g4mless.taskimport.ui.theme.ImportTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImportTheme {
                AppNavigationController()
            }
        }
    }
}

@Composable
fun AppNavigationController() {
    MainAppNavigationHost()
}


@Composable
fun MainAppNavigationHost() {
    val application = LocalContext.current.applicationContext as Application
    val factory = TaskViewModelFactory(application)
    val taskViewModel: TaskViewModel = viewModel(factory = factory)
    var showAuthScreen by remember { mutableStateOf(false) }
    var showVerifyScreen by remember { mutableStateOf(false) }

    val authViewModel: AuthViewModel = viewModel()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isEmailVerified by authViewModel.isEmailVerified.collectAsStateWithLifecycle()
    val authResult by authViewModel.authResult.collectAsStateWithLifecycle()

    var showSettingsScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = showSettingsScreen) {
        showSettingsScreen = false
    }

    BackHandler(enabled = showAuthScreen) {
        showAuthScreen = false // Kembali dari Auth ke Main
    }
    BackHandler(enabled = showVerifyScreen) {
        showVerifyScreen = false // Kembali dari Verify ke Main
    }

    LaunchedEffect(authResult, currentUser, isEmailVerified) {
        authResult?.let { result ->
            if (result.success && currentUser != null) {
                if (currentUser?.isEmailVerified == true) {
                    showAuthScreen = false
                    showVerifyScreen = false
                    authViewModel.clearAuthResult()
                } else {
                    showAuthScreen = false
                    showVerifyScreen = true
                    authViewModel.clearAuthResult()
                }
            }
        }

        if (showVerifyScreen && isEmailVerified) {
            showVerifyScreen = false
        }
    }
    LaunchedEffect(currentUser) {
        taskViewModel.setupFirestoreListenerBasedOnAuth()
    }

    when {
        showAuthScreen -> {
            AuthScreen(authViewModel = authViewModel)
        }
        showVerifyScreen -> {
            EmailVerificationScreen(authViewModel = authViewModel)
        }
        else -> {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
                    targetState = showSettingsScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "Main App Navigation"
                ) { isSettingsVisibleTarget ->
                    if (isSettingsVisibleTarget) {
                        SettingsScreen(
                            taskViewModel = taskViewModel,
                            authViewModel = authViewModel,
                            onNavigateBack = { showSettingsScreen = false },
                            onNavigateToAuth = { showAuthScreen = true }
                        )
                    } else {
                        TodoContent(
                            viewModel = taskViewModel,
                            onNavigateToSettings = { showSettingsScreen = true }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoContent(
    viewModel: TaskViewModel,
    onNavigateToSettings: () -> Unit
) {
    val taskList by viewModel.tasks.collectAsStateWithLifecycle()
    val showCompletedTasks by viewModel.showCompletedTasks.collectAsStateWithLifecycle()
    var showAddTaskDialogState by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    val showDialog = showAddTaskDialogState || taskToEdit != null

    BackHandler(enabled = showCompletedTasks && !showDialog) {
        viewModel.setShowCompleted(false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (showCompletedTasks) "Completed Tasks" else "Import") },
                actions = {
                    IconButton(onClick = { viewModel.setShowCompleted(!showCompletedTasks) }) {
                        Icon(
                            imageVector = if (showCompletedTasks) Icons.AutoMirrored.Filled.List else Icons.Default.Check,
                            contentDescription = if (showCompletedTasks) "Show Active Tasks" else "Show Completed Tasks"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !showCompletedTasks, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(onClick = { showAddTaskDialogState = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                val itemsToShow = if (showCompletedTasks) {
                    taskList.filter { it.isCompleted }
                } else {
                    taskList.filter { !it.isCompleted }
                }.sortedByDescending { it.importance }

                items(itemsToShow, key = { task -> task.id }) { task ->
                    TaskItem(
                        task = task,
                        onDelete = { viewModel.deleteTask(task) },
                        onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                        onEdit = { taskToEdit = task },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    if (showDialog) {
        AddTaskDialog(
            taskToEdit = taskToEdit,
            onDismiss = {
                showAddTaskDialogState = false
                taskToEdit = null
            },
            onAddTask = { name, importance ->
                viewModel.addTask(name, importance)
            },
            onUpdateTask = { taskId, newName, newImportance ->
                viewModel.updateTask(taskId, newName, newImportance)
            }
        )
    }
}

@Composable
fun TaskItem(
    task: Task,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var taskColor: Color = Color.Unspecified
    when (task.importance) {
        1 -> taskColor = Color.Blue.copy(alpha = 0.3f)
        2 -> taskColor = Color.Green.copy(alpha = 0.3f)
        3 -> taskColor = Color.Yellow.copy(alpha = 0.3f)
        4 -> taskColor = Color.Red.copy(alpha = 0.3f)
        5 -> taskColor = Color.Magenta.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (taskColor != Color.Unspecified) taskColor else CardDefaults.cardColors().containerColor
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 90.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onToggleComplete() },
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    SelectionContainer {
                        Text(task.name, style = MaterialTheme.typography.bodyLarge, lineHeight = 19.sp)
                    }
                    Text("Importance: ${task.importance}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Task")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Task")
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    taskToEdit: Task? = null,
    onDismiss: () -> Unit,
    onAddTask: (String, Int) -> Unit,
    onUpdateTask: (taskId: Int, newName: String, newImportance: Int) -> Unit
) {
    val isEditMode = taskToEdit != null
    var taskName by remember { mutableStateOf(taskToEdit?.name ?: "") }
    var selectedImportance by remember { mutableIntStateOf(taskToEdit?.importance ?: 1) }
    var expanded by remember { mutableStateOf(false) }
    val importanceLevels = listOf(1, 2, 3, 4, 5)
    val isSaveEnabled = taskName.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEditMode) "Edit Task" else "Add New Task",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text("Task Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Importance",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(selectedImportance.toString(), style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown arrow")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        importanceLevels.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level.toString()) },
                                onClick = {
                                    selectedImportance = level
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (isEditMode) {
                                onUpdateTask(taskToEdit!!.id, taskName, selectedImportance)
                            } else {
                                onAddTask(taskName, selectedImportance)
                            }
                            onDismiss()
                        },
                        enabled = isSaveEnabled
                    ) {
                        Text(if (isEditMode) "Save" else "Add")
                    }
                }
            }
        }
    }
}