# OpenCode IntelliJ Plugin - Testing Plan
## Goal: Achieve 90%+ Code Coverage

**Timeline:** 6 weeks, phased implementation  
**Total Tests:** ~226 tests  
**Total Lines:** ~1,791 lines of production code  
**Testing Framework:** JUnit 5, IntelliJ Platform Test Framework  
**Mocking:** Mockito, MockWebServer  
**Coverage Tool:** Kover (Kotlin Code Coverage)

---

## Testing Philosophy

Following IntelliJ Platform best practices:
- **Model-level functional tests** - Test features as a whole, not isolated functions
- **Light tests preferred** - Reuse project instances for speed
- **Real components over mocks** - Use actual IntelliJ components where possible
- **Minimal UI mocking** - Work with underlying models instead of Swing UI
- **Stable tests** - Should require minimal maintenance as code evolves

---

## Phase 1: Foundation Setup (Week 1)

### Goals
- Establish testing infrastructure
- Add coverage tooling (Kover)
- Create test utilities and base classes
- Create minimal test data files
- Verify basic test execution

### Directory Structure
```
src/test/
├── kotlin/com/opencode/
│   ├── test/                          # Test utilities
│   │   ├── OpenCodeTestBase.kt        # Base test class
│   │   ├── MockOpenCodeServer.kt      # MockWebServer wrapper
│   │   ├── TestDataFactory.kt         # Test data generators
│   │   └── MockProcessBuilder.kt      # CLI mocking utilities
│   ├── model/
│   ├── service/
│   ├── vfs/
│   ├── editor/
│   ├── actions/
│   ├── ui/
│   ├── utils/
│   ├── toolwindow/
│   └── integration/
└── resources/
    └── testdata/
        ├── api/                       # Only complex API responses
        │   ├── large-session-list.json
        │   └── malformed-response.json
        └── files/                     # Sample files for testing
            └── sample.kt
```

### Build Configuration Changes

**Add to `build.gradle.kts`:**
```kotlin
plugins {
    // ... existing plugins
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
}

dependencies {
    // ... existing dependencies
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // IntelliJ test frameworks
    intellijPlatform {
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        total {
            html { 
                onCheck = true 
            }
            xml { 
                onCheck = true 
            }
        }
        filters {
            excludes {
                classes("*.icons.*")  // Exclude trivial icon classes
            }
        }
    }
    verify {
        rule {
            minBound(90)  // Enforce 90% minimum coverage
        }
    }
}
```

### Test Utilities (4 files)

#### 1. OpenCodeTestBase.kt
- Extends `BasePlatformTestCase`
- Common setup/teardown
- Helper methods for assertions
- Mock service creation

#### 2. MockOpenCodeServer.kt
- Wraps `MockWebServer`
- Pre-configured endpoints
- Methods: `enqueueSessionList()`, `enqueueSessionCreate()`, `enqueueError()`
- JSON serialization/deserialization

#### 3. TestDataFactory.kt
- Factory methods for test data
- `createSessionInfo()`
- `createSessionList(count)`
- `createMockProcess()`

#### 4. MockProcessBuilder.kt
- Mock CLI process execution
- Simulate: success, failure, timeout

### Test Data Files (minimal, store only complex data)
- `large-session-list.json` - 50+ sessions for cleanup testing
- `malformed-response.json` - Invalid JSON for error testing
- `sample.kt` - Sample file for FileUtils testing

### Success Criteria
- ✅ `./gradlew test` runs successfully
- ✅ `./gradlew koverHtmlReport` generates report
- ✅ Test utilities compile and importable
- ✅ MockWebServer instantiates correctly

---

## Phase 2: Core Service Layer (Week 2)

### Goals
- Test `OpenCodeService` thoroughly
- Test `SessionModels` serialization
- Achieve 85-90% coverage of service (531 lines)
- Achieve 95%+ coverage of models (67 lines)

### Test Files (5 files, 62 tests)

#### 1. SessionModelsTest.kt (15 tests)
- `testSessionInfo_Serialization()`
- `testSessionInfo_Deserialization()`
- `testSessionInfo_IsShared_WhenShareInfoPresent()`
- `testSessionInfo_IsShared_WhenShareInfoNull()`
- `testSessionInfo_ShareUrl_ExtractsCorrectly()`
- `testTimeInfo_Serialization()`
- `testShareInfo_Serialization()`
- `testCreateSessionRequest_Serialization()`
- `testSessionResponse_Deserialization()`
- Plus edge cases (nulls, empty strings, etc.)

