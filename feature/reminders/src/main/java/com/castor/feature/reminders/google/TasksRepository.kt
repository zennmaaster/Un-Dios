package com.castor.feature.reminders.google

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that bridges the Google Tasks API with local state.
 *
 * Provides reactive [StateFlow] streams of task lists and active (incomplete) tasks,
 * along with methods to create, complete, and sync tasks.
 */
@Singleton
class TasksRepository @Inject constructor(
    private val tasksApi: GoogleTasksApi,
    private val authManager: GoogleAuthManager
) {
    companion object {
        private const val TAG = "TasksRepository"

        /** The default task list identifier used by Google Tasks. */
        private const val DEFAULT_TASK_LIST = "@default"
    }

    private val _taskLists = MutableStateFlow<List<TaskList>>(emptyList())

    /** All task lists for the authenticated user. */
    val taskLists: StateFlow<List<TaskList>> = _taskLists.asStateFlow()

    private val _activeTasks = MutableStateFlow<List<GoogleTask>>(emptyList())

    /** Active (incomplete) tasks from the default list, sorted by position. */
    val activeTasks: StateFlow<List<GoogleTask>> = _activeTasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    /** Whether a sync operation is currently in progress. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)

    /** The most recent sync error, or null if the last sync succeeded. */
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    /** The currently selected task list ID. Defaults to "@default". */
    private var activeTaskListId: String = DEFAULT_TASK_LIST

    /**
     * Fetches task lists and active tasks from Google Tasks.
     *
     * Retrieves all task lists, then fetches incomplete tasks from the
     * currently active task list. Results are emitted to [taskLists] and [activeTasks].
     */
    suspend fun syncTasks() {
        val auth = authManager.getAuthorizationHeader()
        if (auth == null) {
            _syncError.value = "Not authenticated with Google"
            return
        }

        _isLoading.value = true
        _syncError.value = null

        try {
            // Fetch task lists
            val listsResponse = tasksApi.getTaskLists(auth = auth)
            if (listsResponse.isSuccessful) {
                val lists = listsResponse.body()?.items.orEmpty()
                _taskLists.value = lists
                Log.d(TAG, "Fetched ${lists.size} task lists")

                // Use the first list's ID if we're on default and lists are available
                if (activeTaskListId == DEFAULT_TASK_LIST && lists.isNotEmpty()) {
                    activeTaskListId = lists.first().id
                }
            } else {
                Log.e(TAG, "Task lists fetch failed: ${listsResponse.code()}")
            }

            // Fetch tasks from the active list
            val tasksResponse = tasksApi.getTasks(
                auth = auth,
                taskListId = activeTaskListId,
                showCompleted = false
            )

            if (tasksResponse.isSuccessful) {
                val tasks = tasksResponse.body()?.items.orEmpty()
                _activeTasks.value = tasks
                Log.d(TAG, "Fetched ${tasks.size} active tasks")
            } else {
                val errorBody = tasksResponse.errorBody()?.string()
                _syncError.value = "Tasks sync failed: ${tasksResponse.code()} ${errorBody.orEmpty()}"
                Log.e(TAG, "Tasks API error: ${tasksResponse.code()} $errorBody")
            }
        } catch (e: Exception) {
            _syncError.value = "Tasks sync failed: ${e.message}"
            Log.e(TAG, "Tasks sync exception", e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Switches the active task list and re-syncs tasks.
     *
     * @param taskListId The task list ID to switch to
     */
    suspend fun switchTaskList(taskListId: String) {
        activeTaskListId = taskListId
        syncTasks()
    }

    /**
     * Creates a new task in the active task list.
     *
     * @param title The task title (required)
     * @param notes Optional notes / description
     * @param dueDate Optional due date in RFC3339 format (e.g., "2026-02-20T00:00:00.000Z")
     * @return The created [GoogleTask], or null on failure
     */
    suspend fun createTask(
        title: String,
        notes: String? = null,
        dueDate: String? = null
    ): GoogleTask? {
        val auth = authManager.getAuthorizationHeader() ?: return null

        val request = GoogleTaskRequest(
            title = title,
            notes = notes,
            due = dueDate,
            status = "needsAction"
        )

        return try {
            val response = tasksApi.createTask(
                auth = auth,
                taskListId = activeTaskListId,
                task = request
            )
            if (response.isSuccessful) {
                val task = response.body()
                Log.d(TAG, "Created task: ${task?.id}")
                // Re-sync to update flows
                syncTasks()
                task
            } else {
                Log.e(TAG, "Create task failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create task exception", e)
            null
        }
    }

    /**
     * Marks a task as completed.
     *
     * @param taskListId The task list containing the task
     * @param taskId The task to mark complete
     * @return true if the operation succeeded
     */
    suspend fun completeTask(taskListId: String, taskId: String): Boolean {
        val auth = authManager.getAuthorizationHeader() ?: return false

        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val request = GoogleTaskRequest(
            status = "completed",
            completed = now
        )

        return try {
            val response = tasksApi.updateTask(
                auth = auth,
                taskListId = taskListId,
                taskId = taskId,
                task = request
            )
            if (response.isSuccessful) {
                Log.d(TAG, "Completed task: $taskId")
                // Re-sync to remove completed task from active list
                syncTasks()
                true
            } else {
                Log.e(TAG, "Complete task failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Complete task exception", e)
            false
        }
    }

    /**
     * Deletes a task from a task list.
     *
     * @param taskListId The task list containing the task
     * @param taskId The task to delete
     * @return true if the deletion succeeded
     */
    suspend fun deleteTask(taskListId: String, taskId: String): Boolean {
        val auth = authManager.getAuthorizationHeader() ?: return false

        return try {
            val response = tasksApi.deleteTask(
                auth = auth,
                taskListId = taskListId,
                taskId = taskId
            )
            if (response.isSuccessful || response.code() == 204) {
                Log.d(TAG, "Deleted task: $taskId")
                syncTasks()
                true
            } else {
                Log.e(TAG, "Delete task failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete task exception", e)
            false
        }
    }
}
