# Agent Guidelines for opencode-jb

## Build/Run/Test
- **Install**: `bun install` (JS/TS projects); `./gradlew build` (Kotlin/Java)
- **Build**: `bun run build` or `./gradlew build`
- **Typecheck**: `bun run typecheck` (JS/TS)
- **Test (all)**: `bun test` or `./gradlew test`
- **Run a single test** (Gradle): `./gradlew test --tests 'com.example.MyTestClass'`
- **Lint**: Use bun or Gradle plugins if configured, see package/gradle files.

## Code Style
- **Frameworks**: Bun (JS/TS ESM), Kotlin/Java, IntelliJ Platform
- **Imports**: Relative for local modules; named imports preferred
- **Types**: Use TypeScript interfaces; prefer precise types, avoid `any`, prefer Zod schemas for validation
- **Naming**: camelCase for vars/fns, PascalCase for classes/namespaces
- **Error Handling**: Use Result patterns (TS), avoid `try/catch` if possible
- **Structure**: Namespace org for tools (e.g. `Tool.define()`). Context passed via `sessionID`, DI via `App.provide()`
- **Validation**: Validate all inputs with Zod (TS) or appropriate validation (Kotlin/Java)
- **Logging**: Use structured patterns, e.g., `Log.create({service: "name"})`

## Testing Principles
- Prefer precise, non-flaky, and fast tests.
- Run or debug single tests using the examples above.

## Misc
- No Cursor or Copilot rules found.
- Refer to CONTRIBUTING.md and STYLE_GUIDE.md for expanded guidance.
