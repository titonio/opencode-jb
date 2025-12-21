package com.opencode.model

import com.google.gson.annotations.SerializedName

/**
 * Session information from OpenCode API.
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
    val isShared: Boolean get() = share != null
    val shareUrl: String? get() = share?.url
}

data class TimeInfo(
    @SerializedName("created")
    val created: Long,
    
    @SerializedName("updated")
    val updated: Long,
    
    @SerializedName("archived")
    val archived: Long? = null
)

data class ShareInfo(
    @SerializedName("url")
    val url: String
)

/**
 * Request body for creating a session.
 */
data class CreateSessionRequest(
    @SerializedName("title")
    val title: String
)

/**
 * Response from session creation.
 */
data class SessionResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("directory")
    val directory: String
)
