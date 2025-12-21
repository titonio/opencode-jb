# OpenCode Session Persistence Implementation Plan

## Overview

This document outlines the comprehensive plan to implement persistent OpenCode sessions across IntelliJ IDEA editor tab drag/move operations, with advanced session management UI.

## Design Decisions

Based on user requirements:

1. **Q1: Session Reuse Behavior** - Option B: Each tab gets its own session, but dragging preserves that tab's specific session
2. **Q2: Multiple Tabs Visual Behavior** - Option B: Only allow one OpenCode editor tab per project at a time
3. **Q3: Server Lifetime** - Option B: Stop server when last OpenCode editor tab closes (but session persists on disk)
4. **Q4: Session Management UI** - Option C: Advanced session management (session picker, history, list, create, delete, share)

### Additional Preferences

- **JSON Library:** Gson (not org.json)
- **Session Title Editing:** Not included
- **Session Sharing UI:** Include integration with OpenCode's share feature
- **Missing OpenCode CLI:** Offer download link/instructions
- **Session Cleanup:** Auto-cleanup, keeping only 10 most recent sessions
- **Keyboard Shortcuts:** 
  - Ctrl+Shift+O (Cmd+Shift+O on Mac) - List Sessions
  - Ctrl+Shift+N (Cmd+Shift+N on Mac) - New Session

---

## Architecture Design

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    IntelliJ Project                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────┐       ┌──────────────────────┐   │
│  │  OpenCodeService    │◄──────┤ OpenCodeFileEditor   │   │
│  │  (Project-Level)    │       │  (Single Instance)   │   │
│  ├─────────────────────┤       ├──────────────────────┤   │
│  │ - sharedServerPort  │       │ - currentSessionId   │   │
│  │ - sharedServerProc  │       │ - terminalWidget     │   │
│  │ - activeTabFile     │       │ - serialize/restore  │   │
│  │ - sessionCache      │       └──────────────────────┘   │
│  │ - cleanupOldSess.   │                                   │
│  └─────────────────────┘                                   │
│           ▲                                                 │
│  ┌────────┴───────────────────────────────────┐           │
│  │   Session Management Actions & UI          │           │
│  ├────────────────────────────────────────────┤           │
│  │ - SessionListDialog (with Share button)    │           │
│  │ - ListSessionsAction                       │           │
│  │ - NewSessionAction                         │           │
│  │ - DeleteSessionAction                      │           │
│  │ - ShareSessionAction                       │           │
│  │ - CheckOpencodeInstalled                   │           │
│  └────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
                        │
                        │ HTTP API (Gson for JSON)
                        ▼
┌─────────────────────────────────────────────────────────────┐
│         OpenCode Server (localhost:port)                    │
├─────────────────────────────────────────────────────────────┤
│  GET  /session              - List all sessions             │
│  POST /session              - Create new session            │
│  POST /session/:id/share    - Share session                 │
│  DELETE /session/:id/share  - Unshare session               │
│  DELETE /session/:id        - Delete session                │
└─────────────────────────────────────────────────────────────┘
```

### Key Concepts

1. **Shared Server Per Project**
   - One OpenCode server process per IntelliJ project
   - Server starts when first OpenCode editor tab opens
   - Server stops when last OpenCode editor tab closes
   - All editor tabs connect to the same shared server

2. **Session Persistence**
   - Sessions stored on disk by OpenCode server (`~/.local/share/opencode/storage/`)
   - Session IDs serialized in `FileEditorState` during tab drag/move
   - On tab recreation, session ID is restored and reconnected

3. **Single Tab Enforcement**
   - Only one OpenCode editor tab allowed per project
   - Opening a second tab activates the existing one instead
   - Simplifies implementation and prevents UI conflicts

4. **Advanced Session Management**
   - List all available sessions
   - Create new sessions with custom titles
   - Delete old sessions
   - Share sessions and copy share URLs
   - Auto-cleanup keeping only 10 most recent sessions

---

## Implementation Phases

### Phase 1: Core Infrastructure

#### 1.1 Update Dependencies

**File:** `build.gradle.kts`

Add dependencies:
```kotlin
dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

