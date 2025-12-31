package com.opencode.test

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Utilities for mocking Process and ProcessBuilder for CLI testing.
 */
object MockProcessBuilder {

    /**
     * Create a mock Process that simulates successful CLI execution.
     */
    fun createSuccessfulProcess(
        exitCode: Int = 0,
        output: String = "OpenCode CLI version 1.0.0"
    ): Process {
        val process = mock<Process>()
        val outputStream: InputStream = ByteArrayInputStream(output.toByteArray())

        whenever(process.inputStream).thenReturn(outputStream)
        whenever(process.errorStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(process.waitFor(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(true)
        whenever(process.exitValue()).thenReturn(exitCode)
        whenever(process.isAlive).thenReturn(false)

        return process
    }

    /**
     * Create a mock Process that simulates CLI not found.
     */
    fun createFailedProcess(
        exitCode: Int = 1,
        errorOutput: String = "command not found"
    ): Process {
        val process = mock<Process>()
        val errorStream: InputStream = ByteArrayInputStream(errorOutput.toByteArray())

        whenever(process.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(process.errorStream).thenReturn(errorStream)
        whenever(process.waitFor(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(true)
        whenever(process.exitValue()).thenReturn(exitCode)
        whenever(process.isAlive).thenReturn(false)

        return process
    }

    /**
     * Create a mock Process that simulates timeout.
     */
    fun createTimeoutProcess(): Process {
        val process = mock<Process>()

        whenever(process.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(process.errorStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(process.waitFor(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(false)
        whenever(process.isAlive).thenReturn(true)

        return process
    }

    /**
     * Create a mock Process that simulates a running server process.
     */
    fun createServerProcess(port: Int = 12345): Process {
        val process = mock<Process>()
        val output = "OpenCode server started on port $port"
        val outputStream: InputStream = ByteArrayInputStream(output.toByteArray())

        whenever(process.inputStream).thenReturn(outputStream)
        whenever(process.errorStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(process.isAlive).thenReturn(true)
        whenever(process.waitFor()).thenAnswer {
            Thread.sleep(Long.MAX_VALUE) // Simulate long-running process
            0
        }

        return process
    }

    /**
     * Create a mock ProcessBuilder that returns the given process.
     */
    fun createMockProcessBuilder(process: Process): ProcessBuilder {
        val builder = mock<ProcessBuilder>()
        whenever(builder.start()).thenReturn(process)
        whenever(builder.command(org.mockito.kotlin.any<List<String>>())).thenReturn(builder)
        whenever(builder.directory(org.mockito.kotlin.any<java.io.File>())).thenReturn(builder)
        whenever(builder.redirectErrorStream(org.mockito.kotlin.any<Boolean>())).thenReturn(builder)
        return builder
    }
}