#### 2. OpenCodeServiceBasicTest.kt (10 tests)
- `testIsOpencodeInstalled_Success()`
- `testIsOpencodeInstalled_Failure()`
- `testIsOpencodeInstalled_Timeout()`
- `testHasActiveEditor_WhenRegistered()`
- `testHasActiveEditor_WhenNone()`
- `testRegisterActiveEditor()`
- `testUnregisterActiveEditor()`
- `testGetActiveEditorFile()`
- `testIsServerRunning_WhenRunning()`
- `testIsServerRunning_WhenDown()`

**Mocking:** Mockito for ProcessBuilder, MockWebServer for HTTP

#### 3. OpenCodeServiceSessionTest.kt (20 tests)
- `testCreateSession_WithTitle()`
- `testCreateSession_WithoutTitle()`
- `testCreateSession_ServerDown_ThrowsException()`
- `testCreateSession_InvalidResponse()`
- `testCreateSession_UpdatesCache()`
- `testCreateSession_TriggersCleanup()`
- `testListSessions_FirstCall()`
- `testListSessions_CachedCall_WithinTTL()`
- `testListSessions_ExpiredCache()`
- `testListSessions_ForceRefresh()`
- `testListSessions_ServerDown()`
- `testGetSession_ValidId()`
- `testGetSession_InvalidId()`
- `testDeleteSession_Success()`
- `testDeleteSession_UpdatesCache()`
- `testDeleteSession_Failure()`
- `testShareSession_Success()`
- `testUnshareSession_Success()`
- `testCleanupOldSessions_KeepsMaxSessions()`
- `testCleanupOldSessions_SortsCorrectly()`

#### 4. OpenCodeServiceServerTest.kt (12 tests)
- `testGetOrStartSharedServer_FirstStart()`
- `testGetOrStartSharedServer_ReuseExisting()`
- `testGetOrStartSharedServer_RetryOnFailure()`
- `testGetOrStartSharedServer_AllRetriesFail()`
- `testGetOrStartSharedServer_ProcessStartsButNoResponse()`
- `testStartServerInternal_RandomPort()`
- `testStartServerInternal_CorrectCommand()`
- `testStartServerInternal_WorkingDirectory()`
- `testStopSharedServerIfUnused_WithActiveEditor()`
- `testStopSharedServerIfUnused_NoActiveEditor()`
- `testScheduleServerShutdownCheck_Delay()`
- `testWaitForConnection_Success()`

#### 5. OpenCodeServiceIntegrationTest.kt (5 tests)
- `testFullSessionLifecycle()` - Create → List → Share → Delete
- `testMultipleSessionsWithCleanup()` - Create 15, verify 10 kept
- `testConcurrentSessionOperations()` - Thread safety
- `testServerRecoveryAfterCrash()` - Restart if died
- `testEditorRegistrationIntegration()` - Register/unregister flow

### Success Criteria
- ✅ 62 tests passing
- ✅ 85-90% coverage of OpenCodeService
- ✅ 95%+ coverage of SessionModels
- ✅ All async operations tested
- ✅ Error paths covered

---

## Phase 3: Virtual File System & Utilities (Week 3)

### Goals
- Test VFS components (158 lines)
- Test FileUtils (48 lines)
- Achieve 90%+ coverage

### Test Files (3 files, 37 tests)

#### 1. OpenCodeVirtualFileTest.kt (15 tests)
- `testGetPath_ReturnsCorrectUrl()`
- `testGetName_ShortensSessionId()`
- `testGetPresentableName_MatchesName()`
- `testGetFileSystem_ReturnsCorrectInstance()`
- `testIsValid_AlwaysTrue()`
- `testIsDirectory_AlwaysFalse()`
- `testIsWritable_AlwaysFalse()`
- `testGetParent_ReturnsNull()`
- `testGetChildren_ReturnsNull()`
- `testGetOutputStream_ThrowsException()`
- `testContentsToByteArray_ReturnsEmpty()`
- `testRefresh_ExecutesPostRunnable()`
- `testEquals_SameSession_ReturnsTrue()`
- `testEquals_DifferentSession_ReturnsFalse()`
- `testHashCode_ConsistentWithEquals()`

