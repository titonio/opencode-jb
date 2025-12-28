# IntelliJ Plugin Structure & Components

## Project Structure

A standard IntelliJ Platform plugin project (Gradle-based) typically looks like this:

```
my-plugin/
├── build.gradle.kts        # Gradle build script
├── settings.gradle.kts     # Gradle settings
├── src/
│   ├── main/
│   │   ├── kotlin/         # Kotlin source files
│   │   ├── java/           # Java source files
│   │   └── resources/
│   │       └── META-INF/
│   │           └── plugin.xml  # Plugin configuration file
│   └── test/
│       └── kotlin/         # Tests
```

## Key Components

### 1. Actions (`AnAction`)
Actions are the main way users interact with the plugin (menus, toolbars, shortcuts).
- **Class**: Extend `com.intellij.openapi.actionSystem.AnAction`.
- **Registration**: In `plugin.xml` under `<actions>` or programmatically.
- **Method**: Override `actionPerformed(AnActionEvent e)`.

### 2. Services
Services are singletons (application-level or project-level) used to store state or logic.
- **Class**: Any class.
- **Registration**: In `plugin.xml` under `<extensions defaultExtensionNs="com.intellij">` as `<applicationService>` or `<projectService>`.
- **Usage**: `service<MyService>()` or `project.service<MyProjectService>()`.

### 3. Listeners
Listeners allow the plugin to react to events (file changes, project open/close).
- **Topic**: Define a topic interface or use existing ones (e.g., `FileEditorManagerListener.TOPIC`).
- **Registration**: In `plugin.xml` under `<applicationListeners>` or `<projectListeners>`.

### 4. Extension Points
Allow other plugins to extend your plugin, or for your plugin to extend the IDE.
- **Definition**: `<extensionPoints>` in `plugin.xml`.
- **Usage**: `<extensions defaultExtensionNs="com.intellij">` to use platform extensions (e.g., `toolWindow`, `completionContributor`).

### 5. Tool Windows
Side panels (like Project view, Terminal).
- **Registration**: `<toolWindow>` in `plugin.xml`.
- **Factory**: Implement `ToolWindowFactory`.

## Gradle Configuration

The `gradle-intellij-plugin` handles:
- Downloading the target IDE.
- Patching `plugin.xml`.
- Verifying plugin compatibility.
- Running the IDE with the plugin installed.
- Publishing to the Marketplace.

See `assets/build.gradle.kts` for a template.
