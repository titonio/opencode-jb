# OpenCode

<div align="center">
  <img src="src/main/resources/icons/opencode_13x13.svg" alt="OpenCode Logo" width="120" height="120">
  
  <p><strong>AI-powered coding assistant for JetBrains IDEs</strong></p>

  [![Build](https://github.com/titonio/opencode-jb/workflows/Build/badge.svg)](https://github.com/titonio/opencode-jb/actions)
  [![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/yourusername/opencode-jb/releases)
  [![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
  [![Platform](https://img.shields.io/badge/platform-IntelliJ%20IDEA%202025.3-orange.svg)](https://www.jetbrains.com/idea/)
  [![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-purple.svg)](https://kotlinlang.org/)
</div>

---

## Table of Contents

- [About](#about)
- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Building from Source](#building-from-source)
- [Usage](#usage)
  - [Quick Start](#quick-start)
  - [Keyboard Shortcuts](#keyboard-shortcuts)
  - [Session Management](#session-management)
- [Development](#development)
  - [Project Structure](#project-structure)
  - [Testing](#testing)
  - [Code Coverage](#code-coverage)
- [Contributing](#contributing)
- [License](#license)
- [Support](#support)

## About

OpenCode is an intelligent coding assistant plugin for JetBrains IDEs that integrates AI-powered development tools directly into your IDE. It provides a seamless terminal-like interface for interacting with AI models, managing coding sessions, and enhancing your development workflow.

### Why OpenCode?

- **Native Integration**: Works seamlessly within your JetBrains IDE
- **Session Management**: Organize your AI interactions with multiple sessions
- **Context-Aware**: Automatically includes file paths and project context
- **Flexible Interface**: Choose between tool window or editor tab views
- **Keyboard-First**: Designed for efficiency with comprehensive keyboard shortcuts

## Features

- **AI Terminal Interface**: Interactive terminal-like interface for AI coding assistance
- **Multi-Session Support**: Create and manage multiple coding sessions
- **File Context Integration**: Quickly add file paths to your AI conversations with keyboard shortcuts
- **Flexible Views**: Open OpenCode in a tool window or as an editor tab
- **Custom File System**: Virtual file system for managing AI conversation files
- **Smart Editor**: Specialized editor for `.opencode` files
- **Cross-Platform**: Works on Windows, macOS, and Linux

## Getting Started

### Prerequisites

- JetBrains IntelliJ IDEA 2025.3 or compatible IDE
- Java 21 or higher

### Installation

#### From JetBrains Marketplace (Coming Soon)

1. Open your JetBrains IDE
2. Go to `Settings/Preferences` → `Plugins` → `Marketplace`
3. Search for "OpenCode"
4. Click `Install` and restart your IDE

#### Manual Installation

1. Download the latest release from [Releases](https://github.com/yourusername/opencode-jb/releases)
2. Open your IDE and go to `Settings/Preferences` → `Plugins` → `⚙️` → `Install Plugin from Disk`
3. Select the downloaded `.zip` file
4. Restart your IDE

### Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/opencode-jb.git
cd opencode-jb

# Build the plugin
./gradlew build

# The plugin will be available in build/distributions/
```

## Usage

### Quick Start

1. **Open OpenCode Terminal**:
   - Press `Ctrl+Esc` (Windows/Linux) or `⌘+Esc` (macOS)
   - Or go to `Tools` → `OpenCode` → `Open OpenCode`

2. **Add File Context**:
   - Right-click in any file editor
   - Select `Add Filepath to OpenCode`
   - Or press `Ctrl+Alt+K` (Windows/Linux) or `⌘+⌥+K` (macOS)

3. **Manage Sessions**:
   - Press `Ctrl+Shift+O` (Windows/Linux) or `⌘+⇧+O` (macOS) to list sessions
   - Press `Ctrl+Shift+N` (Windows/Linux) or `⌘+⇧+N` (macOS) to create a new session

### Keyboard Shortcuts

| Action | Windows/Linux | macOS |
|--------|---------------|-------|
| Open OpenCode | `Ctrl+Esc` | `⌘+Esc` |
| Add File to Context | `Ctrl+Alt+K` | `⌘+⌥+K` |
| New Session | `Ctrl+Shift+N` | `⌘+⇧+N` |
| List Sessions | `Ctrl+Shift+O` | `⌘+⇧+O` |
| Toggle Tool Window | `Alt+Shift+W` | `⌥+⇧+W` |

### Session Management

OpenCode supports multiple sessions to help organize your AI interactions:

- **Create Session**: Use the "New Session" action to start a fresh conversation
- **Switch Sessions**: Use "List Sessions" to view and switch between active sessions
- **Session Persistence**: All sessions are automatically saved and restored

## Development

### Project Structure

```
opencode-jb/
├── src/
│   ├── main/
│   │   ├── kotlin/com/opencode/
│   │   │   ├── actions/          # IDE actions
│   │   │   ├── api/              # API clients
│   │   │   ├── editor/           # Custom editors
│   │   │   ├── icons/            # Icon resources
│   │   │   ├── model/            # Data models
│   │   │   ├── services/         # Core services
│   │   │   ├── toolwindow/       # Tool window UI
│   │   │   ├── ui/               # UI components
│   │   │   ├── utils/            # Utilities
│   │   │   └── vfs/              # Virtual file system
│   │   └── resources/
│   │       ├── icons/
│   │       └── META-INF/
│   └── test/
│       ├── kotlin/               # Unit tests
│       └── resources/            # Test resources
├── build.gradle.kts              # Build configuration
└── gradle.properties             # Project properties
```

### Testing

Run all tests:
```bash
./gradlew test
```

Run a specific test class:
```bash
./gradlew test --tests 'com.opencode.YourTestClass'
```

### Code Coverage

The project uses Kover for code coverage analysis.

```bash
# Generate coverage report
./gradlew koverHtmlReport

# View report at: build/reports/kover/html/index.html
```

Current coverage threshold: 35% (see [build.gradle.kts](build.gradle.kts) for configuration)

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](AGENTS.md) for details on:

- Code style and conventions
- Testing requirements
- Pull request process
- Development setup

### Quick Contribution Steps

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/opencode-jb/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/opencode-jb/discussions)
- **Documentation**: [Wiki](https://github.com/yourusername/opencode-jb/wiki)

---

<div align="center">
  Made with ❤️ by the OpenCode team
  
  [⭐ Star us on GitHub](https://github.com/yourusername/opencode-jb)
</div>
