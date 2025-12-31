package com.opencode.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a session in the OpenCode system.
 *
 * Contains all metadata about a session including ID, title, directory path,
 * project ID, timestamps, and optional sharing information.
 *
 * @property id Unique session identifier
 * @property title Human-readable session title
 * @property directory Directory path for the session
 * @property projectID ID of the project this session belongs to
 * @property time Timestamps for session lifecycle events
 * @property share Optional sharing information if session is shared
 */
data class SessionInfo(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("directory")
    val directory: String,

    @SerializedName("projectID")
    val projectID: String,

    @SerializedName("time")
    val time: TimeInfo,

    @SerializedName("share")
    val share: ShareInfo? = null
) {
    /** Indicates whether this session has been shared with others. */
    val isShared: Boolean get() = share != null

    /** Returns the share URL if this session is shared, null otherwise. */
    val shareUrl: String? get() = share?.url
}

/**
 * Timestamp information for a session.
 *
 * Contains Unix epoch timestamps (in milliseconds) for session lifecycle events.
 *
 * @property created Unix timestamp when the session was created
 * @property updated Unix timestamp when the session was last updated
 * @property archived Optional Unix timestamp when the session was archived, null if active
 */
data class TimeInfo(
    @SerializedName("created")
    val created: Long,

    @SerializedName("updated")
    val updated: Long,

    @SerializedName("archived")
    val archived: Long? = null
)

/**
 * Information about a shared session.
 *
 * Contains the URL that can be used to access a shared session.
 *
 * @property url URL to access the shared session
 */
data class ShareInfo(
    @SerializedName("url")
    val url: String
)

/**
 * Request body for creating a new session.
 *
 * Used when making API requests to create a new OpenCode session.
 *
 * @property title Title for the new session (optional in API, required here)
 */
data class CreateSessionRequest(
    @SerializedName("title")
    val title: String
)

/**
 * Response from creating a new session.
 *
 * Contains the basic information about a newly created session returned by the API.
 *
 * @property id Unique identifier of the created session
 * @property title Title of the created session
 * @property directory Directory path of the created session
 */
data class SessionResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("directory")
    val directory: String
)