#### 2. OpenCodeFileSystemTest.kt (12 tests)
- `testGetInstance_ReturnsSingleton()`
- `testGetProtocol_ReturnsOpencode()`
- `testBuildUrl_FormatsCorrectly()`
- `testFindFileByPath_ValidPath()`
- `testFindFileByPath_InvalidProtocol()`
- `testFindFileByPath_MissingSessionId()`
- `testFindFileByPath_CachesFiles()`
- `testFindFileByPath_DifferentSessions()`
- `testRefresh_NoOp()`
- `testRefreshAndFindFileByPath_UpdatesFile()`
- `testFileSystemIntegration_WithEditor()`
- `testFileSystemPersistence_AcrossRefresh()`

#### 3. FileUtilsTest.kt (10 tests)
- `testGetActiveFileReference_NullFile_ReturnsNull()`
- `testGetActiveFileReference_NoEditor_OnlyPath()`
- `testGetActiveFileReference_WithEditor_NoSelection()`
- `testGetActiveFileReference_SingleLineSelection()`
- `testGetActiveFileReference_MultiLineSelection()`
- `testGetActiveFileReference_RelativePath_FromContentRoot()`
- `testGetActiveFileReference_NoContentRoot_UsesFileName()`
- `testGetActiveFileReference_FileEqualsRoot_UsesFileName()`
- `testGetActiveFileReference_ComplexPath()`
- `testGetActiveFileReference_SpecialCharacters()`

### Success Criteria
- ✅ 37 tests passing
- ✅ 90%+ coverage of VFS
- ✅ 90%+ coverage of FileUtils

---

## Phase 4: Actions & UI (Week 4)

### Goals
- Test all 6 action classes (198 lines)
- Test SessionListDialog (245 lines)
- Achieve 85-90% actions, 75-80% UI coverage

### Test Files (8 files, 54 tests)

#### Actions (39 tests total)
1. **ListSessionsActionTest.kt** (8 tests)
2. **NewSessionActionTest.kt** (8 tests)
3. **OpenInEditorActionTest.kt** (6 tests)
4. **AddFilepathActionTest.kt** (5 tests)
5. **ToggleToolWindowActionTest.kt** (4 tests)
6. **OpenTerminalActionTest.kt** (4 tests)
7. **OpenNewTerminalActionTest.kt** (4 tests)

**Common test patterns:**
- `testUpdate_WithProject_EnablesAction()`
- `testUpdate_WithoutProject_DisablesAction()`
- `testActionPerformed_ExecutesCorrectly()`
- `testActionPerformed_HandlesErrors()`

#### UI (15 tests)
8. **SessionListDialogTest.kt** (15 tests)
- `testDialog_Initialization_LoadsSessions()`
- `testDialog_EmptyState_ShowsMessage()`
- `testDialog_SessionList_DisplaysCorrectly()`
- `testDialog_SessionSelection_SingleSelect()`
- `testDialog_DoubleClick_ConfirmsSelection()`
- `testDialog_SearchField_FiltersResults()`
- `testDialog_SearchField_CaseInsensitive()`
- `testDialog_ShareButton_EnabledWhenUnshared()`
- `testDialog_ShareButton_CallsService()`
- `testDialog_UnshareButton_EnabledWhenShared()`
- `testDialog_UnshareButton_CallsService()`
- `testDialog_DeleteButton_ConfirmsBeforeDelete()`
- `testDialog_DeleteButton_RemovesFromList()`
- `testDialog_RefreshButton_ReloadsData()`
- `testDialog_ErrorHandling_DisplaysMessage()`

### Success Criteria
- ✅ 54 tests passing
- ✅ 85-90% coverage of Actions
- ✅ 75-80% coverage of UI

---

## Phase 5: Editor Components & Integration (Week 5)

### Goals
- Test editor components (431 lines)
- Test tool window factory (13 lines)
- Create integration tests
- Achieve 80-85% coverage

### Test Files (6 files, 58 tests)

#### Editor Tests (44 tests)
1. **OpenCodeFileTypeTest.kt** (5 tests)
2. **OpenCodeFileEditorProviderTest.kt** (8 tests)
3. **OpenCodeFileEditorTest.kt** (25 tests)
   - Initialization (5 tests)
   - Lifecycle (7 tests)
   - State Management (6 tests)
   - Server Management (5 tests)
   - Properties (2 tests)
