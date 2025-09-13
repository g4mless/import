package com.g4mless.taskimport

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.Exception

data class Task (
    val id: Int = 0,
    val name: String = "",
    val importance: Int = 1,
    var isCompleted: Boolean = false,
    val dueDate: Long? = null
)

private val TASKS_KEY = stringPreferencesKey("tasks")
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tasks")

val IMPORTANCE_COLOR_1_KEY = stringPreferencesKey("importance_color_1")
val IMPORTANCE_COLOR_2_KEY = stringPreferencesKey("importance_color_2")
val IMPORTANCE_COLOR_3_KEY = stringPreferencesKey("importance_color_3")
val IMPORTANCE_COLOR_4_KEY = stringPreferencesKey("importance_color_4")
val IMPORTANCE_COLOR_5_KEY = stringPreferencesKey("importance_color_5")

class TaskViewModel(application: Application) : ViewModel() {

    private val appContext = application.applicationContext
    private val gson = Gson() // Instance Gson

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _showCompletedTasks = MutableStateFlow(false)
    val showCompletedTasks: StateFlow<Boolean> = _showCompletedTasks.asStateFlow()

    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

    private val _customImportanceColors = MutableStateFlow<Map<Int, String?>>(emptyMap())

    init {
        viewModelScope.launch {
            loadTasksFromDataStore(appContext).collect { taskList ->
                if (!areTaskListsEqual(_tasks.value, taskList)) {
                    _tasks.value = taskList
                }
            }
        }
        viewModelScope.launch {
            loadCustomColors(appContext).collect { colorMap ->
                _customImportanceColors.value = colorMap
            }
        }
        // setupFirestoreListenerBasedOnAuth() // Removed Firebase call
    }

    private fun loadCustomColors(context: Context): Flow<Map<Int, String?>> {
        return context.dataStore.data
            .map { preferences ->
                mapOf(
                    1 to preferences[IMPORTANCE_COLOR_1_KEY],
                    2 to preferences[IMPORTANCE_COLOR_2_KEY],
                    3 to preferences[IMPORTANCE_COLOR_3_KEY],
                    4 to preferences[IMPORTANCE_COLOR_4_KEY],
                    5 to preferences[IMPORTANCE_COLOR_5_KEY]
                )
            }
            .catch { exception ->
                exception.printStackTrace()
                emit(emptyMap())
            }
    }

    fun prepareJsonExportData(): String? {
        return try {
            gson.toJson(_tasks.value)
        } catch (e: Exception) {
            e.printStackTrace()
            _operationResult.value = "Error preparing JSON data: ${e.message}"
            null
        }
    }

    fun importTasksFromJson(jsonData: String) {
        viewModelScope.launch {
            try {
                val type = object : TypeToken<List<Task>>() {}.type
                val importedTasks: List<Task>? = gson.fromJson(jsonData, type)

                if (importedTasks != null) {
                    _tasks.value = importedTasks
                    saveTasksToDataStore(appContext, importedTasks)
                    _operationResult.value = "Tasks imported successfully locally."
                } else {
                    _operationResult.value = "Import failed: Invalid JSON format."
                }
            } catch (e: JsonSyntaxException) {
                e.printStackTrace()
                _operationResult.value = "Import failed: Invalid JSON syntax. ${e.message}"
            } catch (e: Exception) {
                e.printStackTrace()
                _operationResult.value = "Import failed: ${e.message}"
            }
        }
    }

    private fun areTaskListsEqual(list1: List<Task>, list2: List<Task>): Boolean {
        if (list1.size != list2.size) return false
        return list1.sortedBy { it.id } == list2.sortedBy { it.id }
    }

    fun addTask(name: String, importance: Int, dueDate: Long?) {
        viewModelScope.launch {
            val currentList = _tasks.value
            val nextId = (currentList.maxOfOrNull { it.id } ?: -1) + 1
            val newTask = Task(
                id = nextId,
                name = name,
                importance = importance,
                isCompleted = false,
                dueDate = dueDate
            )
            val newList = (currentList + newTask).sortedByDescending { it.importance }

            _tasks.value = newList
            saveTasksToDataStore(appContext, newList)
        }
    }

    fun updateTask(taskId: Int, newName: String, newImportance: Int, newDueDate: Long?) {
        viewModelScope.launch {
            val currentList = _tasks.value
            val newList = currentList.map { task ->
                if (task.id == taskId) {
                    task.copy(name = newName, importance = newImportance, dueDate = newDueDate)
                } else {
                    task
                }
            }.sortedByDescending { it.importance }

            _tasks.value = newList
            saveTasksToDataStore(appContext, newList)
        }
    }

    fun deleteTask(taskToDelete: Task) {
        viewModelScope.launch {
            val currentList = _tasks.value
            val newList = currentList.filter { it.id != taskToDelete.id }

            _tasks.value = newList
            saveTasksToDataStore(appContext, newList)
        }
    }

    fun toggleTaskCompletion(taskToToggle: Task) {
        viewModelScope.launch {
            val currentList = _tasks.value
            val newList = currentList.map { task ->
                if (task.id == taskToToggle.id) {
                    task.copy(isCompleted = !task.isCompleted)
                } else {
                    task
                }
            }

            _tasks.value = newList
            saveTasksToDataStore(appContext, newList)
        }
    }

    fun setShowCompleted(show: Boolean) {
        _showCompletedTasks.value = show
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            val currentList = _tasks.value
            val tasksToKeep = currentList.filter { !it.isCompleted }

            if (tasksToKeep.size == currentList.size) return@launch // No completed tasks

            _tasks.value = tasksToKeep
            saveTasksToDataStore(appContext, tasksToKeep)
            _operationResult.value = "Completed tasks cleared locally."
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            if (_tasks.value.isEmpty()) return@launch

            val emptyList = emptyList<Task>()
            _tasks.value = emptyList
            saveTasksToDataStore(appContext, emptyList)
            _operationResult.value = "All tasks cleared locally."
        }
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }

    private suspend fun saveTasksToDataStore(context: Context, tasks: List<Task>) {
        val json = gson.toJson(tasks)
        context.dataStore.edit { prefs ->
            prefs[TASKS_KEY] = json
        }
    }

    private fun loadTasksFromDataStore(context: Context): Flow<List<Task>> {
        return context.dataStore.data
            .map { prefs ->
                val json = prefs[TASKS_KEY] ?: "[]"
                try {
                    gson.fromJson<List<Task>>(json, object : TypeToken<List<Task>>() {}.type) ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<Task>()
                }
            }
            .catch { exception ->
                exception.printStackTrace()
                emit(emptyList<Task>())
            }
    }
}