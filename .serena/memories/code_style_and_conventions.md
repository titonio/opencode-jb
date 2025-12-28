# OpenCode IntelliJ Plugin - Code Style and Conventions

## Naming Conventions

### Variables/Functions
- **Style**: `camelCase`
```kotlin
val sessionId = "session-123"
fun loadSessions() { }
```

### Classes/Interfaces
- **Style**: `PascalCase`
```kotlin
class SessionListViewModel
interface ViewCallback
data class SessionInfo
```

### Constants
- **Style**: `SCREAMING_SNAKE_CASE`
```kotlin
private const val CACHE_TTL = 5000L
private const val MAX_SESSIONS_TO_KEEP = 10
```

### Private Properties
- **DO NOT** use underscore prefix
```kotlin
// ✅ Good
private val sessionCache = mutableMapOf<String, SessionInfo>()

// ❌ Bad
private val _sessionCache = mutableMapOf<String, SessionInfo>()
```

## File Organization

### Import Order
1. IntelliJ Platform imports
2. Project imports
3. Third-party imports
4. Testing imports

```kotlin
import com.intellij.openapi.project.Project  // IntelliJ Platform
import com.opencode.model.SessionInfo         // Project imports
import kotlinx.coroutines.CoroutineScope      // Third-party
import org.junit.jupiter.api.Test             // Testing
```

### Import Rules
- **ALWAYS** use explicit imports (never star imports)
- **NO** unused imports

## Types & Null Safety

### Public APIs
- **USE** explicit types for public APIs
```kotlin
fun createSession(title: String?): String { }
```

### Private/Local Variables
- **USE** type inference for private/local variables
```kotlin
private val sessions = mutableListOf<SessionInfo>()
val result = service.loadSessions()
```

### Nullable Types
- **PREFER** nullable types over lateinit
```kotlin
// ✅ Good
private var callback: ViewCallback? = null

// ⚠️ Use lateinit only when initialization guaranteed before use
private lateinit var testScope: TestScope
```

### Null Safety Operators
- **USE** safe calls and elvis operator
```kotlin
val title = session.title ?: "Untitled"
callback?.onSessionsLoaded(sessions)
```

- **AVOID** !! (assert non-null) unless absolutely necessary
```kotlin
// ❌ Bad - can throw NPE
val file = findFile()!!

// ✅ Good - explicit handling
val file = findFile() ?: return
```

## Error Handling

### Exception Handling
- **CATCH** specific exceptions, not generic Exception
```kotlin
try {
    val response = client.newCall(request).execute()
} catch (e: IOException) {
    LOG.warn("Network request failed", e)
    return emptyList()
}
```

### Result Type
- **USE** Result for operations that can fail
```kotlin
suspend fun loadSessions(): Result<List<SessionInfo>> {
    return try {
        Result.success(fetchSessions())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### "Not Found" Scenarios
- **USE** nullable return types
```kotlin
fun getSession(id: String): SessionInfo? {
    return sessionCache[id]
}
```

### Logging in Tests
- **USE** LOG.warn() for recoverable errors in tests (LOG.error() will fail tests)
```kotlin
// ❌ Bad - will cause TestLoggerAssertionError
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

## Coroutines & Threading

### IO Operations
- **USE** withContext(Dispatchers.IO) for IO operations
```kotlin
suspend fun loadSessions(): List<SessionInfo> = withContext(Dispatchers.IO) {
    // HTTP call
}
```

### CoroutineScope Injection
- **PASS** CoroutineScope via constructor for testability
```kotlin
class SessionListViewModel(
    private val service: OpenCodeService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
)
```

### Launch vs Suspend
- **USE** launch for fire-and-forget operations
- **USE** suspend functions for async operations
```kotlin
fun createSession(title: String) {
    scope.launch {
        try {
            service.createSession(title)
        } catch (e: Exception) {
            callback?.onError(e.message ?: "Unknown error")
        }
    }
}

suspend fun createSession(title: String): String
```

### Threading in Tests
- **USE** runInEdtAndWait for UI updates in platform tests
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

## Data Classes

### Data Class Usage
- **USE** data classes for models
```kotlin
data class SessionInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("time")
    val time: TimeInfo
)
```

### Default Parameters
- **USE** default parameters for optional fields
```kotlin
data class CreateSessionRequest(
    @SerializedName("title")
    val title: String? = null
)
```

### Computed Properties
- **ALLOW** computed properties in data classes
```kotlin
data class SessionInfo(
    val id: String,
    val time: TimeInfo
) {
    val isActive: Boolean get() = time.archived == null
}
```

### Immutability
- **PREFER** immutable data classes
```kotlin
// ✅ Good - immutable
data class Session(
    val id: String,
    val isActive: Boolean
)

// ❌ Bad - mutable var in data class
data class Session(
    val id: String,
    var isActive: Boolean
)
```

## Comments & Documentation

### KDoc for Public APIs
- **USE** KDoc for public APIs
```kotlin
/**
 * Loads sessions from the OpenCode service.
 * 
 * @param forceRefresh Whether to bypass cache and fetch from server
 * @return List of sessions, sorted by last updated time
 * @throws IOException if network request fails
 */
suspend fun loadSessions(forceRefresh: Boolean = true): List<SessionInfo>
```

### Code Comments
- **USE** comments for complex logic
- **AVOID** obvious comments
```kotlin
// ❌ Bad
val sessions = loadSessions()  // Loads sessions

// ✅ Good - no comment needed
val sessions = loadSessions()

// ✅ Good - explain WHY, not WHAT
// Wait 1 second to handle tab drag/split scenarios where editor
// is briefly disposed before new one is created
Thread.sleep(1000)
```

## Testing Patterns

### AAA Pattern (Arrange-Act-Assert)
```kotlin
@Test
fun `createSession creates new session and notifies callback`() = runTest {
    // Arrange
    val expectedId = "new-session-123"
    whenever(mockService.createSession(any())).thenReturn(expectedId)
    
    // Act
    viewModel.createSession("New Session")
    advanceUntilIdle()
    
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
        advanceUntilIdle()
        
        // Assert
        verify(mockCallback).onSessionsLoaded(sessions)
    }
}
```

### Mockito Kotlin
```kotlin
// Mock creation
private val mockService = mock<OpenCodeService>()
private val mockCallback = mock<SessionListViewModel.ViewCallback>()

// Stubbing
whenever(mockService.listSessions(any())).thenReturn(sessions)

// Verification
verify(mockService).createSession("Title")
verify(mockCallback).onError(argThat { contains("Failed") })
verify(mockService, never()).deleteSession(any())

// Order verification
val inOrder = inOrder(mockCallback)
inOrder.verify(mockCallback).onStateChanged(State.INITIALIZING)
inOrder.verify(mockCallback).onStateChanged(State.RUNNING)
```
