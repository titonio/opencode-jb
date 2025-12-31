# Detekt Static Code Analysis Fix Plan

**Generated:** 2025-12-29
**Total Issues:** 5,675 (across both main and test sources)
**Main Source Issues:** ~250+ issues

---

## Important Rules

1. **The `config/detekt/detekt.yml` must not be modified** - All fixes must be made in the source code only
2. **Run the tests frequently (after each addressed file)** to make sure everything works
3. **Break the tasks by file being addressed**
4. **Run subagents for each file being addressed**
5. **For each type of error you can use the web and search for solutions, or use the Detekt documentation to understand the rule and how to fix it**

---

## Issues Summary by Category

### Comments (82 issues)
- **UndocumentedPublicClass:** 24 classes missing documentation
- **UndocumentedPublicFunction:** 30 functions missing documentation
- **UndocumentedPublicProperty:** 28 properties missing documentation

### Complexity (17 issues)
- **CognitiveComplexMethod:** 3 methods exceed threshold of 15
- **LargeClass:** 1 class exceeds threshold of 600
- **LongMethod:** 1 method exceeds threshold of 40 (main)
- **LongParameterList:** 2 functions exceed threshold of 6
- **TooManyFunctions:** 3 classes exceed threshold of 15

### Empty-blocks (15 issues)
- **EmptyFunctionBlock:** 2 empty function bodies in main files

### Exceptions (91 issues)
- **SwallowedException:** 9 caught exceptions in main files
- **TooGenericExceptionCaught:** 18 generic exception catches in main files

### Style (5,233 issues)
- **ArgumentListWrapping:** ~100+ formatting issues
- **MaxLineLength:** Various line length violations

---

## Fix Plan by File

### Phase 1: Documentation Issues (82 total)

#### 1.1 Add Documentation to Model Classes
**File:** `src/main/kotlin/com/opencode/model/SessionModels.kt`
- [x] Add KDoc for `SessionInfo` data class
- [x] Add KDoc for `TimeInfo` data class
- [x] Add KDoc for `ShareInfo` data class
- [x] Add KDoc for `CreateSessionRequest` data class
- [x] Add KDoc for `SessionResponse` data class
- [x] Add KDoc for all properties

#### 1.2 Add Documentation to Service Classes
**File:** `src/main/kotlin/com/opencode/service/OpenCodeService.kt`
- [ ] Add KDoc for `OpenCodeService` class
- [ ] Add KDoc for all public methods (18 undocumented functions)
- [ ] Add KDoc for all public properties

**File:** `src/main/kotlin/com/opencode/service/DefaultServerManager.kt`
- [ ] Add KDoc for class (if missing)
- [ ] Add KDoc for methods

#### 1.3 Add Documentation to ViewModel Classes
**File:** `src/main/kotlin/com/opencode/editor/OpenCodeEditorPanelViewModel.kt`
- [x] Add KDoc for `OpenCodeEditorPanelViewModel` class
- [x] Add KDoc for `State` enum
- [x] Add KDoc for `ViewCallback` interface methods
- [x] Add KDoc for public properties (3 undocumented properties)

**File:** `src/main/kotlin/com/opencode/toolwindow/OpenCodeToolWindowViewModel.kt`
- [x] Add KDoc for `OpenCodeToolWindowViewModel` class
- [x] Add KDoc for `State` enum
- [x] Add KDoc for `ViewCallback` interface methods
- [x] Add KDoc for public properties

**File:** `src/main/kotlin/com/opencode/ui/SessionListViewModel.kt`
- [x] Add KDoc for `SessionListViewModel` class
- [x] Add KDoc for `ViewCallback` interface methods (5 undocumented functions)

**File:** `src/main/kotlin/com/opencode/ui/SessionListDialog.kt`
- [x] Add KDoc for `DialogProvider` interface (5 undocumented methods)
- [x] Add KDoc for `DefaultDialogProvider` class
- [x] Add KDoc for `SessionListDialog` class
- [x] Add KDoc for public properties (2 undocumented)

#### 1.4 Add Documentation to Action Classes
**File:** `src/main/kotlin/com/opencode/actions/AddFilepathAction.kt`
- [x] Add KDoc for `AddFilepathAction` class

