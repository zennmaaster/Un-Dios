package com.castor.feature.reminders.google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Google Tasks API v1.
 *
 * All endpoints require a valid Bearer token in the Authorization header.
 * Base URL: https://www.googleapis.com/
 *
 * @see <a href="https://developers.google.com/tasks/reference/rest">Tasks API Reference</a>
 */
interface GoogleTasksApi {

    /**
     * Returns all the authenticated user's task lists.
     *
     * @param auth Bearer token (e.g., "Bearer ya29.xxx")
     */
    @GET("tasks/v1/users/@me/lists")
    suspend fun getTaskLists(
        @Header("Authorization") auth: String
    ): Response<TaskListsResponse>

    /**
     * Returns all tasks in the specified task list.
     *
     * @param auth Bearer token
     * @param taskListId The task list identifier (use "@default" for the default list)
     * @param showCompleted Whether to include completed tasks
     * @param showHidden Whether to include hidden/deleted tasks
     * @param maxResults Maximum number of tasks returned
     */
    @GET("tasks/v1/lists/{taskListId}/tasks")
    suspend fun getTasks(
        @Header("Authorization") auth: String,
        @Path("taskListId") taskListId: String,
        @Query("showCompleted") showCompleted: Boolean = false,
        @Query("showHidden") showHidden: Boolean = false,
        @Query("maxResults") maxResults: Int = 100
    ): Response<TasksResponse>

    /**
     * Creates a new task in the specified task list.
     *
     * @param auth Bearer token
     * @param taskListId The task list identifier
     * @param task The task to create
     */
    @POST("tasks/v1/lists/{taskListId}/tasks")
    suspend fun createTask(
        @Header("Authorization") auth: String,
        @Path("taskListId") taskListId: String,
        @Body task: GoogleTaskRequest
    ): Response<GoogleTask>

    /**
     * Updates an existing task (partial update via PATCH).
     *
     * @param auth Bearer token
     * @param taskListId The task list identifier
     * @param taskId The task identifier
     * @param task The fields to update
     */
    @PATCH("tasks/v1/lists/{taskListId}/tasks/{taskId}")
    suspend fun updateTask(
        @Header("Authorization") auth: String,
        @Path("taskListId") taskListId: String,
        @Path("taskId") taskId: String,
        @Body task: GoogleTaskRequest
    ): Response<GoogleTask>

    /**
     * Deletes a task from the specified task list.
     *
     * @param auth Bearer token
     * @param taskListId The task list identifier
     * @param taskId The task identifier
     */
    @DELETE("tasks/v1/lists/{taskListId}/tasks/{taskId}")
    suspend fun deleteTask(
        @Header("Authorization") auth: String,
        @Path("taskListId") taskListId: String,
        @Path("taskId") taskId: String
    ): Response<Unit>
}

// =============================================================================
// Response Data Classes
// =============================================================================

/**
 * Response wrapper for the task lists endpoint.
 */
@Serializable
data class TaskListsResponse(
    @SerialName("kind") val kind: String? = null,
    @SerialName("items") val items: List<TaskList> = emptyList()
)

/**
 * Represents a Google Tasks task list.
 */
@Serializable
data class TaskList(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("updated") val updated: String? = null,
    @SerialName("selfLink") val selfLink: String? = null
)

/**
 * Response wrapper for the tasks list endpoint.
 */
@Serializable
data class TasksResponse(
    @SerialName("kind") val kind: String? = null,
    @SerialName("items") val items: List<GoogleTask> = emptyList()
)

/**
 * Represents a single Google Task.
 */
@Serializable
data class GoogleTask(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("notes") val notes: String? = null,
    @SerialName("due") val due: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("completed") val completed: String? = null,
    @SerialName("updated") val updated: String? = null,
    @SerialName("selfLink") val selfLink: String? = null,
    @SerialName("parent") val parent: String? = null,
    @SerialName("position") val position: String? = null,
    @SerialName("links") val links: List<TaskLink> = emptyList()
) {
    /** Returns true if this task has been completed. */
    val isCompleted: Boolean
        get() = status == "completed"
}

/**
 * Represents a link attached to a task.
 */
@Serializable
data class TaskLink(
    @SerialName("type") val type: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("link") val link: String? = null
)

// =============================================================================
// Request Data Classes
// =============================================================================

/**
 * Request body for creating or updating a Google Task.
 *
 * @param title The task title (required for creation)
 * @param notes Optional notes / description
 * @param due Optional due date in RFC3339 format (e.g., "2026-02-20T00:00:00.000Z")
 * @param status Task status: "needsAction" or "completed"
 * @param completed Completion date in RFC3339 format (set when marking complete)
 */
@Serializable
data class GoogleTaskRequest(
    @SerialName("title") val title: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("due") val due: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("completed") val completed: String? = null
)
