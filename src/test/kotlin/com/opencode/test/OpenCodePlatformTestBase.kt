package com.opencode.test

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.opencode.service.OpenCodeService

/**
 * Base class for OpenCode plugin tests that require IntelliJ Platform infrastructure.
 *
 * This class extends BasePlatformTestCase to provide:
 * - A real IntelliJ project with file system support
 * - VFS (Virtual File System) operations
 * - Editor and PSI (Program Structure Interface) support
 * - Service injection and dependency management
 *
 * Usage:
 * ```kotlin
 * class MyPlatformTest : OpenCodePlatformTestBase() {
 *     fun `test something with platform`() {
 *         val file = createTestFile("test.txt", "content")
 *         // Use project, myFixture, etc.
 *     }
 * }
 * ```
 *
 * Available properties:
 * - `project` - The test project instance
 * - `myFixture` - CodeInsightTestFixture for code operations
 * - `testDataPath` - Path to test data files
 *
 * Note: These tests run slower than unit tests because they initialize
 * the full IntelliJ platform environment. Use them only when necessary.
 */
abstract class OpenCodePlatformTestBase : BasePlatformTestCase() {

    /**
     * Path to test data directory.
     * Override this if you need a different path.
     */
    override fun getTestDataPath(): String {
        return "src/test/resources/testdata"
    }

    /**
     * Helper to get OpenCodeService with a mock server manager.
     * Useful for testing service interactions without real server processes.
     */
    protected fun getOpenCodeServiceWithMockServer(mockPort: Int = 3000): OpenCodeService {
        val mockServerManager = MockServerManager(mockPort = mockPort, shouldSucceed = true)
        return OpenCodeService(project, mockServerManager)
    }

    /**
     * Helper to create a file in the test project.
     * Returns the PsiFile, use .virtualFile to get VirtualFile.
     */
    protected fun createTestFile(relativePath: String, content: String = "") =
        myFixture.addFileToProject(relativePath, content)

    /**
     * Helper to create a directory in the test project.
     */
    protected fun createTestDir(relativePath: String) {
        val parts = relativePath.split("/")
        var currentPath = ""
        for (part in parts) {
            currentPath += if (currentPath.isEmpty()) part else "/$part"
            myFixture.tempDirFixture.findOrCreateDir(currentPath)
        }
    }

    /**
     * Helper to open a file in the editor.
     *
     * @param relativePath Path relative to project root
     * @return The opened PsiFile
     */
    protected fun openFileInEditor(relativePath: String) =
        myFixture.configureFromTempProjectFile(relativePath)
}
