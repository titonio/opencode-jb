# Agent Guidelines for opencode-jb

This document provides comprehensive guidelines for AI coding agents working on the OpenCode IntelliJ Plugin project. The project is a Kotlin/Java-based IntelliJ IDEA plugin that integrates OpenCode AI assistant functionality.

## Table of Contents

- [Build, Lint & Test Commands](#build-lint--test-commands)
- [Project Overview](#project-overview)
- [Code Style Guidelines](#code-style-guidelines)
- [Architecture Patterns](#architecture-patterns)
- [Testing Guidelines](#testing-guidelines)
- [Common Tasks](#common-tasks)
- [Troubleshooting](#troubleshooting)

---

## Build, Lint & Test Commands

### Setup

```bash
# No separate install step needed - Gradle manages dependencies
# Java 21 required (configured in gradle.properties)

# Clean build
./gradlew clean build

# Build plugin distribution
./gradlew buildPlugin
# Output: build/distributions/opencode-jb-1.0.0.zip
```

### Testing

```bash
# Run all tests
./gradlew test --continue

# Run specific test class
./gradlew test --tests 'com.opencode.ui.SessionListViewModelTest'

# Run specific test method (use quotes for methods with spaces)
./gradlew test --tests 'com.opencode.ui.SessionListViewModelTest.loadSessions calls service and notifies callback with results'

# Run with detailed output
./gradlew test --info

# Run tests in parallel (faster)
./gradlew test --parallel --max-workers=4

# Continue on failure (useful for comprehensive test runs)
./gradlew test --continue --stacktrace
```

### Coverage

```bash
# Generate HTML coverage report (includes verification)
./gradlew test koverHtmlReport

# View coverage report
open build/reports/kover/html/index.html  # macOS
xdg-open build/reports/kover/html/index.html  # Linux

# Generate XML coverage report
./gradlew koverXmlReport

# Verify coverage meets minimum threshold (35%)
./gradlew koverVerify
```

### Linting & Verification

```bash
# Verify plugin compatibility with target IDE versions
./gradlew verifyPlugin

# Run all verification tasks
./gradlew check

# Build and run plugin in IDE sandbox for testing
./gradlew runIde
```

---

## Project Overview

### Technology Stack

- **Language**: Kotlin 2.3.0 (JVM target: Java 21)
- **Framework**: IntelliJ Platform SDK 2025.3
- **Build Tool**: Gradle 9.2.1 with Kotlin DSL
- **Testing**: JUnit 5 (Jupiter), Mockito Kotlin, MockWebServer
- **Async**: Kotlin Coroutines 1.10.2 (Platform-bundled)
- **JSON**: Gson 2.10.1, Jackson 2.17.0
- **HTTP**: OkHttp 4.12.0
- **Coverage**: Kover 0.7.5

### Project Structure

```
src/main/kotlin/com/opencode/
├── actions/          # IDE actions (menu items, shortcuts)
├── editor/           # Custom file editor for .opencode files
├── icons/            # Icon resources
├── model/            # Data models (SessionInfo, etc.)
├── service/          # Core services (OpenCodeService, ServerManager)
├── settings/         # Plugin settings/configuration
├── toolwindow/       # Tool window UI components
├── ui/               # Reusable UI components and ViewModels
├── utils/            # Utility functions
└── vfs/              # Virtual file system for opencode:// protocol

src/test/kotlin/com/opencode/
├── [same structure]/ # Test classes mirror main structure
└── test/             # Test infrastructure (MockServerManager, etc.)

src/main/resources/
├── icons/            # SVG icons
└── META-INF/
    └── plugin.xml    # Plugin descriptor
```

### Key Components

1. **OpenCodeService** (Service Layer)
   - Project-level service managing OpenCode server lifecycle
   - Session CRUD operations (create, list, delete, share)
   - HTTP API client for OpenCode backend
   - Widget and editor registration

2. **ServerManager** (Server Management)
   - Manages OpenCode CLI process lifecycle
   - Health checking and port management
   - Process monitoring and recovery

3. **ViewModels** (Business Logic)
   - `SessionListViewModel`: Session list management
   - `OpenCodeEditorPanelViewModel`: Editor lifecycle
   - `OpenCodeToolWindowViewModel`: Tool window state
   - Fully testable without UI dependencies

4. **Virtual File System**
   - Custom VFS for `opencode://sessionId` URLs
   - Integrates with IntelliJ file system
   - Enables editor tabs for AI sessions

5. **Actions**
   - `OpenTerminalAction`: Open OpenCode in tool window
   - `OpenInEditorAction`: Open OpenCode in editor tab
   - `AddFilepathAction`: Add current file to OpenCode context
   - `SessionManagementActions`: Create, list, delete sessions

---

## Code Style Guidelines

### Naming Conventions

- **Variables/Functions**: `camelCase`
  ```kotlin
  val sessionId = "session-123"
  fun loadSessions() { }
  ```

- **Classes/Interfaces**: `PascalCase`
  ```kotlin
  class SessionListViewModel
  interface ViewCallback
  data class SessionInfo
  ```

- **Constants**: `SCREAMING_SNAKE_CASE`
  ```kotlin
  private const val CACHE_TTL = 5000L
  private const val MAX_SESSIONS_TO_KEEP = 10
  ```

- **Private properties**: Prefix with underscore is NOT used (Kotlin convention)
  ```kotlin
  // ✅ Good
  private val sessionCache = mutableMapOf<String, SessionInfo>()
  
  // ❌ Bad
  private val _sessionCache = mutableMapOf<String, SessionInfo>()
  ```

### File Organization

```kotlin
package com.opencode.ui

// 1. Imports (grouped)
import com.intellij.openapi.project.Project  // IntelliJ Platform
import com.opencode.model.SessionInfo         // Project imports
import kotlinx.coroutines.CoroutineScope      // Third-party
import org.junit.jupiter.api.Test             // Testing

// 2. Top-level logger (if needed)
private val LOG = logger<ClassName>()

// 3. Class definition with KDoc
/**
 * ViewModel for managing session list UI.
 * Separates business logic from UI for testability.
 */
class SessionListViewModel(
    private val service: OpenCodeService,
    private val scope: CoroutineScope
) {
    // 4. Inner interfaces/enums
    interface ViewCallback { }
    
    // 5. Properties (group by visibility)
    private var callback: ViewCallback? = null
    private val sessions = mutableListOf<SessionInfo>()
    
    // 6. Public methods
    fun loadSessions() { }
    
    // 7. Private methods
    private suspend fun refreshData() { }
}
```

### Imports

- **Explicit imports**: Always use explicit imports, never star imports
  ```kotlin
  // ✅ Good
  import com.intellij.openapi.project.Project
  import com.intellij.openapi.application.ApplicationManager
  
  // ❌ Bad
  import com.intellij.openapi.*
  ```

- **Import order**: IntelliJ Platform, project, third-party, testing
- **No unused imports**: Remove all unused imports before committing

### Types & Null Safety

```kotlin
// ✅ Use explicit types for public APIs
fun createSession(title: String?): String { }

// ✅ Use type inference for private/local variables
private val sessions = mutableListOf<SessionInfo>()
val result = service.loadSessions()

// ✅ Prefer nullable types over lateinit when possible
private var callback: ViewCallback? = null

// ✅ Use lateinit only when initialization is guaranteed before use
private lateinit var testScope: TestScope

// ✅ Use safe calls and elvis operator
val title = session.title ?: "Untitled"
callback?.onSessionsLoaded(sessions)

// ❌ Avoid !! (assert non-null) unless absolutely necessary
val file = findFile()!!  // Bad - can throw NPE
val file = findFile() ?: return  // Good - explicit handling
```

### Error Handling

```kotlin
// ✅ Catch specific exceptions, not generic Exception
try {
    val response = client.newCall(request).execute()
} catch (e: IOException) {
    LOG.warn("Network request failed", e)
    return emptyList()
}

// ✅ Use Result type for operations that can fail
suspend fun loadSessions(): Result<List<SessionInfo>> {
    return try {
        Result.success(fetchSessions())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// ✅ Use nullable return types for "not found" scenarios
fun getSession(id: String): SessionInfo? {
    return sessionCache[id]
}

// ✅ Log errors at appropriate level
LOG.error("Fatal error", exception)  // For unrecoverable errors
LOG.warn("Retrying after failure", exception)  // For recoverable errors
LOG.info("Operation completed")  // For normal flow
LOG.debug("Detailed state: $state")  // For debugging

// ❌ Don't swallow exceptions silently
catch (e: Exception) { }  // Bad
```

### Coroutines & Threading

```kotlin
// ✅ Use withContext for IO operations
suspend fun loadSessions(): List<SessionInfo> = withContext(Dispatchers.IO) {
    // HTTP call
}

// ✅ Pass CoroutineScope via constructor for testability
class SessionListViewModel(
    private val service: OpenCodeService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
)

// ✅ Use launch for fire-and-forget
fun createSession(title: String) {
    scope.launch {
        try {
            service.createSession(title)
        } catch (e: Exception) {
            callback?.onError(e.message ?: "Unknown error")
        }
    }
}

// ✅ Use suspend functions for async operations
suspend fun createSession(title: String): String

// ❌ Don't block the EDT
runBlocking { longOperation() }  // Bad in UI code

// ✅ Use runInEdtAndWait for UI updates in tests
protected fun <T> runInEdtAndGet(action: () -> T): T {
    return if (ApplicationManager.getApplication().isDispatchThread) {
        action()
    } else {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait {
            result = action()
        }
        result!!
    }
}
```

### Data Classes

```kotlin
// ✅ Use data classes for models
data class SessionInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("time")
    val time: TimeInfo
) {
    // ✅ Computed properties allowed
    val isActive: Boolean get() = time.archived == null
}

// ✅ Use default parameters for optional fields
data class CreateSessionRequest(
    @SerializedName("title")
    val title: String? = null
)

// ❌ Don't put mutable state in data classes
data class Session(
    val id: String,
    var isActive: Boolean  // Bad - prefer immutable
)
```

### Companion Objects & Constants

```kotlin
class OpenCodeService {
    companion object {
        // ✅ Public constants
        const val DEFAULT_PORT = 8080
        
        // ✅ Private constants
        private const val CACHE_TTL = 5000L
        private const val MAX_RETRIES = 3
    }
    
    // ❌ Don't use companion object for instance factories
    // Use constructor instead
}

// ✅ Top-level constants for shared values
private const val SESSION_ID_PREFIX = "session-"
private val LOG = logger<ClassName>()
```

### Comments & Documentation

```kotlin
/**
 * Loads sessions from the OpenCode service.
 * 
 * @param forceRefresh Whether to bypass cache and fetch from server
 * @return List of sessions, sorted by last updated time
 * @throws IOException if network request fails
 */
suspend fun loadSessions(forceRefresh: Boolean = true): List<SessionInfo>

// ✅ Use KDoc for public APIs
// ✅ Use comments for complex logic
// ✅ Avoid obvious comments
val sessions = loadSessions()  // ❌ Bad: Loads sessions
val sessions = loadSessions()  // ✅ Good: no comment needed

// ✅ Explain WHY, not WHAT
// Wait 1 second to handle tab drag/split scenarios where editor
// is briefly disposed before new one is created
Thread.sleep(1000)
```

---

## Architecture Patterns

### ViewModel Pattern ⭐ (Preferred)

The project uses the ViewModel pattern to separate business logic from UI, enabling comprehensive unit testing without platform dependencies.

```kotlin
// ViewModel structure
class SessionListViewModel(
    private val service: OpenCodeService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    /**
     * Callback interface for view updates.
     * View implements this to receive notifications.
     */
    interface ViewCallback {
        fun onSessionsLoaded(sessions: List<SessionInfo>)
        fun onError(message: String)
        fun onSuccess(message: String)
    }
    
    private var callback: ViewCallback? = null
    private val sessions = mutableListOf<SessionInfo>()
    
    // ViewModel manages state
    fun loadSessions(forceRefresh: Boolean = true) {
        scope.launch {
            try {
                val loaded = service.listSessions(forceRefresh)
                sessions.clear()
                sessions.addAll(loaded)
                callback?.onSessionsLoaded(loaded)
            } catch (e: Exception) {
                callback?.onError("Failed to load: ${e.message}")
            }
        }
    }
    
    // View sets callback to receive updates
    fun setCallback(callback: ViewCallback) {
        this.callback = callback
    }
}

// View implements callback
class SessionListDialog : DialogWrapper(), SessionListViewModel.ViewCallback {
    private val viewModel = SessionListViewModel(service, scope)
    
    init {
        viewModel.setCallback(this)
        viewModel.loadSessions()
    }
    
    override fun onSessionsLoaded(sessions: List<SessionInfo>) {
        // Update UI on EDT
        ApplicationManager.getApplication().invokeLater {
            listModel.clear()
            sessions.forEach { listModel.addElement(it) }
        }
    }
}
```

**Benefits**:
- Business logic 100% testable without UI
- Clear separation of concerns
- Easy to maintain and extend
- Consistent pattern across codebase

### Service Pattern

```kotlin
@Service(Service.Level.PROJECT)
class OpenCodeService(private val project: Project) {
    // Lazy initialization
    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    
    // Caching
    private val sessionCache = mutableMapOf<String, SessionInfo>()
    private var lastCacheUpdate: Long = 0
    private val CACHE_TTL = 5000L
    
    // Public API with caching
    suspend fun listSessions(forceRefresh: Boolean = false): List<SessionInfo> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - lastCacheUpdate < CACHE_TTL) {
            return sessionCache.values.toList()
        }
        return refreshCache()
    }
    
    // Private implementation
    private suspend fun refreshCache(): List<SessionInfo> = withContext(Dispatchers.IO) {
        // Implementation
    }
}
```

### Testing Infrastructure

```kotlin
// MockServerManager for consistent server mocking
class MockServerManager(
    private val mockPort: Int = 12345,
    private val shouldSucceed: Boolean = true
) : ServerManager {
    var startServerCallCount = 0
    
    override suspend fun getOrStartServer(): Int? {
        startServerCallCount++
        return if (shouldSucceed) mockPort else null
    }
}

// TestDataFactory for consistent test data
object TestDataFactory {
    fun createSessionInfo(
        id: String = "test-session-${UUID.randomUUID()}",
        title: String = "Test Session",
        shareUrl: String? = null
    ): SessionInfo {
        return SessionInfo(
            id = id,
            title = title,
            directory = "/test/dir",
            projectID = "test-project",
            time = createTimeInfo(),
            share = shareUrl?.let { ShareInfo(it) }
        )
    }
}
```

---

## Testing Guidelines

### Test Organization

Tests mirror the main source structure:

```
src/test/kotlin/com/opencode/
├── ui/
│   ├── SessionListViewModelTest.kt              # Unit test
│   ├── SessionListDialogComponentTest.kt        # Component test
│   └── SessionListDialogPlatformTest.kt         # Platform test
├── service/
│   ├── OpenCodeServiceTest.kt                   # Core tests
│   ├── OpenCodeServiceEdgeCaseTest.kt          # Edge cases
│   └── OpenCodeServiceErrorPathTest.kt         # Error handling
└── test/
    ├── MockServerManager.kt                     # Test doubles
    ├── TestDataFactory.kt                       # Test data
    └── OpenCodePlatformTestBase.kt             # Platform test base
```

### Test Naming

```kotlin
// ✅ Use descriptive test names with backticks
@Test
fun `loadSessions calls service and notifies callback with results`() {
    // Test implementation
}

// ✅ Use categories for organization
// ========== Loading Tests ==========
@Test
fun `loadSessions with forceRefresh true bypasses cache`() { }

// ========== Error Handling Tests ==========
@Test
fun `loadSessions handles network errors gracefully`() { }
```

### Test Structure (AAA Pattern)

```kotlin
@Test
fun `createSession creates new session and notifies callback`() = runTest {
    // Arrange
    val expectedId = "new-session-123"
    whenever(mockService.createSession(any())).thenReturn(expectedId)
    whenever(mockService.listSessions(any())).thenReturn(emptyList())
    
    // Act
    viewModel.createSession("New Session")
    advanceUntilIdle()  // For coroutines
    
    // Assert
    verify(mockService).createSession("New Session")
    verify(mockCallback).onSuccess(argThat { contains("created") })
}
```

### Coroutine Testing

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {
    private lateinit var testScope: TestScope
    
    @BeforeEach
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        viewModel = SessionListViewModel(mockService, testScope)
    }
    
    @Test
    fun `async operation test`() = testScope.runTest {
        // Arrange
        whenever(mockService.loadSessions()).thenReturn(sessions)
        
        // Act
        viewModel.loadSessions()
        advanceUntilIdle()  // Let coroutines complete
        
        // Assert
        verify(mockCallback).onSessionsLoaded(sessions)
    }
}
```

### Mockito Best Practices

```kotlin
// ✅ Use Mockito Kotlin for cleaner syntax
import org.mockito.kotlin.*

// ✅ Mock creation
private val mockService = mock<OpenCodeService>()
private val mockCallback = mock<SessionListViewModel.ViewCallback>()

// ✅ Stubbing
whenever(mockService.listSessions(any())).thenReturn(sessions)

// ✅ Verification
verify(mockService).createSession("Title")
verify(mockCallback).onError(argThat { contains("Failed") })
verify(mockService, never()).deleteSession(any())

// ✅ Order verification
val inOrder = inOrder(mockCallback)
inOrder.verify(mockCallback).onStateChanged(State.INITIALIZING)
inOrder.verify(mockCallback).onStateChanged(State.RUNNING)
```

### MockWebServer Pattern

```kotlin
class OpenCodeServiceTest {
    private lateinit var mockServer: MockWebServer
    
    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
    }
    
    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }
    
    @Test
    fun `listSessions returns parsed sessions`() = runTest {
        // Arrange
        val sessionsJson = """[
            {"id": "session-1", "title": "Session 1", ...}
        ]"""
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(sessionsJson))
        
        // Act
        val sessions = service.listSessions()
        
        // Assert
        assertEquals(1, sessions.size)
        assertEquals("session-1", sessions[0].id)
    }
}
```

### Coverage Goals

- **Overall Target**: 72%+ (architectural ceiling for unit tests)
- **ViewModels**: 90%+ (currently 92-99%)
- **Services**: 70%+ (currently 71-86%)
- **Utils**: 90%+ (currently 92%)
- **Models**: 85%+ (currently 85%)
- **UI Components**: 0-25% (requires platform tests, not unit-testable)

---

## Common Tasks

### Adding a New Action

1. Create action class:
```kotlin
package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class MyNewAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<OpenCodeService>()
        // Implementation
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
```

2. Register in `plugin.xml`:
```xml
<actions>
    <action id="opencode.MyNewAction"
            class="com.opencode.actions.MyNewAction"
            text="My New Action"
            description="Description of what this does"
            icon="AllIcons.Actions.Execute">
        <keyboard-shortcut first-keystroke="control alt M" keymap="$default"/>
        <add-to-group group-id="ToolsMenu" anchor="last"/>
    </action>
</actions>
```

3. Write tests:
```kotlin
class MyNewActionTest {
    @Test
    fun `action performs expected operation`() {
        // Test implementation
    }
}
```

### Adding a New Service Method

1. Add method to service:
```kotlin
@Service(Service.Level.PROJECT)
class OpenCodeService(private val project: Project) {
    suspend fun myNewOperation(param: String): Result<Data> = withContext(Dispatchers.IO) {
        try {
            // Implementation
            Result.success(data)
        } catch (e: Exception) {
            LOG.warn("Operation failed", e)
            Result.failure(e)
        }
    }
}
```

2. Write comprehensive tests:
```kotlin
class OpenCodeServiceTest {
    @Test
    fun `myNewOperation succeeds with valid input`() = runTest {
        // Success case
    }
    
    @Test
    fun `myNewOperation handles errors gracefully`() = runTest {
        // Error case
    }
    
    @Test
    fun `myNewOperation validates input`() = runTest {
        // Validation
    }
}
```

### Adding a New ViewModel

1. Create ViewModel with callback interface:
```kotlin
class MyViewModel(
    private val service: OpenCodeService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    interface ViewCallback {
        fun onStateChanged(state: State)
        fun onError(message: String)
    }
    
    enum class State { IDLE, LOADING, SUCCESS, ERROR }
    
    private var callback: ViewCallback? = null
    private var state: State = State.IDLE
    
    fun initialize() {
        scope.launch {
            try {
                state = State.LOADING
                callback?.onStateChanged(state)
                
                // Do work
                
                state = State.SUCCESS
                callback?.onStateChanged(state)
            } catch (e: Exception) {
                state = State.ERROR
                callback?.onError(e.message ?: "Unknown error")
            }
        }
    }
    
    fun setCallback(callback: ViewCallback) {
        this.callback = callback
    }
}
```

2. Write ViewModel tests (no UI dependencies):
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    private lateinit var testScope: TestScope
    private lateinit var mockService: OpenCodeService
    private lateinit var mockCallback: MyViewModel.ViewCallback
    private lateinit var viewModel: MyViewModel
    
    @BeforeEach
    fun setUp() {
        testScope = TestScope(StandardTestDispatcher())
        mockService = mock()
        mockCallback = mock()
        viewModel = MyViewModel(mockService, testScope)
        viewModel.setCallback(mockCallback)
    }
    
    @Test
    fun `initialize transitions through states correctly`() = testScope.runTest {
        // Arrange
        whenever(mockService.loadData()).thenReturn(data)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onStateChanged(State.LOADING)
        inOrder.verify(mockCallback).onStateChanged(State.SUCCESS)
    }
}
```

---

## Troubleshooting

### Common Build Issues

**Issue**: `NoSuchMethodError: kotlinx.coroutines.CoroutineDispatcher.limitedParallelism$default`

**Solution**: Coroutines dependency must use `compileOnly` to let Platform provide version:
```kotlin
compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
```

**Issue**: Tests fail with `TestLoggerAssertionError`

**Solution**: Don't use `LOG.error()` in code that tests expect to succeed. Use `LOG.warn()` for recoverable errors:
```kotlin
// ❌ Bad - will fail tests
try {
    operation()
} catch (e: Exception) {
    LOG.error("Operation failed", e)
}

// ✅ Good - tests can handle this
try {
    operation()
} catch (e: Exception) {
    LOG.warn("Operation failed, will retry", e)
}
```

### Testing Issues

**Issue**: Platform tests fail with missing EDT

**Solution**: Use `runInEdtAndWait` or `runInEdtAndGet`:
```kotlin
protected fun <T> runInEdtAndGet(action: () -> T): T {
    return if (ApplicationManager.getApplication().isDispatchThread) {
        action()
    } else {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait {
            result = action()
        }
        result!!
    }
}
```

**Issue**: Coroutine tests hang or fail

**Solution**: Use `TestScope.runTest` and `advanceUntilIdle()`:
```kotlin
@Test
fun `test async operation`() = testScope.runTest {
    viewModel.startOperation()
    advanceUntilIdle()  // Wait for coroutines
    verify(mockCallback).onComplete()
}
```

### Runtime Issues

**Issue**: Server fails to start

**Solution**: Check if OpenCode CLI is installed:
```kotlin
fun isOpencodeInstalled(): Boolean {
    return try {
        val process = ProcessBuilder("opencode", "--version")
            .start()
        process.waitFor(2, TimeUnit.SECONDS)
        process.exitValue() == 0
    } catch (e: Exception) {
        false
    }
}
```

---

## Additional Resources

- **IntelliJ Platform SDK**: https://plugins.jetbrains.com/docs/intellij/
- **Kotlin Docs**: https://kotlinlang.org/docs/
- **Coroutines Guide**: https://kotlinlang.org/docs/coroutines-guide.html
- **Test Specification**: See `docs/TEST_SPEC.md` for comprehensive testing documentation
- **Coverage Reports**: `build/reports/kover/html/index.html`

---

**Last Updated**: 2025-12-26  
**Maintained By**: OpenCode Team  
**Plugin Version**: 1.0.0