#### 1.2 Create Data Models

**New File:** `src/main/kotlin/com/opencode/model/SessionModels.kt`

Create Gson-annotated data classes:
- `SessionInfo` - Session metadata from API
- `TimeInfo` - Session timestamps
- `ShareInfo` - Session share details
- `CreateSessionRequest` - Request body for session creation
- `SessionResponse` - Response from session creation

Key fields:
- `id: String` - Unique session identifier
- `title: String` - Session title
- `directory: String` - Project directory
- `time.created: Long` - Creation timestamp
- `time.updated: Long` - Last update timestamp
- `share.url: String?` - Share URL if shared

#### 1.3 Enhanced OpenCodeService

**File:** `src/main/kotlin/com/opencode/service/OpenCodeService.kt`

Add functionality:
- **Shared server management**: Start/stop OpenCode server process
- **Session operations**: Create, list, get, delete via HTTP API
- **Session sharing**: Share/unshare sessions, get share URLs
- **Session cleanup**: Auto-delete old sessions, keep 10 most recent
- **Installation check**: Verify OpenCode CLI is installed
- **Single tab enforcement**: Track active editor tab

Key methods:
- `getOrStartSharedServer(): Int?` - Get server port, start if needed
- `createSession(title: String?): String` - Create new session, return ID
- `listSessions(forceRefresh: Boolean): List<SessionInfo>` - List all sessions
- `getSession(sessionId: String): SessionInfo?` - Get session details
- `deleteSession(sessionId: String): Boolean` - Delete session
- `shareSession(sessionId: String): String?` - Share session, return URL
- `unshareSession(sessionId: String): Boolean` - Unshare session
- `isOpencodeInstalled(): Boolean` - Check CLI availability
- `registerActiveEditor(file: VirtualFile)` - Register active tab
- `unregisterActiveEditor(file: VirtualFile)` - Unregister, stop server if last

#### 1.4 OpenCodeFileEditor with State Serialization

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeFileEditor.kt`

Implement state persistence:
- **Serialize**: Save `sessionId` and `serverPort` to `FileEditorState`
- **Deserialize**: Restore `sessionId` and `serverPort` on tab recreation
- **Verify**: Check if restored session still exists
- **Fallback**: Create new session if restored one is missing

Key methods:
- `setState(state: FileEditorState)` - Restore session from serialized state
- `getState(level: FileEditorStateLevel): FileEditorState` - Serialize current state
- `initializeWidget()` - Create terminal widget connected to session
- `createTerminalForSession(port: Int, sessionId: String)` - Launch `opencode attach --session <id>`

Handle edge cases:
- OpenCode CLI not installed → Show install dialog
- Server fails to start → Show error message
- Session not found → Create new session, notify user

#### 1.5 Single Tab Enforcement

**File:** `src/main/kotlin/com/opencode/editor/OpenCodeFileEditorProvider.kt`

Enforce one tab per project:
- Check if editor tab already exists via `OpenCodeService.hasActiveEditor()`
- If exists, activate existing tab instead of creating new one
- Still return a `FileEditor` instance (IntelliJ requirement), but it won't be used

---

### Phase 2: Advanced Session Management UI

#### 2.1 Session List Dialog

**New File:** `src/main/kotlin/com/opencode/ui/SessionListDialog.kt`

Create dialog with:
- **List view**: Show all sessions sorted by update time
- **Session info**: Display title, ID (truncated), update date, share status (🔗 icon)
- **Actions**:
  - "New Session" - Create with custom title
  - "Delete" - Remove session (with confirmation)
  - "Share" - Share/unshare session
  - "Copy URL" - Copy share URL to clipboard
  - "Refresh" - Reload session list
- **Open button**: Open selected session in editor

Custom cell renderer:
- Bold session title
- Show share icon if shared
- Display formatted date and truncated ID

#### 2.2 Session Management Actions

**New File:** `src/main/kotlin/com/opencode/actions/SessionManagementActions.kt`

Create actions:
- `ListSessionsAction` - Show session list dialog
- `NewSessionAction` - Create new session directly

Both actions:
1. Close existing OpenCode editor tab if open
2. Create new `OpenCodeVirtualFile`
3. Open editor with `FileEditorManager`
4. Set editor state with selected/new session ID

#### 2.3 Update Plugin Configuration

**File:** `src/main/resources/META-INF/plugin.xml`

Add action group:
```xml
<group id="OpenCode.SessionManagement" text="OpenCode Sessions" popup="true">
    <action id="OpenCode.ListSessions" 
            class="com.opencode.actions.ListSessionsAction" 
            text="List Sessions..." 
            description="View and manage OpenCode sessions">
        <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift O"/>
        <keyboard-shortcut keymap="Mac OS X" first-keystroke="meta shift O"/>
    </action>
    
    <action id="OpenCode.NewSession" 
            class="com.opencode.actions.NewSessionAction" 
            text="New Session" 
            description="Create a new OpenCode session">
        <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift N"/>
        <keyboard-shortcut keymap="Mac OS X" first-keystroke="meta shift N"/>
    </action>
    
    <add-to-group group-id="ToolsMenu" anchor="last"/>
