package com.opencode.test

import com.google.gson.Gson
import com.opencode.model.SessionResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Debug test to verify MockOpenCodeServer behavior.
 */
class MockServerDebugTest {

    private lateinit var mockServer: MockOpenCodeServer
    private val client = OkHttpClient()
    private val gson = Gson()

    @BeforeEach
    fun setUp() {
        mockServer = MockOpenCodeServer()
        mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `verify SessionResponse JSON format`() {
        // Arrange
        val response = TestDataFactory.createSessionResponse(
            id = "test-id",
            title = "Test Title",
            directory = "/test/path"
        )

        mockServer.enqueueSessionCreate(response)

        // Act - Make HTTP request
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("${mockServer.url}/session")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { httpResponse ->
            val body = httpResponse.body?.string()
            println("Response body: $body")

            // Verify it's a valid JSON object
            assertNotNull(body)
            assertTrue(body!!.startsWith("{"), "Expected JSON object to start with {, got: $body")
            assertTrue(body.contains("\"id\""), "Expected id field in response")

            // Verify it can be parsed as SessionResponse
            val parsed = gson.fromJson(body, SessionResponse::class.java)
            assertEquals("test-id", parsed.id)
            assertEquals("Test Title", parsed.title)
        }
    }

    @Test
    fun `verify MockWebServer is reachable from OkHttpClient`() {
        // Arrange
        mockServer.enqueueSessionList(emptyList())

        println("DEBUG: Mock server URL: ${mockServer.url}")
        println("DEBUG: Mock server port: ${mockServer.port}")

        // Act - Use the same OkHttpClient settings as OpenCodeService
        val client = OkHttpClient.Builder()
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("http://localhost:${mockServer.port}/session")
            .get()
            .build()

        println("DEBUG: Making request to: http://localhost:${mockServer.port}/session")

        client.newCall(request).execute().use { response ->
            println("DEBUG: Response code: ${response.code}")
            println("DEBUG: Response successful: ${response.isSuccessful}")
            val body = response.body?.string()
            println("DEBUG: Response body: $body")

            // Assert
            assertTrue(response.isSuccessful)
            assertEquals("[]", body)
        }
    }
}