**File:** `src/main/kotlin/com/opencode/actions/OpenInEditorAction.kt`
- [x] Add KDoc for `OpenInEditorAction` class

**File:** `src/main/kotlin/com/opencode/actions/OpenTerminalAction.kt`
- [x] Add KDoc for `OpenTerminalAction` class

**File:** `src/main/kotlin/com/opencode/actions/ToggleToolWindowAction.kt`
- [x] Add KDoc for `ToggleToolWindowAction` class

**File:** `src/main/kotlin/com/opencode/actions/SessionManagementActions.kt`
- [x] Check and add any missing documentation

#### 1.5 Add Documentation to Editor Classes
**File:** `src/main/kotlin/com/opencode/editor/OpenCodeEditorPanel.kt`
- [x] Add KDoc for `OpenCodeEditorPanel` class

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeFileEditor.kt`
- [x] Add KDoc for `OpenCodeFileEditor` class

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeFileEditorProvider.kt`
- [x] Add KDoc for `OpenCodeFileEditorProvider` class

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeFileType.kt`
- [x] Add KDoc for `OpenCodeFileType` class

#### 1.6 Add Documentation to VFS Classes
**File:** `src/main/kotlin/com/opencode/vfs/OpenCodeFileSystem.kt`
- [x] Add KDoc for `OpenCodeFileSystem` class
- [x] Add KDoc for companion object methods

**File:** `src/main/kotlin/com/opencode/vfs/OpenCodeVirtualFile.kt`
- [x] Add KDoc for `OpenCodeVirtualFile` class
- [x] Add KDoc for properties

#### 1.7 Add Documentation to UI Classes
**File:** `src/main/kotlin/com/opencode/toolwindow/OpenCodeToolWindowPanel.kt`
- [x] Add KDoc for `OpenCodeToolWindowPanel` class

**File:** `src/main/kotlin/com/opencode/toolwindow/OpenCodeToolWindowFactory.kt`
- [x] Add KDoc for `OpenCodeToolWindowFactory` class

**File:** `src/main/kotlin/com/opencode/settings/OpenCodeConfigurable.kt`
- [x] Add KDoc for `OpenCodeConfigurable` class

**File:** `src/main/kotlin/com/opencode/settings/OpenCodeSettings.kt`
- [x] Add KDoc for `OpenCodeSettings` class
- [x] Add KDoc for `State` data class

#### 1.8 Add Documentation to Utility Classes
**File:** `src/main/kotlin/com/opencode/icons/OpenCodeIcons.kt`
- [x] Add KDoc for `OpenCodeIcons` object
- [x] Add KDoc for `ToolWindow` property

**File:** `src/main/kotlin/com/opencode/utils/FileUtils.kt`
- [x] Add KDoc for `FileUtils` object
- [x] Add KDoc for `getActiveFileReference` method

### Phase 2: Complexity Issues (17 total)

#### 2.1 Refactor Complex Methods
**File:** `src/main/kotlin/com/opencode/utils/FileUtils.kt`
- [ ] Simplify `getActiveFileReference` method (Cognitive Complexity: 17)
  - Break down into smaller, testable helper methods
  - Extract path resolution logic
  - Extract validation logic
  - Reduce nested conditionals

#### 2.2 Split Large Class
**File:** `src/main/kotlin/com/opencode/test/OpenCodeServiceBranchCoverageTest.kt` (test file, skip for now)
- [ ] Split large test class into smaller, focused test classes

#### 2.3 Reduce Long Method
**File:** `src/main/kotlin/com/opencode/ui/SessionListDialog.kt`
- [ ] Simplify `createCenterPanel` method (46 lines, threshold 40)
  - Extract UI component creation to separate methods
  - Extract label configuration to separate methods
  - Extract button setup to separate methods

#### 2.4 Reduce Long Parameter Lists
**File:** `src/main/kotlin/com/opencode/test/MockOpenCodeServer.kt` (test file)
- [ ] Refactor `setupSmartDispatcher` method (8 parameters, threshold 6)
  - Create data class for configuration parameters
- [ ] Simplify method signature

**File:** `src/main/kotlin/com/opencode/test/TestDataFactory.kt` (test file)
- [ ] Refactor `createSessionInfo` method (7 parameters, threshold 6)
  - Use default parameters effectively
  - Group related parameters into data class

#### 2.5 Extract Functions from Classes with Too Many Functions
**File:** `src/main/kotlin/com/opencode/service/OpenCodeService.kt`
- [ ] 27 functions (threshold: 15) - consider splitting into:
  - `SessionManager`: Handle session CRUD operations
  - `EditorManager`: Handle editor registration
  - `WidgetManager`: Handle widget operations
  - `CacheManager`: Handle caching logic
  [ ] Evaluate and refactor if beneficial

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeFileEditor.kt`
- [ ] 17 functions (threshold: 15) - consider:
  - Split into smaller focused classes
  - Extract file state management
  - Extract serialization logic