</group>
```

---

### Phase 3: Testing Strategy

#### Test Scenarios

**Session Persistence:**
- [ ] Tab drag within same split → Session continues
- [ ] Tab move to new split → Session continues
- [ ] Tab detach to floating window → Session continues
- [ ] Tab reattach from floating window → Session continues

**Single Tab Enforcement:**
- [ ] Try opening second tab → First tab activated
- [ ] Switch session while tab open → Old tab closes, new opens

**Server Lifecycle:**
- [ ] First tab opens → Server starts
- [ ] Last tab closes → Server stops
- [ ] Multiple session switches → Same server reused

**Session Management UI:**
- [ ] List sessions → All shown, sorted by date
- [ ] Create new session → Dialog works, session created
- [ ] Delete session → Confirmation, removed from disk
- [ ] Share session → URL generated, 🔗 icon shown
- [ ] Unshare session → Share removed
- [ ] Copy share URL → Clipboard contains URL
- [ ] Auto-cleanup → Only 10 most recent kept after creating 15

**Error Handling:**
- [ ] OpenCode not installed → Install dialog shown
- [ ] Server fails to start → Error message, retry logic
- [ ] Session not found on restore → New session created, user notified

---

### Phase 4: Error Handling & Edge Cases

#### 4.1 Server Connection Failures

**Problem:** OpenCode server fails to start or crashes

**Solution:**
- Retry logic (3 attempts) in `getOrStartSharedServer()`
- Show notification if all attempts fail
- Provide manual "Restart Server" action

#### 4.2 Session Not Found

**Problem:** Serialized session ID no longer exists on disk

**Solution:**
- Verify session exists via API on `setState()`
- If not found, create new session automatically
- Show warning notification to user

#### 4.3 Multiple Projects Open

**Problem:** User has multiple IntelliJ projects open

**Solution:**
- Already handled by `@Service(Service.Level.PROJECT)` annotation
- Each project gets its own `OpenCodeService` instance
- Each project gets its own server on different port
- Sessions scoped by project directory via `?directory=` query parameter

#### 4.4 OpenCode CLI Not Installed

**Problem:** User doesn't have OpenCode installed

**Solution:**
- Check via `isOpencodeInstalled()` in editor init
- Show dialog with installation instructions
- Link to https://opencode.ai/install
- Prevent editor from initializing until installed

---

## Implementation Checklist

### Phase 1: Core Infrastructure ⏱️ 4-6 hours
- [ ] Update `build.gradle.kts` with Gson dependency
- [ ] Create `SessionModels.kt` with data classes
- [ ] Update `OpenCodeService.kt`:
  - [ ] Shared server management
  - [ ] Session CRUD operations (create, list, get, delete)
  - [ ] Session sharing (share, unshare)
  - [ ] Session cleanup (keep 10 most recent)
  - [ ] Installation check
  - [ ] Single tab enforcement tracking
  - [ ] Server lifecycle management
- [ ] Update `OpenCodeFileEditor.kt`:
  - [ ] State serialization/deserialization
  - [ ] Session restoration with verification
  - [ ] Terminal widget creation with session
  - [ ] Error handling (CLI not found, session missing)
- [ ] Create `OpenCodeEditorState` data class
- [ ] Update `OpenCodeFileEditorProvider.kt`:
  - [ ] Single tab enforcement logic
- [ ] Test basic session persistence on tab drag

### Phase 2: Advanced UI ⏱️ 3-4 hours
- [ ] Create `SessionListDialog.kt`:
  - [ ] List view with custom cell renderer
  - [ ] New Session button
  - [ ] Delete button with confirmation
  - [ ] Share/Unshare button
  - [ ] Copy URL button
  - [ ] Refresh button
  - [ ] Session selection handling
- [ ] Create `SessionManagementActions.kt`:
  - [ ] `ListSessionsAction`
  - [ ] `NewSessionAction`
  - [ ] Helper method to open editor with session
- [ ] Update `plugin.xml`:
  - [ ] Add action group
  - [ ] Configure keyboard shortcuts
  - [ ] Add to Tools menu

### Phase 3: Testing ⏱️ 2-3 hours
- [ ] Test all tab drag scenarios
- [ ] Test single tab enforcement
- [ ] Test server lifecycle
- [ ] Test session list UI
- [ ] Test session creation
- [ ] Test session deletion
- [ ] Test session sharing
- [ ] Test auto-cleanup (create 15 sessions)
- [ ] Test error scenarios
- [ ] Test keyboard shortcuts

### Phase 4: Error Handling ⏱️ 2-3 hours
- [ ] Add server start retry logic with notifications
- [ ] Add session existence verification
- [ ] Add OpenCode installation check dialog
- [ ] Add graceful degradation for missing sessions
- [ ] Add loading indicators in dialogs
- [ ] Add comprehensive error messages

### Phase 5: Polish ⏱️ 1-2 hours
- [ ] Add tooltips to UI elements
- [ ] Add help text in dialogs
- [ ] Test on Windows, macOS, Linux
- [ ] Update README with session management instructions
- [ ] Add screenshots/GIFs of session management
- [ ] Add icons for session-related actions (optional)

---

## Dependencies & Requirements

### Required Libraries

```kotlin
dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### External Requirements