4. **OpenCodeToolWindowFactoryTest.kt** (6 tests)

#### Integration Tests (14 tests)
5. **FullWorkflowIntegrationTest.kt** (8 tests)
   - `testWorkflow_CreateSessionAndOpen()`
   - `testWorkflow_ListAndSelectSession()`
   - `testWorkflow_ShareAndOpenSharedSession()`
   - `testWorkflow_TabDragAndRestore()`
   - `testWorkflow_MultipleTabsServerSharing()`
   - `testWorkflow_CloseAllTabsStopsServer()`
   - `testWorkflow_EditorWithFileReference()`
   - `testWorkflow_SessionCleanupOnCreate()`

6. **TerminalWidgetIntegrationTest.kt** (6 tests)
   - `testTerminalWidget_Creation()`
   - `testTerminalWidget_AttachCommand()`
   - `testTerminalWidget_EnvironmentVariables()`
   - `testTerminalWidget_WorkingDirectory()`
   - `testTerminalWidget_Disposal()`
   - `testTerminalWidget_MultipleInstances()`

### Success Criteria
- ✅ 58 tests passing
- ✅ 80-85% coverage of Editor
- ✅ 85%+ coverage of ToolWindowFactory
- ✅ Integration tests cover full workflows

---

## Phase 6: Coverage Analysis & Refinement (Week 6)

### Goals
- Analyze coverage gaps
- Add tests for uncovered paths
- Reach 90%+ overall coverage
- Document testing strategy

### Tasks

#### 1. Coverage Analysis
```bash
./gradlew koverHtmlReport
# Open: build/reports/kover/html/index.html
```
- Identify gaps by file and component
- Categorize uncovered lines:
  - Testable (add tests)
  - UI-bound (hard to test, document why)
  - Error paths (add negative tests)
  - Dead code (consider removing)

#### 2. Gap Filling (~15-20 additional tests)
- Error handling paths
- Edge cases (null, empty, concurrent)
- Timeout scenarios
- Network failures
- Race conditions
- Boundary conditions

#### 3. Test Quality Review
- Remove flaky tests
- Add missing assertions
- Improve test names
- Remove duplicate logic
- Ensure tearDown in finally blocks

#### 4. Documentation
Create **`docs/TESTING.md`** covering:
- Testing philosophy
- How to run tests
- How to run single tests
- How to check coverage
- How to add new tests
- Mocking patterns and utilities
- Test data generation strategy
- Coverage requirements
- Troubleshooting common failures

#### 5. Test Stability
- Run full suite 10 times
- Fix flaky tests
- Ensure tests run in any order
- Verify cleanup in tearDown

#### 6. Final Verification
```bash
./gradlew test          # All tests pass
./gradlew koverVerify   # Coverage ≥ 90%
./gradlew build         # Full build succeeds
```

### Success Criteria
- ✅ 90%+ overall coverage achieved
- ✅ ~226+ tests passing consistently
- ✅ No flaky tests
- ✅ TESTING.md complete
- ✅ Build with verification passes

---

## Final Test Count Summary

| Phase | Component | Tests | Lines | Coverage % |
|-------|-----------|-------|-------|------------|
| 2 | SessionModels | 15 | 67 | 95% |
| 2 | OpenCodeService | 47 | 531 | 85-90% |
| 3 | VFS (File + System) | 27 | 158 | 90% |
| 3 | FileUtils | 10 | 48 | 90% |
| 4 | Actions (all 6) | 39 | 198 | 85-90% |
| 4 | SessionListDialog | 15 | 245 | 75-80% |
| 5 | FileType | 5 | 19 | 95% |
| 5 | FileEditorProvider | 8 | 38 | 90% |
| 5 | FileEditor | 25 | 374 | 80-85% |
| 5 | ToolWindowFactory | 6 | 13 | 85% |
| 5 | Integration Tests | 14 | N/A | - |
| 6 | Gap Filling | ~15 | ~100 | - |
| **TOTAL** | **All Components** | **~226** | **~1,791** | **90%+** |

---

## Key Testing Patterns

### 1. Light Tests (Preferred)
```kotlin
class MyTest : BasePlatformTestCase() {
    // Tests reuse project instances - fast!
}
```

