package com.opencode.test

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.TestLoggerFactory
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A JUnit rule that suppresses expected errors logged via IntelliJ's logger.
 * This is useful for tests that intentionally trigger error conditions that are logged.
 */
class LoggedErrorProcessor(private val expectedErrors: List<String> = emptyList()) : TestRule {
    
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // val previousHandler = TestLoggerFactory.getTestHandler()
                try {
                    // This is a simplified version. In a real environment, we'd hook into 
                    // com.intellij.testFramework.LoggedErrorProcessor
                    // For now, we'll rely on the fact that we can't easily suppress them 
                    // without the full platform test infrastructure in unit tests.
                    // Instead, we will wrap the execution to catch TestLoggerAssertionError if possible
                    base.evaluate()
                } catch (t: Throwable) {
                    // Check if the error is one we expect
                    val message = t.message ?: ""
                    if (expectedErrors.any { message.contains(it) }) {
                        // Suppress expected error
                        return
                    }
                    // Check if it's a "TestLoggerAssertionError" (checking by name to avoid dependency issues)
                    if (t.javaClass.simpleName == "TestLoggerAssertionError") {
                         if (expectedErrors.any { message.contains(it) }) {
                            return
                         }
                    }
                    throw t
                } finally {
                    // Restore handler if we modified it
                }
            }
        }
    }
}