- **OpenCode CLI** must be installed and in PATH
- OpenCode version must support:
  - `opencode serve` command
  - `opencode attach` command with `--session` flag
  - HTTP API endpoints: `/session`, `/session/:id/share`
- OpenCode server must be running for session operations

### Supported Platforms

- ✅ Linux
- ✅ macOS
- ✅ Windows

---

## OpenCode Session Concepts

### What is a Session?

An OpenCode session represents a conversation thread between user and AI, containing:
- **Conversation history**: All messages (user prompts and AI responses)
- **Context state**: Directory, project association, working tree
- **Metadata**: Title, timestamps, version info
- **Change tracking**: File diffs, summaries of modifications
- **Share status**: Optional shareable URL

### Session Storage

Sessions are stored persistently:
- **Location**: `~/.local/share/opencode/storage/session/<projectID>/<sessionID>.json`
- **Format**: JSON files with complete conversation history
- **Lifetime**: Persist indefinitely until explicitly deleted
- **Migration**: Automatic schema migration on version upgrades

### Session Identification

- **ID Format**: Descending ULIDs (e.g., `ses_abc123xyz...`)
- **Globally unique**: Can be used across clients/machines
- **No expiration**: Sessions never time out

### Session Operations

#### Via CLI:
```bash
# Create/resume session
opencode --session <sessionID>

# Continue most recent session
opencode --continue

# List all sessions
opencode session list

# Attach to running server
opencode attach http://localhost:4096 --session <sessionID>
```