### 2. Mockito for External Dependencies
```kotlin
val mockProcess = mock<Process> {
    on { waitFor(any(), any()) } doReturn true
    on { exitValue() } doReturn 0
}
```

### 3. MockWebServer for API
```kotlin
val server = MockWebServer()
server.enqueue(MockResponse().setBody("""{"id":"123"}"""))
```

### 4. Coroutine Testing
```kotlin
@Test
fun testSuspendFunction() = runTest {
    val result = service.createSession("title")
    assertEquals("session-id", result)
}
```

### 5. Test Data
```kotlin
val session = TestDataFactory.createSessionInfo(
    id = "test-123",
    title = "Test Session"
)
```

---

## Useful Gradle Commands

```bash
# Run all tests
./gradlew test

# Run single test class
./gradlew test --tests 'com.opencode.service.OpenCodeServiceBasicTest'

# Run single test method
./gradlew test --tests 'com.opencode.service.OpenCodeServiceBasicTest.testIsOpencodeInstalled_Success'

# Generate HTML coverage report
./gradlew koverHtmlReport

# Generate XML coverage report (for CI)
./gradlew koverXmlReport

# Verify coverage meets threshold
./gradlew koverVerify

# Full build with tests and coverage
./gradlew build koverVerify
```

---

## Mocking Strategy Details

### CLI Process Mocking
```kotlin
// Mock ProcessBuilder to return our mock process
val mockProcessBuilder = mock<ProcessBuilder>()
whenever(mockProcessBuilder.start()).thenReturn(mockProcess)
```

### HTTP API Mocking
```kotlin
val server = MockOpenCodeServer()
server.enqueueSessionList(listOf(session1, session2))
// Make request...
server.takeRequest() // Verify request was made
```

### Coroutine Dispatcher Mocking
```kotlin
val testDispatcher = StandardTestDispatcher()
// Inject into service or use runTest { }
```

### IntelliJ Service Mocking
```kotlin
// Replace service in test
val mockService = mock<OpenCodeService>()
ServiceContainerUtil.replaceService(
    project, 
    OpenCodeService::class.java, 
    mockService, 
    testRootDisposable
)
```

---

## Test Data Strategy

### Generate Programmatically
- Simple objects (SessionInfo, requests)
- Small lists (1-5 items)
- Predictable test scenarios

### Store in Files
- Large datasets (50+ sessions)
- Complex/nested structures
- Malformed/edge case data
- Binary files for testing

### Factory Pattern
```kotlin
object TestDataFactory {
    fun createSessionInfo(
        id: String = "test-${UUID.randomUUID()}",
        title: String = "Test Session",
        directory: String = "/test/path"
    ): SessionInfo { ... }
    
    fun createSessionList(count: Int): List<SessionInfo> { ... }
}
```

---

## Coverage Considerations

### Easy to Test (95%+ target)
- Models (data classes)
- Utils (pure functions)
- VFS (well-defined interfaces)

### Moderate (85-90% target)
- Service layer (API calls)
- Actions (user interactions)
- Providers (factory classes)

### Harder (80-85% target)
- Editor (complex lifecycle)
- ToolWindow (platform integration)

### Hardest (75-80% target)
- UI dialogs (Swing components)
- Visual components

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Terminal widget hard to test | Use integration tests, document untestable parts |
| Flaky tests in async code | Use `runTest`, `TestDispatcher`, avoid real delays |
| Mockito with Kotlin final classes | Use `mockito-kotlin`, consider `@TestOnly` interfaces |
| UI tests require UI thread | Use `EdtTestUtil`, mark as integration tests |
| Coverage may not reach 90% | Analyze untestable code, add gap tests, document exceptions |

---

## Success Metrics

- ✅ 90%+ overall code coverage
- ✅ 226+ tests passing
- ✅ All phases completed on schedule
- ✅ No flaky tests
- ✅ Documentation complete
- ✅ Build passes with `koverVerify`
- ✅ Tests run in <5 minutes
- ✅ Easy to add new tests

---

## References

- [IntelliJ Platform Testing Guide](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)
- [Kover Documentation](https://github.com/Kotlin/kotlinx-kover)
- [Mockito Kotlin](https://github.com/mockito/mockito-kotlin)
- [MockWebServer](https://github.com/square/okhttp/tree/master/mockwebserver)
- [Coroutines Testing](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
