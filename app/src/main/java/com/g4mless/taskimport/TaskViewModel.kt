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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.Exception
import com.google.firebase.firestore.ListenerRegistration

data class Task (
    val id: Int = 0,
    val name: String = "",
    val importance: Int = 1,
    var isCompleted: Boolean = false,
    val createdAt: Long? = null // New field for creation timestamp, nullable for backward compatibility
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
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    private var firestoreListener: ListenerRegistration? = null
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
        setupFirestoreListenerBasedOnAuth()
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
                    val validTasks = importedTasks
                    _tasks.value = validTasks
                    saveTasksToDataStore(appContext, validTasks)
                    val collectionRef = getUserTasksCollection()
                    if (collectionRef != null) {
                        val batch = db.batch()
                        validTasks.forEach { task ->
                            val docRef = collectionRef.document(task.id.toString())
                            batch.set(docRef, task)
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                _operationResult.value = "Tasks imported successfully & synced!"
                                println("Firestore: Imported tasks synced.")
                            }
                            .addOnFailureListener { e ->
                                _operationResult.value = "Tasks imported locally, but sync failed: ${e.message}"
                                println("Firestore Error syncing imported tasks: ${e.message}")
                            }

                    } else {
                        _operationResult.value = "Tasks imported locally (user not logged in)."
                    }

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

    private fun startFirestoreListener() {
        stopFirestoreListener()

        val collectionRef = getUserTasksCollection() ?: return

        firestoreListener = collectionRef.addSnapshotListener { snapshots, error ->
            if (error != null) {
                println("Firestore Listen error: ${error.message}")
                _operationResult.value = "Error listening for task updates: ${error.message}"
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val firestoreTasks = snapshots.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Task::class.java)?.copy(id = doc.id.toIntOrNull() ?: -1)
                    } catch (e: Exception) {
                        println("Error converting Firestore document ${doc.id}: ${e.message}")
                        null
                    }
                }.filter { it.id != -1 }

                println("Firestore Listener: Received ${firestoreTasks.size} tasks.")

                viewModelScope.launch {
                    if (!areTaskListsEqual(_tasks.value, firestoreTasks)) {
                        println("Firestore Listener: Updating local DataStore with Firestore data.")
                        _tasks.value = firestoreTasks.sortedByDescending { it.importance }
                        saveTasksToDataStore(appContext, _tasks.value)
                    } else {
                        println("Firestore Listener: Local data is already up-to-date.")
                    }
                }
            }
        }
        println("Firestore Listener started.")
    }

    fun setupFirestoreListenerBasedOnAuth() {
        if (auth.currentUser != null) {
            startFirestoreListener()
        } else {
            stopFirestoreListener()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopFirestoreListener()
    }

    private fun areTaskListsEqual(list1: List<Task>, list2: List<Task>): Boolean {
        if (list1.size != list2.size) return false
        return list1.sortedBy { it.id } == list2.sortedBy { it.id }
    }

    private fun stopFirestoreListener() {
        firestoreListener?.remove()
        firestoreListener = null
        println("Firestore Listener stopped.")
    }

    private fun getUserTasksCollection(): CollectionReference? {
        val userId = auth.currentUser?.uid
        return if (userId != null) {
            db.collection("users").document(userId).collection("tasks")
        } else {
            null
        }
    }


    fun addTask(name: String, importance: Int) {
        viewModelScope.launch {
            val currentList = _tasks.value
            val nextId = (currentList.maxOfOrNull { it.id } ?: -1) + 1
            val newTask = Task(nextId, name, importance, isCompleted = false, createdAt = System.currentTimeMillis()) // Set createdAt for new tasks
            val newList = (currentList + newTask).sortedByDescending { it.importance }

            _tasks.value = newList
            saveTasksToDataStore(appContext, newList)

            getUserTasksCollection()?.document(newTask.id.toString())?.set(newTask)
                ?.addOnSuccessListener { println("Firestore: Task ${newTask.id} added.") }
                ?.addOnFailureListener { e -> println("Firestore Error adding task ${newTask.id}: ${e.message}") } // Log error
        }
    }

    fun updateTask(taskId: Int, newName: String, newImportance: Int) {
        viewModelScope.launch {
            var updatedTask: Task? = null
            val currentList = _tasks.value
            val newList = currentList.map { task ->
                if (task.id == taskId) {
                    updatedTask = task.copy(name = newName, importance = newImportance)
                    updatedTask!!
                } else {
                    task
                }
            }.sortedByDescending { it.importance }

            _tasks.value = newList
            saveTasksToDataStore(appContext, newList)

            updatedTask?.let { taskToSync ->
                getUserTasksCollection()?.document(taskToSync.id.toString())?.set(taskToSync)
                    ?.addOnSuccessListener { println("Firestore: Task ${taskToSync.id} updated.") }
                    ?.addOnFailureListener { e -> println("Firestore Error updating task ${taskToSync.id}: ${e.message}") }
            }
        }
    }

    fun deleteTask(taskToDelete: Task) {
        viewModelScope.launch {
            val currentList = _tasks.value
            val newList = currentList.filter { it.id != taskToDelete.id }

            _tasks.value = newList
            saveTasksToDataStore(appContext, newList)

            getUserTasksCollection()?.document(taskToDelete.id.toString())?.delete()
                ?.addOnSuccessListener { println("Firestore: Task ${taskToDelete.id} deleted.") }
                ?.addOnFailureListener { e -> println("Firestore Error deleting task ${taskToDelete.id}: ${e.message}") }
        }
    }

    fun toggleTaskCompletion(taskToToggle: Task) {
        viewModelScope.launch {
            var updatedTask: Task? = null
            val currentList = _tasks.value
            val newList = currentList.map { task ->
                if (task.id == taskToToggle.id) {
                    updatedTask = task.copy(isCompleted = !task.isCompleted)
                    updatedTask!!
                } else {
                    task
                }
            }

            _tasks.value = newList
            saveTasksToDataStore(appContext, newList)

            updatedTask?.let { taskToSync ->
                getUserTasksCollection()?.document(taskToSync.id.toString())?.set(taskToSync, SetOptions.merge())
                    ?.addOnSuccessListener { println("Firestore: Task ${taskToSync.id} toggled.") }
                    ?.addOnFailureListener { e -> println("Firestore Error toggling task ${taskToSync.id}: ${e.message}") }
            }
        }
    }

    fun setShowCompleted(show: Boolean) {
        _showCompletedTasks.value = show
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            val currentList = _tasks.value
            val tasksToKeep = currentList.filter { !it.isCompleted }
            val tasksToDelete = currentList.filter { it.isCompleted }

            if (tasksToDelete.isEmpty()) return@launch

            _tasks.value = tasksToKeep
            saveTasksToDataStore(appContext, tasksToKeep)

            val collectionRef = getUserTasksCollection()
            if (collectionRef != null) {
                val batch = db.batch()
                tasksToDelete.forEach { task ->
                    val docRef = collectionRef.document(task.id.toString())
                    batch.delete(docRef)
                }
                batch.commit()
                    .addOnSuccessListener { _operationResult.value = "Completed tasks cleared & sync started." }
                    .addOnFailureListener { e -> _operationResult.value = "Completed tasks cleared locally, sync failed: ${e.message}" }
            } else if (tasksToDelete.isNotEmpty()) {
                _operationResult.value = "Completed tasks cleared locally, sync skipped (not logged in)."
            }
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            val tasksToDelete = _tasks.value
            if (tasksToDelete.isEmpty()) return@launch

            val emptyList = emptyList<Task>()

            _tasks.value = emptyList
            saveTasksToDataStore(appContext, emptyList)

            val collectionRef = getUserTasksCollection()
            if (collectionRef != null) {
                val batch = db.batch()
                tasksToDelete.forEach { task ->
                    val docRef = collectionRef.document(task.id.toString())
                    batch.delete(docRef)
                }
                batch.commit()
                    .addOnSuccessListener { _operationResult.value = "All tasks cleared & sync started." }
                    .addOnFailureListener { e -> _operationResult.value = "All tasks cleared locally, sync failed: ${e.message}" }
            } else if (tasksToDelete.isNotEmpty()) {
                _operationResult.value = "All tasks cleared locally, sync skipped (not logged in)."
            }
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