#### Via HTTP API:
```http
GET  /session                    # List all sessions
POST /session                    # Create new session
GET  /session/:id                # Get session details
DELETE /session/:id              # Delete session
POST /session/:id/share          # Share session
DELETE /session/:id/share        # Unshare session
POST /session/:id/message        # Send message to session
```

---

## Implementation Timeline

### Estimated Time Breakdown

- **Phase 1 (Core Infrastructure):** 4-6 hours
- **Phase 2 (Advanced UI):** 3-4 hours
- **Phase 3 (Testing):** 2-3 hours
- **Phase 4 (Error Handling):** 2-3 hours
- **Phase 5 (Polish):** 1-2 hours

**Total:** ~12-18 hours of focused development

### Suggested Schedule

**Day 1: Core Infrastructure (4-6 hours)**
1. Update dependencies
2. Create data models
3. Enhance OpenCodeService
4. Update OpenCodeFileEditor
5. Test basic persistence

**Day 2: Advanced UI (3-4 hours)**
6. Create SessionListDialog
7. Create actions
8. Update plugin.xml
9. Test UI operations

**Day 3: Testing & Polish (4-6 hours)**
10. Run comprehensive tests
11. Fix bugs
12. Add error handling
13. Polish UI and UX
14. Documentation

---

## Success Criteria

### Must Have ✅

- [x] Session ID persists across tab drag/move
- [x] Only one OpenCode tab per project allowed
- [x] Shared server starts/stops automatically
- [x] Session list dialog works
- [x] New session creation works
- [x] Session deletion works
- [x] Session sharing works
- [x] Auto-cleanup keeps 10 sessions
- [x] OpenCode installation check

### Nice to Have 🎯

- [ ] Session title editing (excluded by user request)
- [ ] Session export/import
- [ ] Session search/filter in list
- [ ] Custom icons for actions
- [ ] Session statistics (message count, etc.)

---

## Known Limitations

1. **Single Tab per Project**: Users cannot have multiple OpenCode tabs open simultaneously in the same project (by design)

2. **Terminal Widget Sharing**: Each editor tab gets its own terminal widget instance, even when connecting to the same session (Swing component limitation)

3. **Visual Sync**: If somehow multiple tabs exist (edge case), scroll position and cursor may differ between tabs showing same session

4. **OpenCode Dependency**: Requires external OpenCode CLI to be installed and accessible

5. **Network Requirement**: Requires localhost HTTP API to be functional (typically always available)

---

## Future Enhancements (Not in Scope)

- Session templates
- Session branching/forking UI
- Session merge capabilities
- Session analytics dashboard
- Integration with IDE's own project structure
- Session backup/restore to cloud
- Collaborative session editing
- Session playback/replay

---

## References

### OpenCode Documentation

- Session API: `/docs/refs/opencode/packages/opencode/src/session/index.ts`
- Server API: `/docs/refs/opencode/packages/opencode/src/server/server.ts`
- Storage: `/docs/refs/opencode/packages/opencode/src/storage/storage.ts`
- CLI Commands: `/docs/refs/opencode/packages/opencode/src/cli/`

### IntelliJ Platform

- FileEditor API: https://plugins.jetbrains.com/docs/intellij/editors.html
- FileEditorState: https://plugins.jetbrains.com/docs/intellij/editor-basics.html
- Services: https://plugins.jetbrains.com/docs/intellij/plugin-services.html

---

## Contact & Support

For questions or issues during implementation:
1. Review OpenCode source code in `/docs/refs/opencode/`
2. Check IntelliJ Platform Plugin SDK documentation
3. Test with `opencode --help` and `opencode serve --help`

---

**Document Version:** 1.0  
**Last Updated:** December 21, 2025  
**Status:** Ready for Implementation
