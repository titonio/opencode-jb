package com.opencode.test

import com.google.gson.Gson
import com.opencode.model.SessionInfo
import com.opencode.model.SessionResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.TimeUnit

/**
 * Mock HTTP server for testing OpenCode API interactions.
 * Wraps MockWebServer with OpenCode-specific convenience methods.
 * 
 * Supports two modes:
 * 1. Queue mode (default): Enqueue responses in order
 * 2. Dispatcher mode: Handle requests dynamically based on path/method
 */
class MockOpenCodeServer {
    
    private val server = MockWebServer()
    private val gson = Gson()
    
    val port: Int
        get() = server.port
    
    val url: String
        get() = server.url("/").toString().trimEnd('/')
    
    /**
     * Start the mock server.
     */
    fun start() {
        server.start()
    }
    
    /**
     * Shutdown the mock server.
     */
    fun shutdown() {
        server.shutdown()
    }
    
    /**
     * Set up a smart dispatcher that handles all standard API requests.
     * This is more robust than queueing responses as it handles requests in any order.
     * 
     * @param sessions Sessions to return for GET /session
     * @param getSessionResponse Specific session to return for GET /session/{id}
     * @param createResponse Response for POST /session
     * @param deleteSuccess Success state for DELETE /session/{id}
     * @param shareSessionResponse Session to return after sharing (POST /session/{id}/share)
     * @param shareUrl Share URL for POST /session/{id}/share (used if shareSessionResponse is null)
     * @param unshareSuccess Success state for DELETE /session/{id}/share
     */
    fun setupSmartDispatcher(
        sessions: List<SessionInfo> = emptyList(),
        getSessionResponse: SessionInfo? = null,
        createResponse: SessionResponse? = null,
        deleteSuccess: Boolean = true,
        shareSessionResponse: SessionInfo? = null,
        shareUrl: String? = null,
        unshareSuccess: Boolean = true
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return errorResponse(404, "Not Found")
                val method = request.method ?: "GET"
                
                // Strip query parameters for easier matching
                val pathWithoutQuery = path.split('?')[0]
                
                return when {
                    // GET /session - List sessions
                    pathWithoutQuery == "/session" && method == "GET" -> {
                        successResponse(gson.toJson(sessions))
                    }
                    
                    // GET /session/{id} - Get specific session
                    pathWithoutQuery.matches(Regex("/session/[^/]+")) && method == "GET" -> {
                        if (getSessionResponse != null) {
                            successResponse(gson.toJson(getSessionResponse))
                        } else {
                            errorResponse(404, "Session not found")
                        }
                    }
                    
                    // POST /session - Create session
                    pathWithoutQuery == "/session" && method == "POST" -> {
                        if (createResponse != null) {
                            successResponse(gson.toJson(createResponse))
                        } else {
                            errorResponse(500, "Create response not configured")
                        }
                    }
                    
                    // DELETE /session/{id} - Delete session
                    pathWithoutQuery.startsWith("/session/") && method == "DELETE" && !pathWithoutQuery.endsWith("/share") -> {
                        if (deleteSuccess) {
                            successResponse("{}")
                        } else {
                            errorResponse(500, "Delete failed")
                        }
                    }
                    
                    // POST /session/{id}/share - Share session
                    pathWithoutQuery.matches(Regex("/session/[^/]+/share")) && method == "POST" -> {
                        when {
                            shareSessionResponse != null -> successResponse(gson.toJson(shareSessionResponse))
                            shareUrl != null -> successResponse("""{"url":"$shareUrl"}""")
                            else -> errorResponse(500, "Share failed")
                        }
                    }
                    
                    // DELETE /session/{id}/share - Unshare session
                    pathWithoutQuery.matches(Regex("/session/[^/]+/share")) && method == "DELETE" -> {
                        if (unshareSuccess) {
                            successResponse("{}")
                        } else {
                            errorResponse(500, "Unshare failed")
                        }
                    }
                    
                    else -> errorResponse(404, "Not Found: $method $path")
                }
            }
            
            private fun successResponse(body: String): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setBody(body)
                    .addHeader("Content-Type", "application/json")
            }
            
            private fun errorResponse(code: Int, message: String): MockResponse {
                return MockResponse()
                    .setResponseCode(code)
                    .setBody("""{"error":"$message"}""")
                    .addHeader("Content-Type", "application/json")
            }
        }
    }
    
    /**
     * Enqueue a successful session list response.
     */
    fun enqueueSessionList(sessions: List<SessionInfo>) {
        val json = gson.toJson(sessions)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )
    }
    
    /**
     * Enqueue a successful session creation response.
     */
    fun enqueueSessionCreate(response: SessionResponse) {
        val json = gson.toJson(response)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )
    }
    
    /**
     * Enqueue a successful session detail response.
     */
    fun enqueueSession(session: SessionInfo) {
        val json = gson.toJson(session)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )
    }
    
    /**
     * Enqueue a successful empty response (for DELETE, etc.).
     */
    fun enqueueSuccess() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .addHeader("Content-Type", "application/json")
        )
    }
    
    /**
     * Enqueue an error response.
     */
    fun enqueueError(code: Int = 500, message: String = "Internal Server Error") {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setBody("""{"error":"$message"}""")
                .addHeader("Content-Type", "application/json")
        )
    }
    
    /**
     * Enqueue a network error (socket timeout).
     */
    fun enqueueTimeout() {
        server.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE)
        )
    }
    
    /**
     * Enqueue malformed JSON response.
     */
    fun enqueueMalformedJson() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{invalid json")
                .addHeader("Content-Type", "application/json")
        )
    }
    
    /**
     * Take and return the next request received by the server.
     */
    fun takeRequest(timeout: Long = 1, unit: TimeUnit = TimeUnit.SECONDS): RecordedRequest? {
        return server.takeRequest(timeout, unit)
    }
    
    /**
     * Get the number of requests received.
     */
    fun getRequestCount(): Int {
        return server.requestCount
    }
}