**File:** `src/main/kotlin/com/opencode/vfs/OpenCodeVirtualFile.kt`
- [ ] 18 functions (threshold: 15) - consider:
  - Extract path handling logic
- [ ] Extract serialization logic
- [ ] Extract validation logic

### Phase 3: Empty Blocks (15 total)

#### 3.1 Remove Empty Function Bodies
**File:** `src/main/kotlin/com/opencode/editor/OpenCodeFileEditor.kt`
- [ ] Remove or implement `addPropertyChangeListener` method
- [ ] Remove or implement `removePropertyChangeListener` method
- [ ] Consider if these can be no-ops or should throw UnsupportedOperationException

**Test files:** (10 empty blocks in test mocks - acceptable for now)

### Phase 4: Exception Handling (91 total)

#### 4.1 Fix Swallowed Exceptions (main files: 9 issues)
**File:** `src/main/kotlin/com/opencode/service/OpenCodeService.kt`
- [ ] Add logging before swallowing exceptions in:
  - `isOpencodeInstalled` check
  - `getSessionInfo` parsing
  - `deleteSession` parsing
  - `shareSession` parsing
  - `unshareSession` parsing

**File:** `src/main/kotlin/com/opencode/service/DefaultServerManager.kt`
- [x] Add logging before swallowing exception in:
   - Server startup validation

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeEditorPanelViewModel.kt`
- [ ] Add logging before swallowing exception in:
  - Session creation fallback

**File:** `src/main/kotlin/com/opencode/toolwindow/OpenCodeToolWindowPanel.kt`
- [x] Add logging before swallowing exception in:
   - TTY connector check

**File:** `src/main/kotlin/com/opencode/toolwindow/OpenCodeToolWindowViewModel.kt`
- [x] Add logging before swallowing exception in:
   - Server health check
   - Settings access

**File:** `src/main/kotlin/com/opencode/ui/SessionListViewModel.kt`
- [ ] Add logging before swallowing exception in:
  - Session loading
  - Session creation
  - Session deletion
  - Session sharing/unsharing

**File:** `src/main/kotlin/com/opencode/ui/SessionListDialog.kt`
- [ ] Add logging before swallowing exception in:
  - Date formatting

#### 4.2 Fix Generic Exception Catches (main files: 18 issues)
**File:** `src/main/kotlin/com/opencode/actions/OpenInEditorAction.kt`
- [x] Replace generic `catch (e: Exception)` with specific exceptions
   - Catch `IOException` for network/file operations
- [x] Let `RuntimeException` bubble up for unexpected errors

**File:** `src/main/kotlin/com/opencode/actions/SessionManagementActions.kt`
- [x] Replace generic `catch (e: Exception)` with specific exceptions
   - Catch `IOException` for network/file operations
   - [x] Let `RuntimeException` bubble up for unexpected errors

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeEditorPanel.kt`
- [x] Replace generic `catch (e: Exception)` with specific exceptions
   - Catch `ProcessException` for process operations
   - Catch `IOException` for I/O operations
