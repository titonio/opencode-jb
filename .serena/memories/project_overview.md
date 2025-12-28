# OpenCode IntelliJ Plugin - Project Overview

## Project Purpose
OpenCode is an AI-powered coding assistant plugin for JetBrains IDEs that provides a seamless terminal-like interface for interacting with AI models, managing coding sessions, and enhancing development workflows.

## Tech Stack
- **Language**: Kotlin 2.3.0 (JVM target: Java 21)
- **Platform**: IntelliJ Platform SDK 2025.3
- **Build Tool**: Gradle 9.2.1 with Kotlin DSL
- **Testing**: JUnit 5 (Jupiter), Mockito Kotlin, MockWebServer
- **Async**: Kotlin Coroutines 1.10.2 (Platform-bundled, compileOnly)
- **HTTP**: OkHttp 4.12.0
- **JSON**: Gson 2.10.1, Jackson 2.17.0
- **Coverage**: Kover 0.7.5 (target: 35% minimum, current: 72.12%)

## Codebase Structure
```
src/main/kotlin/com/opencode/
├── actions/          # IDE actions (menu items, shortcuts)
├── editor/           # Custom file editor for .opencode files
├── icons/            # Icon resources
├── model/            # Data models (SessionInfo, TimeInfo, ShareInfo)
├── service/          # Core services (OpenCodeService, ServerManager)
├── settings/         # Plugin settings/configuration
├── toolwindow/       # Tool window UI components
├── ui/               # Reusable UI components and ViewModels
├── utils/            # Utility functions
└── vfs/              # Virtual file system for opencode:// protocol

src/test/kotlin/com/opencode/
├── [mirrors main structure] # Test classes mirror main structure
└── test/             # Test infrastructure (MockServerManager, TestDataFactory, OpenCodePlatformTestBase)
```

## Key Architectural Patterns

### ViewModel Pattern (⭐ Preferred)
Separates business logic from UI for comprehensive unit testing without platform dependencies.

**Structure**:
- ViewModel manages state and business logic
- ViewCallback interface for UI updates
- 100% testable business logic
- Implemented in SessionListViewModel (96% coverage), OpenCodeEditorPanelViewModel (99%), OpenCodeToolWindowViewModel (92%)

### Service Pattern
- OpenCodeService: Project-level service managing OpenCode server lifecycle
- Session CRUD operations (create, list, delete, share)
- HTTP API client for OpenCode backend
- Lazy initialization with caching

### Testing Architecture
- **Current Coverage**: 72.12% (1,023 tests, 100% pass rate)
- **Architectural Ceiling**: 72-75% for unit tests
- **ViewModels**: 90-99% coverage
- **Services**: 70-86% coverage
- **VFS**: 95% coverage
- **Utils**: 92% coverage

## Important Notes

### Dependency Configuration
- Coroutines must use `compileOnly` to let Platform provide version at runtime
- Prevents version conflicts with IntelliJ Platform 2025.3
- Example: `compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")`

### Testing Constraints
- Platform-dependent UI components (dialogs, panels) require IntelliJ Platform runtime
- Cannot be fully unit-tested (0-25% coverage typical)
- Business logic extracted to ViewModels for testability

### Coverage Limitations
- UI components: 0-25% (requires platform infrastructure)
- Actions update() methods: ~30% (requires action context)
- Business logic: ~100% covered
