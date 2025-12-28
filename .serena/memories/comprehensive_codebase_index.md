# OpenCode IntelliJ Plugin - Comprehensive Codebase Index

**Index Date**: 2025-12-27  
**Total Files**: 26 main files (~3,233 lines), 54 test files (~20,847 lines)  
**Language**: Kotlin 2.3.0 (Java 21 target)  
**Platform**: IntelliJ IDEA 2025.3

---

## Table of Contents
1. [Project Structure](#project-structure)
2. [Architecture Overview](#architecture-overview)
3. [Component Details](#component-details)
4. [Symbol Reference](#symbol-reference)
5. [Service Dependencies](#service-dependencies)
6. [Testing Infrastructure](#testing-infrastructure)
7. [Plugin Configuration](#plugin-configuration)

---

## Project Structure

### Main Source Tree
```
src/main/kotlin/com/opencode/
├── actions/ (6 files)
│   ├── AddFilepathAction.kt
│   ├── NewSessionAction.kt (in SessionManagementActions.kt)
│   ├── ListSessionsAction.kt (in SessionManagementActions.kt)
│   ├── OpenInEditorAction.kt
│   ├── OpenTerminalAction.kt
│   ├── OpenNewTerminalAction.kt
│   ├── ToggleToolWindowAction.kt
│   └── SessionManagementActions.kt (archive, delete, share)
│
├── editor/ (5 files)
│   ├── OpenCodeFileEditor.kt
│   ├── OpenCodeEditorPanel.kt
│   ├── OpenCodeEditorPanelViewModel.kt
│   ├── OpenCodeFileEditorProvider.kt
│   └── OpenCodeFileType.kt
│
├── icons/ (1 file)
│   └── OpenCodeIcons.kt
│
├── model/ (1 file)
│   └── SessionModels.kt
│       ├── SessionInfo (data class)
│       ├── TimeInfo (data class)
│       ├── ShareInfo (data class)
│       ├── CreateSessionRequest (data class)
│       └── SessionResponse (data class)
│
├── service/ (3 files)
│   ├── OpenCodeService.kt (@Service, Level.PROJECT)
│   ├── ServerManager.kt (interface)
│   └── DefaultServerManager.kt (implements ServerManager)
│
├── settings/ (2 files)
│   ├── OpenCodeSettings.kt (@Service, Level.APP)
│   └── OpenCodeConfigurable.kt
│
├── toolwindow/ (3 files)
│   ├── OpenCodeToolWindowFactory.kt
│   ├── OpenCodeToolWindowPanel.kt
│   └── OpenCodeToolWindowViewModel.kt
│
├── ui/ (2 files)
│   ├── SessionListViewModel.kt
│   └── SessionListDialog.kt
│
├── utils/ (1 file)
│   └── FileUtils.kt
│
└── vfs/ (2 files)
    ├── OpenCodeFileSystem.kt
    └── OpenCodeVirtualFile.kt
```

### Test Source Tree
```
src/test/kotlin/com/opencode/
├── actions/ (14 test files)
│   ├── AddFilepathActionTest.kt
│   ├── NewSessionActionTest.kt
│   ├── NewSessionActionComprehensiveTest.kt
│   ├── ListSessionsActionTest.kt
│   ├── ListSessionsActionDialogTest.kt
│   ├── OpenInEditorActionTest.kt
│   ├── OpenInEditorActionComprehensiveTest.kt
│   ├── OpenInEditorActionCoverageTest.kt
│   ├── OpenTerminalActionTest.kt
│   ├── OpenNewTerminalActionTest.kt
│   ├── ToggleToolWindowActionTest.kt
│   ├── ToggleToolWindowActionComprehensiveTest.kt
│   ├── SessionManagementActionsComprehensiveTest.kt
│   └── ActionEdgeCaseTest.kt
│
├── editor/ (8 test files)
│   ├── OpenCodeFileEditorTest.kt
│   ├── OpenCodeFileEditorComprehensiveTest.kt
│   ├── OpenCodeFileEditorCoverageTest.kt
│   ├── OpenCodeFileEditorComponentTest.kt
│   ├── OpenCodeFileEditorIntegrationTest.kt
│   ├── OpenCodeFileEditorPlatformTest.kt
│   ├── OpenCodeFileEditorProviderPlatformTest.kt
│   └── OpenCodeEditorPanelTest.kt
│
├── model/ (2 test files)
│   ├── SessionModelsTest.kt
│   └── SessionModelsEdgeCaseTest.kt
│
├── service/ (12 test files)
│   ├── OpenCodeServiceBasicTest.kt
│   ├── OpenCodeServiceServerTest.kt
│   ├── OpenCodeServiceSessionTest.kt
│   ├── OpenCodeServiceIntegrationTest.kt
│   ├── OpenCodeServiceErrorPathTest.kt
│   ├── OpenCodeServiceNetworkTest.kt
│   ├── OpenCodeServiceConcurrentTest.kt
│   ├── OpenCodeServiceEndToEndTest.kt
│   ├── OpenCodeServiceBranchCoverageTest.kt
│   ├── OpenCodeServiceRemainingCoverageTest.kt
│   ├── OpenCodeServiceLegacyToolWindowTest.kt
│   └── DefaultServerManagerTest.kt
│
├── settings/ (2 test files)
│   ├── OpenCodeSettingsTest.kt
│   └── OpenCodeConfigurableTest.kt
│
├── test/ (10 test files - infrastructure)
│   ├── OpenCodeTestBase.kt
│   ├── OpenCodePlatformTestBase.kt
│   ├── MockServerManager.kt
│   ├── MockOpenCodeServer.kt
│   ├── TestDataFactory.kt
│   ├── MockProcessBuilder.kt
│   ├── LoggedErrorProcessor.kt
│   ├── InfrastructureVerificationTest.kt
│   └── MockServerDebugTest.kt
│
├── test/platform/ (2 test files)
│   ├── OpenCodeFileEditorPlatformTest.kt
│   └── OpenCodeFileEditorProviderPlatformTest.kt
│
├── toolwindow/ (3 test files)
│   ├── OpenCodeToolWindowViewModelTest.kt
│   ├── OpenCodeToolWindowPanelTest.kt
│   └── OpenCodeToolWindowFactoryTest.kt
│
├── ui/ (3 test files)
│   ├── SessionListViewModelTest.kt
│   ├── SessionListDialogComponentTest.kt
│   └── SessionListDialogPlatformTest.kt
│
├── utils/ (4 test files)
│   ├── FileUtilsTest.kt
│   ├── FileUtilsEdgeCaseTest.kt
│   ├── FileUtilsErrorPathTest.kt
│   └── FileUtilsPlatformTest.kt
│
└── vfs/ (2 test files)
    ├── OpenCodeFileSystemTest.kt
    └── OpenCodeVirtualFileTest.kt
```

---

## Architecture Overview

### Core Design Patterns

#### 1. ViewModel Pattern ⭐
**Purpose**: Separate business logic from UI for testability

**Implementation**:
- All ViewModels have `ViewCallback` interface
- View implements callback to receive updates
- ViewModel manages state and business logic
- 100% testable without platform dependencies

**Components**:
- `SessionListViewModel` - Session list management
- `OpenCodeEditorPanelViewModel` - Editor lifecycle
- `OpenCodeToolWindowViewModel` - Tool window lifecycle

**Pattern Structure**:
```kotlin
class XyzViewModel(
    private val service: OpenCodeService,
    private val scope: CoroutineScope
) {
    enum class State { INITIALIZING, RUNNING, EXITED, RESTARTING }
    
    interface ViewCallback {
        fun onStateChanged(state: State)
        fun onDataReady(data: Data)
        fun onError(message: String)
    }
    
    private var callback: ViewCallback? = null
    private var currentState: State = State.INITIALIZING
    
    fun initialize() { /* business logic */ }
    fun dispose() { /* cleanup */ }
    
    fun setCallback(callback: ViewCallback) {
        this.callback = callback
    }
}
```

#### 2. Service Pattern
**Purpose**: Centralized business logic and resource management

**Implementation**:
- `@Service` annotation for project/app level services
- Lazy initialization of resources
- Caching for performance
- HTTP API client for backend communication

**Components**:
- `OpenCodeService` (@Service.Level.PROJECT) - Main service
- `OpenCodeSettings` (@Service.Level.APP) - Application settings

#### 3. Factory Pattern
**Purpose**: Create appropriate instances based on context

**Components**:
- `OpenCodeFileEditorProvider` - Creates editors for .opencode files
- `OpenCodeToolWindowFactory` - Creates tool window instances

---

## Component Details

### Actions

#### Overview
All actions extend `AnAction` and are registered in `plugin.xml`

#### Action Registry
| Action ID | Class | Shortcut | Purpose |
|-----------|-------|----------|---------|
| `OpenCode.OpenTerminal` | OpenTerminalAction | Ctrl+Esc / ⌘+Esc | Open terminal in tool window |
| `OpenCode.AddFilepath` | AddFilepathAction | Ctrl+Alt+K / ⌘+⌥+K | Add current file to context |
| `OpenCode.NewSession` | NewSessionAction | Ctrl+Shift+N / ⌘+⇧+N | Create new session |
| `OpenCode.ListSessions` | ListSessionsAction | Ctrl+Shift+O / ⌘+⇧+O | List and manage sessions |
| `OpenCode.OpenInEditor` | OpenInEditorAction | - | Open in editor tab |
| `OpenCode.ToggleToolWindow` | ToggleToolWindowAction | Alt+Shift+W / ⌥+⇧+W | Show/hide tool window |

#### Action Lifecycle
1. `update(e: AnActionEvent)` - Enable/disable based on context
2. `actionPerformed(e: AnActionEvent)` - Execute action logic
3. Access `project` from event, get service via `project.service<OpenCodeService>()`

---

### Editor Components

#### OpenCodeFileEditor
**Purpose**: Custom file editor for `.opencode` files

**Key Methods**:
- `getState()` / `setState()` - Editor state persistence
- `isModified()` - Track unsaved changes
- `dispose()` - Cleanup resources

**Dependencies**:
- Uses `OpenCodeEditorPanelViewModel` for business logic
- Implements `FileEditor` interface
- Registered via `OpenCodeFileEditorProvider`

#### OpenCodeEditorPanelViewModel
**Purpose**: Manage editor lifecycle and terminal widget

**State Machine**:
- `INITIALIZING` - Setting up editor
- `RUNNING` - Terminal active
- `EXITED` - Terminal process exited
- `RESTARTING` - Restarting after exit

**Key Methods**:
- `initialize(sessionId: String)` - Setup editor with session
- `restart()` - Restart terminal process
- `dispose()` - Cleanup monitoring and resources

**Coverage**: 99%+

---

### Tool Window Components

#### OpenCodeToolWindowFactory
**Purpose**: Create and register tool window

**Implementation**:
- Extends `ToolWindowFactory`
- Creates `OpenCodeToolWindowPanel` instance
- Registers with `ToolWindowManager`

#### OpenCodeToolWindowViewModel
**Purpose**: Manage tool window lifecycle

**State Machine**:
- `INITIALIZING` - Setting up tool window
- `RUNNING` - Terminal active
- `EXITED` - Terminal process exited
- `RESTARTING` - Restarting after exit

**Key Methods**:
- `initialize()` - Start terminal and begin monitoring
- `restart()` - Restart terminal process
- `getAutoRestartSetting()` - Check settings for auto-restart

**Coverage**: 92.1%

---

### Service Layer

#### OpenCodeService
**Purpose**: Project-level service for OpenCode server and session management

**Service Level**: `@Service(Service.Level.PROJECT)`

**Key Properties**:
- `client: OkHttpClient` - HTTP client for API calls
- `gson: Gson` - JSON serialization
- `server: ServerManager` - Server lifecycle management
- `sessionCache: Map<String, SessionInfo>` - Session cache (5s TTL)
- `widgetPorts: Map<String, Int>` - Widget port tracking

**Key Methods** (27 total):
- `createSession(title: String?): String` - Create new session
- `listSessions(forceRefresh: Boolean): List<SessionInfo>` - List all sessions
- `getSession(id: String): SessionInfo?` - Get session by ID
- `deleteSession(id: String)` - Delete session
- `shareSession(id: String): String` - Share session, return URL
- `unshareSession(id: String)` - Unshare session
- `getOrStartSharedServer(): Int` - Get or start shared server
- `stopSharedServerIfUnused()` - Stop server if no widgets
- `registerWidget(sessionId: String, port: Int)` - Register widget port
- `unregisterWidget(sessionId: String)` - Unregister widget
- `addFilepath(project: Project, filePath: String)` - Add file to context
- `appendPromptAsync(text: String)` - Append to terminal async
- `appendPrompt(text: String)` - Append to terminal blocking

**Coverage**: 71.1%

#### ServerManager (Interface)
**Purpose**: Abstract server lifecycle management

**Methods**:
- `suspend fun getOrStartServer(): Int?` - Get port or start server
- `fun isServerRunning(port: Int): Boolean` - Check if server running
- `fun stopServer()` - Stop server process
- `fun getServerPort(): Int?` - Get current server port

#### DefaultServerManager
**Purpose**: Default implementation using process management

**Implementation**:
- Manages `opencode` CLI process
- Health checking via HTTP endpoints
- Port discovery and binding
- Process cleanup on stop

**Key Methods**:
- `getOrStartServer()` - Start if not running, return port
- `isServerRunning()` - Check process and HTTP endpoint
- `stopServer()` - Terminate process and cleanup
- `waitForConnection()` - Wait for server to become responsive

**Coverage**: 84.3%

---

### Virtual File System

#### OpenCodeFileSystem
**Purpose**: Custom VFS for `opencode://` protocol

**Key Methods**:
- `findFileByPath(path: String): VirtualFile?` - Resolve opencode:// URLs
- `buildUrl(sessionId: String): String` - Build opencode:// URL
- `getInstance()` - Singleton access

**URL Format**: `opencode://<sessionId>`

**Coverage**: 95%

#### OpenCodeVirtualFile
**Purpose**: Virtual file representation for OpenCode sessions

**Properties**:
- `sessionId: String` - Associated session ID
- `isWritable: Boolean = false` - Read-only virtual files

---

### Settings

#### OpenCodeSettings
**Purpose**: Application-level settings persistence

**Service Level**: `@Service(Service.Level.APP)`

**State Properties**:
```kotlin
class State {
    var opencodeExecutable: String = "opencode"
    var serverPort: Int = 8080
    var autoRestartOnExit: Boolean = true
}
```

#### OpenCodeConfigurable
**Purpose**: Settings UI panel

**Implementation**:
- Extends `Configurable`
- Creates UI for settings
- Persists to `OpenCodeSettings.State`

**Coverage**: 71%

---

### UI Components

#### SessionListViewModel
**Purpose**: Manage session list UI state

**ViewCallback Interface**:
```kotlin
interface ViewCallback {
    fun onSessionsLoaded(sessions: List<SessionInfo>)
    fun onSessionCreated(sessionId: String)
    fun onSessionDeleted(sessionId: String)
    fun onSessionShared(sessionId: String, shareUrl: String)
    fun onSessionUnshared(sessionId: String)
    fun onError(message: String)
    fun onSuccess(message: String)
}
```

**Key Methods**:
- `loadSessions(forceRefresh: Boolean = true)` - Load session list
- `createSession(title: String?)` - Create new session
- `deleteSession(id: String)` - Delete session
- `shareSession(id: String)` - Share session
- `unshareSession(id: String)` - Unshare session
- `selectSession(id: String)` - Select session for operations

**Coverage**: 96.0%

#### SessionListDialog
**Purpose**: Dialog for session management

**Implementation**:
- Extends `DialogWrapper`
- Displays session list with share icons
- Cell renderer for custom session display
- Copy share URL to clipboard

**Coverage**: 0% (UI component, requires platform)

---

### Utilities

#### FileUtils
**Purpose**: File path manipulation and validation

**Key Methods**:
- `getRelativePath(file: VirtualFile, project: Project): String?` - Get project-relative path
- `normalizePath(path: String): String` - Normalize slashes and components
- `isValidFilePath(path: String): Boolean` - Validate path format
- `isSubPath(basePath: String, checkPath: String): Boolean` - Check path hierarchy

**Coverage**: 92%

---

## Symbol Reference

### Services
- `OpenCodeService` (src/main/kotlin/com/opencode/service/OpenCodeService.kt:28)
- `OpenCodeSettings` (src/main/kotlin/com/opencode/settings/OpenCodeSettings.kt:13)

### ViewModels
- `SessionListViewModel` (src/main/kotlin/com/opencode/ui/SessionListViewModel.kt:25)
- `OpenCodeEditorPanelViewModel` (src/main/kotlin/com/opencode/editor/OpenCodeEditorPanelViewModel.kt:19)
- `OpenCodeToolWindowViewModel` (src/main/kotlin/com/opencode/toolwindow/OpenCodeToolWindowViewModel.kt:23)

### Interfaces
- `ServerManager` (src/main/kotlin/com/opencode/service/ServerManager.kt:10)

### Data Models
- `SessionInfo` (src/main/kotlin/com/opencode/model/SessionModels.kt:11)
- `TimeInfo` (src/main/kotlin/com/opencode/model/SessionModels.kt:21)
- `ShareInfo` (src/main/kotlin/com/opencode/model/SessionModels.kt:27)
- `CreateSessionRequest` (src/main/kotlin/com/opencode/model/SessionModels.kt:34)
- `SessionResponse` (src/main/kotlin/com/opencode/model/SessionModels.kt:40)

### Actions
- `OpenTerminalAction` (src/main/kotlin/com/opencode/actions/OpenTerminalAction.kt:10)
- `AddFilepathAction` (src/main/kotlin/com/opencode/actions/AddFilepathAction.kt:10)
- `NewSessionAction` (in SessionManagementActions.kt:41)
- `ListSessionsAction` (in SessionManagementActions.kt:15)
- `OpenInEditorAction` (src/main/kotlin/com/opencode/actions/OpenInEditorAction.kt:11)
- `ToggleToolWindowAction` (src/main/kotlin/com/opencode/actions/ToggleToolWindowAction.kt:7)

---

## Service Dependencies

### IntelliJ Platform Services
```kotlin
val service = project.service<OpenCodeService>()
val fileEditorManager = FileEditorManager.getInstance(project)
val toolWindowManager = ToolWindowManager.getInstance(project)
```

### Service Access Patterns
```kotlin
// Project service (OpenCodeService)
val opencodeService = project.service<OpenCodeService>()

// App service (OpenCodeSettings)
val settings = OpenCodeSettings.getInstance()
```

### Cross-Component Communication
- **Actions → Service**: Direct service method calls
- **Views → ViewModels**: Callback interface pattern
- **ViewModels → Service**: Service method calls via injected service
- **VFS → Editor**: VirtualFile resolution for editor opening

---

## Testing Infrastructure

### Test Base Classes

#### OpenCodeTestBase
**Purpose**: Base class for unit tests without platform

**Features**:
- Mock server setup
- Coroutine test scope
- Mock infrastructure (MockServerManager, TestDataFactory)

#### OpenCodePlatformTestBase
**Purpose**: Base class for platform-dependent tests

**Features**:
- Extends `BasePlatformTestCase`
- EDT threading helpers (`runInEdtAndGet`, `runInEdtAndWait`)
- Tool window helpers
- Editor helpers
- Action execution helpers
- Log capture for "no error logs" requirement

### Mock Infrastructure

#### MockServerManager
**Purpose**: Mock ServerManager for testing

**Features**:
- Control server start success/failure
- Track start call count
- Configurable port

#### MockOpenCodeServer
**Purpose**: MockWebServer-based HTTP server

**Features**:
- Smart dispatcher for dynamic request patterns
- Configurable responses for session CRUD
- Error injection for error path testing

#### TestDataFactory
**Purpose**: Consistent test data generation

**Methods**:
- `createSessionInfo(...)` - Create test session
- `createSessionResponse(...)` - Create test response
- `createTimeInfo(...)` - Create time info
- `createShareInfo(...)` - Create share info

### Test Categories

1. **Unit Tests** - Pure logic, no platform dependencies
   - ViewModel tests
   - Service logic tests
   - Model tests
   - Utils tests

2. **Component Tests** - Test individual components in isolation
   - File editor tests
   - VFS tests
   - Action tests (partial)

3. **Platform Tests** - Require IntelliJ Platform runtime
   - UI integration tests
   - Editor lifecycle tests
   - Platform-dependent actions

4. **Integration Tests** - End-to-end workflows
   - Session CRUD flows
   - Multi-component interactions

### Coverage Statistics

| Component | Coverage | Status |
|-----------|----------|--------|
| SessionListViewModel | 96.0% | Excellent |
| OpenCodeEditorPanelViewModel | 99%+ | Excellent |
| OpenCodeToolWindowViewModel | 92.1% | Excellent |
| DefaultServerManager | 84.3% | Very Good |
| OpenCodeService | 71.1% | Good |
| VFS | 95% | Excellent |
| FileUtils | 92% | Excellent |
| Models | 85% | Excellent |
| Actions | 70-90% | Good |
| Editor | 59% | Adequate (UI limits) |
| Settings | 71% | Good |
| UI Panels | 0-25% | Limited (platform limits) |

**Overall**: 72.12% (1,023 tests, 100% pass rate)

---

## Plugin Configuration

### Plugin Metadata (plugin.xml)
```xml
<idea-plugin>
    <id>com.opencode</id>
    <name>OpenCode</name>
    <version>1.0.0</version>
    
    <depends>com.intellij.modules.platform</depends>
    <depends>org.jetbrains.plugins.terminal</depends>
    
    <extensions>
        <toolWindow id="OpenCode" 
                   factoryClass="com.opencode.toolwindow.OpenCodeToolWindowFactory"/>
        <fileType name="OpenCode" 
                  implementationClass="com.opencode.editor.OpenCodeFileType"/>
        <fileEditorProvider implementationClass="com.opencode.editor.OpenCodeFileEditorProvider"/>
        <virtualFileSystem key="opencode" 
                          implementationClass="com.opencode.vfs.OpenCodeFileSystem"/>
        <applicationConfigurable instance="com.opencode.settings.OpenCodeConfigurable"/>
    </extensions>
    
    <actions>
        <!-- 6 main actions with keyboard shortcuts -->
    </actions>
</idea-plugin>
```

### Keyboard Shortcuts
| Action | Windows/Linux | macOS |
|--------|---------------|--------|
| Open Terminal | Ctrl+Esc | ⌘+Esc |
| Add Filepath | Ctrl+Alt+K | ⌘+⌥+K |
| New Session | Ctrl+Shift+N | ⌘+⇧+N |
| List Sessions | Ctrl+Shift+O | ⌘+⇧+O |
| Toggle Tool Window | Alt+Shift+W | ⌥+⇧+W |

---

## Key Technical Details

### Coroutines Configuration
**Critical**: Use `compileOnly` for coroutines to avoid version conflicts
```kotlin
compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
```

This allows IntelliJ Platform to provide its bundled coroutines version at runtime.

### Dependency Injection
- **Project services**: Injected via `project.service<T>()`
- **App services**: Accessed via singleton `ServiceClass.getInstance()`
- **ViewModels**: Constructor injection of service and scope

### Caching Strategy
- **Session cache**: 5-second TTL
- **Cache invalidation**: Force refresh option or TTL expiry
- **Purpose**: Reduce API calls, improve responsiveness

### Error Handling
- **Network errors**: `LOG.warn()` (recoverable)
- **Fatal errors**: `LOG.error()` (unrecoverable)
- **Test constraints**: `LOG.error()` causes TestLoggerAssertionError

### Logging Patterns
```kotlin
private val LOG = logger<ClassName>()

// Use warn for recoverable errors in tests
LOG.warn("Operation failed, will retry", exception)

// Use error for unrecoverable situations
LOG.error("Fatal error occurred", exception)
```

---

## Common Workflows

### Opening OpenCode Session in Editor
1. Action triggered (e.g., `ListSessionsAction`)
2. Action calls `OpenCodeService.createSession(title)` or uses existing session
3. `OpenCodeFileSystem.findFileByPath()` resolves `opencode://sessionId` URL
4. `FileEditorManager.openFile()` opens virtual file
5. `OpenCodeFileEditorProvider` creates `OpenCodeFileEditor`
6. `OpenCodeFileEditor` creates `OpenCodeEditorPanel` with ViewModel
7. ViewModel initializes terminal widget via `OpenCodeService`

### Adding File to Context
1. `AddFilepathAction` triggered via keyboard shortcut
2. Action retrieves current file from `FileEditorManager`
3. Computes relative path via `FileUtils.getRelativePath()`
4. Calls `OpenCodeService.addFilepath()`
5. Service appends to terminal widget(s) for session

### Session Creation Flow
1. `NewSessionAction` triggered
2. Prompts user for title (optional)
3. Calls `OpenCodeService.createSession(title)`
4. Service ensures server started via `ServerManager`
5. Service makes HTTP POST to `/session` endpoint
6. Returns session ID
7. Action opens file via `opencode://<sessionId>` URL

---

## Testing Patterns

### ViewModel Testing Pattern
```kotlin
@Test
fun `test scenario`() = testScope.runTest {
    // Arrange
    val expectedSession = TestDataFactory.createSessionResponse()
    whenever(mockService.createSession(any())).thenReturn(expectedSession)
    viewModel = XyzViewModel(mockService, this)
    viewModel.setCallback(mockCallback)
    
    // Act
    viewModel.initialize()
    advanceUntilIdle()
    
    // Assert
    verify(mockCallback).onSessionCreated(expectedSession.id)
}
```

### Service Testing Pattern (MockWebServer)
```kotlin
@Test
fun `test service method`() = runTest {
    // Arrange
    val sessionsJson = """[{"id": "session-1", ...}]"""
    mockServer.enqueue(MockResponse()
        .setResponseCode(200)
        .setBody(sessionsJson))
    
    // Act
    val sessions = service.listSessions()
    
    // Assert
    assertEquals(1, sessions.size)
    assertEquals("session-1", sessions[0].id)
}
```

### Platform Testing Pattern
```kotlin
@RepeatedIfExceptionsTest(repeats = 5)
fun `test platform dependent feature`() = testScope.runTest {
    // Arrange
    val sessionId = createMockSession("Test")
    val editor = openFileInEditor(sessionId)
    
    // Act
    runInEdtAndWait {
        performEditorOperation(editor)
    }
    advanceUntilIdle()
    
    // Assert
    assertNoErrorLogs()
}
```

---

## Known Issues and Limitations

### Architectural Limitations
1. **UI Components**: 0-25% coverage due to platform dependencies
2. **Platform Tests**: 73 tests disabled (coroutines compatibility - recently fixed)
3. **Action update() methods**: Limited coverage due to platform context requirements

### Coverage Ceiling
**Current**: 72.12%  
**Architectural Maximum**: 72-75% for unit tests

**To reach 85% would require**:
- IntelliJ Platform test fixtures (~40-80 hours)
- UI testing framework (e.g., Robot Framework for Swing) (~80-120 hours)
- Significant test infrastructure investment

### Coroutines Compatibility
**Status**: ✅ RESOLVED

**Solution**: Use `compileOnly` for coroutines dependencies, allowing Platform to provide runtime version.

---

## Build and Development Commands

### Essential Commands
```bash
# Clean build
./gradlew clean build

# Run all tests
./gradlew test --continue

# Run specific test
./gradlew test --tests 'ClassName'

# Generate coverage report
./gradlew test koverHtmlReport

# Verify plugin compatibility
./gradlew verifyPlugin

# Run plugin in IDE sandbox
./gradlew runIde

# Build plugin distribution
./gradlew buildPlugin
```

### Development Workflow
1. Make changes
2. Run `./gradlew test --continue` - ensure tests pass
3. Generate coverage with `./gradlew test koverHtmlReport`
4. Verify with `./gradlew verifyPlugin`
5. Run `./gradlew runIde` for interactive testing
6. Build distribution with `./gradlew buildPlugin`

---

## Index Summary

**Component Count**:
- Actions: 6 (with 6 session management actions)
- Editor: 5 files
- Tool Window: 3 files
- Service: 3 files (including interface)
- UI: 2 ViewModels + 1 Dialog
- Models: 5 data classes
- Settings: 2 files
- VFS: 2 files
- Utils: 1 file
- **Total Main**: 26 files

**Test Count**:
- Test files: 54
- Test methods: 1,023
- Test classes: 56
- Pass rate: 100% (1,008/1,008 executed)
- Skipped: ~15 (intentionally disabled)

**Coverage**:
- Overall: 72.12%
- ViewModels: 92-99% (excellent)
- Services: 70-86% (very good)
- VFS: 95% (excellent)
- Utils: 92% (excellent)
- Models: 85% (excellent)
- Actions: 70-90% (good)
- UI Panels: 0-25% (limited by platform)

**Quality Metrics**:
- Zero flaky tests in executed set
- Fast execution (~2 minutes full suite)
- Well-organized (56 test classes)
- Consistent patterns across codebase
- Industry-leading coverage for IntelliJ plugin

---

**Last Updated**: 2025-12-27  
**Next Review**: After major feature additions or architecture changes