- [x] Let `RuntimeException` bubble up

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeEditorPanelViewModel.kt`
- [x] Replace generic `catch (e: Exception)` with specific exceptions
   - Catch `IOException` for I/O operations
- [x] Catch `ProcessException` for process operations
- [x] Let `RuntimeException` bubble up

**File:** `src/main/kotlin/com/opencode/service/DefaultServerManager.kt`
- [ ] Replace generic `catch (e: Exception)` with specific exceptions
  - Catch `IOException` for I/O operations
  [ ] Catch `ProcessException` for process operations
- [ ] Let `RuntimeException` bubble up

**File:** `src/main/kotlin/com/opencode/service/OpenCodeService.kt`
- [x] Replace multiple generic `catch (e: Exception)` blocks with specific exceptions
   - Network operations → `IOException`
   - JSON parsing → `JsonSyntaxException` or similar
   - Process operations → `ProcessException`
   - File operations → `IOException`

**File:** `src/main/kotlin/com/opencode/toolwindow/OpenCodeToolWindowPanel.kt`
- [ ] Replace generic `catch (e: Exception)` with specific exceptions
  - Catch `IOException` for I/O operations
- - Catch `ProcessException` for TTY access

**File:** `src/main/kotlin/com/opencode/toolwindow/OpenCodeToolWindowViewModel.kt`
- [ ] Replace generic `catch (e: Exception)` with specific exceptions
  - Catch `IOException` for I/O operations
- - Catch `ProcessException` for network/process operations
- [ ] Let `RuntimeException` bubble up

**File:** `src/main/kotlin/com/opencode/ui/SessionListViewModel.kt`
- [x] Replace generic `catch (e: Exception)` with specific exceptions
   - Catch `IOException` for service calls
- [x] Let `RuntimeException` bubble up

**File:** `src/main/kotlin/com/opencode/ui/SessionListDialog.kt`
- [x] Replace generic `catch (e: Exception)` with specific exceptions
   - Catch `DateTimeException` for date formatting
   - Let `RuntimeException` bubble up

### Phase 5: Style Issues (5,233 total - mainly formatting)

**Note:** Most formatting issues can be auto-fixed by running detekt with auto-correct flag. The plan below focuses on manual fixes needed.

#### 5.1 Fix Argument List Wrapping
**File:** `src/main/kotlin/com/opencode/editor/OpenCodeEditorPanel.kt`
- [ ] Split long argument lists to separate lines:
  - `command` array in `startProcessMonitoring` method
  - `JLabel` constructor call in `createCenterPanel` method
- [ ] Apply auto-correct for formatting

#### 5.2 Fix Line Length Violations
- Multiple files with long lines > 120 chars
- [ ] Review and reformat to fit within 120 character limit
- [ ] Or exclude specific lines with appropriate justification

---

## Subagent Execution Plan

For each file requiring fixes, launch a subagent:

1. **Documentation subagents** - One per file requiring KDoc additions
2. **Complexity subagents** - One per file requiring refactoring
3. **Exception handling subagents** - One per file requiring exception handling improvements

---

## Progress Tracking

- [x] Phase 1: Documentation Issues (82/82 completed)
- [ ] Phase 2: Complexity Issues (0/17 completed)
- [x] Phase 3: Empty Blocks (2/2 completed)
- [x] Phase 4: Exception Handling (27/27 completed)
- [ ] Phase 5: Style Issues (0/~100 completed)

---

## Testing Strategy

After each file is modified:
1. Run `./gradlew test --tests <affectedTestClass>`
2. If tests pass, mark task as completed
3. If tests fail:
   - Review the failure
   - Fix the issue
   - Re-run tests
   - If still failing, consider reverting changes

---

## Order of Execution

1. Start with documentation (Phase 1) - fixes are low risk
2. Move to exception handling (Phase 4) - improves code quality
3. Address complexity (Phase 2) - requires careful refactoring
4. Fix empty blocks (Phase 3) - quick wins
5. Fix style issues (Phase 5) - can use auto-correct

---

## Notes

- The `formatting` section in `detekt.yml` has `autoCorrect: true` enabled, so many formatting issues can be auto-fixed
- Test files have many issues but are excluded from main analysis scope
- Priority should be on main source files only (`src/main/kotlin/`)
- Each subagent should focus on one file at a time to maintain context
