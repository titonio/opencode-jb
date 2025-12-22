# Agent Guidelines for opencode-jb

## Build/Lint/Test
- **Install**: `bun install` (JS/TS); `./gradlew build` (Kotlin/Java)
- **Build**: `bun run build` or `./gradlew build`
- **Typecheck**: `bun run typecheck` (JS/TS)
- **Test all**: `bun test` (JS/TS) or `./gradlew test`
- **Single test**: `./gradlew test --tests 'com.example.MyTestClass'`
- **Lint**: Use associated Bun or Gradle lint plugins if configured.

## Code Style
- **Frameworks**: Bun (JS/TS ESM), Kotlin/Java, IntelliJ Platform
- **Imports**: Prefer relative for local modules, named imports elsewhere
- **Types**: Use interfaces (TS), avoid `any`, prefer Zod schemas or robust types
- **Naming**: camelCase for variables/functions; PascalCase for classes/namespaces
- **Error handling**: Prefer Result patterns (TS); minimize try/catch, rely on language idioms
- **Structure**: Use namespaces (e.g. `Tool.define()`), pass context (`sessionID`), DI via `App.provide()`
- **Validation**: All external input validated (Zod for TS, idiomatic for Kotlin/Java)
- **Logging**: Structured, e.g., `Log.create({service: "name"})`

## Testing
- Write fast, precise, non-flaky tests
- Prefer running/debugging single tests with provided commands

## Misc
- No Cursor or Copilot rules at present
- See CONTRIBUTING.md or STYLE_GUIDE.md (if present) for deeper style detail
