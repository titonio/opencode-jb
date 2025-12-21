package com.opencode.test

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.opencode.service.OpenCodeService
import org.mockito.Mockito
import org.mockito.kotlin.mock

/**
 * Base test class for OpenCode plugin tests.
 * Extends BasePlatformTestCase to get IntelliJ Platform test infrastructure.
 */
abstract class OpenCodeTestBase : BasePlatformTestCase() {
    
    /**
     * Create a mock OpenCodeService for testing.
     */
    protected fun createMockService(): OpenCodeService {
        return mock()
    }
    
    /**
     * Get the test data path for loading test resources.
     */
    override fun getTestDataPath(): String {
        return "src/test/resources/testdata"
    }
    
    /**
     * Ensure proper cleanup in tearDown.
     */
    override fun tearDown() {
        try {
            // Subclass-specific cleanup can go here
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
}
