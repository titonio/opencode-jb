package com.opencode.test

import com.google.gson.Gson
import com.opencode.model.SessionInfo
import com.opencode.model.SessionResponse
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.TimeUnit

/**
 * Mock HTTP server for testing OpenCode API interactions.
 * Wraps MockWebServer with OpenCode-specific convenience methods.
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
