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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel 
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g4mless.taskimport.ui.theme.ImportTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


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
        showAuthScreen = false
    }
    BackHandler(enabled = showVerifyScreen) {
        showVerifyScreen = false
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
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    val completedTasksCount = taskList.count { it.isCompleted }
    BackHandler(enabled = showCompletedTasks && !showDialog) {
        viewModel.setShowCompleted(false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (showCompletedTasks) "Completed ($completedTasksCount)" else "Import") },
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
                        onDelete = {
                            taskToDelete = task
                            showDeleteConfirmDialog = true
                        },
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
            onAddTask = { name, importance, dueDate ->
                viewModel.addTask(name, importance, dueDate)
            },
            onUpdateTask = { taskId, newName, newImportance, newDueDate ->
                viewModel.updateTask(taskId, newName, newImportance, newDueDate)
            }
        )
    }
    if (showDeleteConfirmDialog && taskToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                taskToDelete = null
            },
            title = { Text("Confirm Delete?") },
            text = { Text("Delete this: \"${taskToDelete?.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        taskToDelete?.let { viewModel.deleteTask(it) }
                        showDeleteConfirmDialog = false
                        taskToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        taskToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
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
        5 -> taskColor = Color(red = 0.627f, green = 0.125f, blue = 0.941f, alpha = 0.3f)
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
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Importance",
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .alignByBaseline(),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("${task.importance}", style = MaterialTheme.typography.bodySmall)
                        if (task.createdAt != null) {
                            val date = remember(task.createdAt) { Date(task.createdAt) }
                            val formatted = remember(task.createdAt) {
                                val oneDay = 24 * 60 * 60 * 1000L
                                val todayStart = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                val dateStart = java.util.Calendar.getInstance().apply {
                                    timeInMillis = task.createdAt
                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                when (dateStart) {
                                    todayStart -> "Today"
                                    todayStart - oneDay -> "Yesterday"
                                    else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Created at",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .alignByBaseline(),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(formatted, style = MaterialTheme.typography.bodySmall, color = Color.White)
                            }
                        }
                    }
                    if (task.dueDate != null) {
                        val date = remember(task.dueDate) { Date(task.dueDate) }
                        val formatted = remember(task.dueDate) {
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Due date",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(14.dp)
                                    .alignByBaseline(),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                "Due: $formatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
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
    onAddTask: (String, Int, Long?) -> Unit,
    onUpdateTask: (taskId: Int, newName: String, newImportance: Int, newDueDate: Long?) -> Unit
) {
    val isEditMode = taskToEdit != null
    var taskName by remember { mutableStateOf(taskToEdit?.name ?: "") }
    var selectedImportance by remember { mutableIntStateOf(taskToEdit?.importance ?: 1) }
    var expanded by remember { mutableStateOf(false) }
    val importanceLevels = listOf(1, 2, 3, 4, 5)
    val isSaveEnabled = taskName.isNotBlank()

    var dueDate by remember { mutableStateOf(taskToEdit?.dueDate) }
    var showDatePicker by remember { mutableStateOf(false) }

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

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = dueDate?.let {
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
                        } ?: "Set Due Date"
                    )
                }
                if (dueDate != null) {
                    Button(onClick = { dueDate = null }) {
                        Text("Clear Due Date")
                    }
                }


                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dueDate = datePickerState.selectedDateMillis
                                    showDatePicker = false
                                }
                            ) {
                                Text("OK")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showDatePicker = false
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
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
                                onUpdateTask(taskToEdit!!.id, taskName, selectedImportance, dueDate)
                            } else {
                                onAddTask(taskName, selectedImportance, dueDate)
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