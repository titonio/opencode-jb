# OpenCode IntelliJ Plugin - Suggested Commands

## Build Commands

### Full Build
```bash
./gradlew clean build
```
- Cleans and builds entire project
- Compiles main and test sources
- Runs all tests
- Verifies plugin compatibility

### Build Plugin Distribution
```bash
./gradlew buildPlugin
```
- Builds plugin ZIP file
- Output: `build/distributions/opencode-jb-1.0.0.zip`
- Use for manual installation

### Compile Only (Faster)
```bash
./gradlew compileKotlin compileTestKotlin
```
- Compiles without running tests
- Useful for quick syntax checks

### Quick Build
```bash
./gradlew build --continue
```
- Continues on failure
- Useful for comprehensive error collection

## Testing Commands

### Run All Tests
```bash
./gradlew test --continue
```
- Runs all tests
- Continues on failure to show all errors

### Run Specific Test Class
```bash
./gradlew test --tests 'com.opencode.ui.SessionListViewModelTest'
```
- Runs all tests in a specific class

### Run Specific Test Method
```bash
./gradlew test --tests 'com.opencode.ui.SessionListViewModelTest.loadSessions calls service and notifies callback with results'
```
- Note: Use quotes for test methods with spaces

### Run with Detailed Output
```bash
./gradlew test --info
```
- Shows detailed test execution info
- Useful for debugging test failures

### Run with Stack Traces
```bash
./gradlew test --stacktrace
```
- Shows full stack traces for failures

### Run Tests in Parallel (Faster)
```bash
./gradlew test --parallel --max-workers=4
```
- Uses multiple workers
- Significantly faster on multi-core systems

## Coverage Commands

### Generate HTML Coverage Report
```bash
./gradlew test koverHtmlReport
```
- Runs tests and generates HTML coverage report
- Output: `build/reports/kover/html/index.html`

### View Coverage Report
```bash
# macOS
open build/reports/kover/html/index.html

# Linux
xdg-open build/reports/kover/html/index.html
```

### Generate XML Coverage Report
```bash
./gradlew koverXmlReport
```
- Generates machine-readable XML report
- Output: `build/reports/kover/report.xml`

### Verify Coverage Threshold
```bash
./gradlew koverVerify
```
- Verifies coverage meets minimum (35%)
- Fails build if below threshold

### Get Coverage Percentage in Console
```bash
./gradlew koverLog
```
- Prints coverage percentages to console
- Quick coverage check without report generation

## Verification Commands

### Verify Plugin Compatibility
```bash
./gradlew verifyPlugin
```
- Verifies plugin compatibility with target IDE versions
- Checks compatibility with IntelliJ IDEA 2025.3

### Run All Verification Tasks
```bash
./gradlew check
```
- Runs linting, tests, and verification
- Comprehensive quality check

## Development Commands

### Build and Run Plugin in IDE Sandbox
```bash
./gradlew runIde
```
- Launches test IntelliJ instance with plugin
- Useful for interactive testing
- Changes are hot-reloaded

### Clean Build
```bash
./gradlew clean
```
- Removes build artifacts
- Useful before fresh builds

## Git Commands

### Check Git Status
```bash
git status
```
- Shows working tree status
- Useful before committing

### Show Differences
```bash
git diff
```
- Shows unstaged changes
- Useful for reviewing changes

### View Recent Commits
```bash
git log --oneline -10
```
- Shows recent commit history
- Useful for context

## Utility Commands

### List Files
```bash
ls -la
```
- Lists all files including hidden ones

### Search in Files
```bash
grep -r "searchTerm" src/
```
- Recursively searches for text in source files
- Useful for finding usages

### Find Files by Pattern
```bash
find . -name "*.kt" -type f
```
- Finds all Kotlin files
- Modify pattern for other file types

## Platform Test Commands (When Implemented)

### Run Platform Tests Only
```bash
./gradlew test --tests "*PlatformTest"
```
- Runs only platform tests
- Requires Xvfb on headless systems

### Run Specific Platform Test
```bash
./gradlew test --tests 'com.opencode.editor.EditorSplittingSessionPersistencePlatformTest'
```

## CI/CD Commands

### Run CI Tests Locally
```bash
./gradlew test koverHtmlReport --continue
```
- Simulates CI pipeline
- Useful for pre-commit checks

## Troubleshooting Commands

### Clean and Rebuild
```bash
./gradlew clean build --refresh-dependencies
```
- Refreshes all dependencies
- Useful when build seems corrupted

### Show Detailed Debug Info
```bash
./gradlew test --debug
```
- Shows debug-level logging
- Useful for investigating issues

### Check Dependencies
```bash
./gradlew dependencies
```
- Shows dependency tree
- Useful for resolving version conflicts

## After Task Completion

### Recommended Workflow
```bash
# 1. Run all tests
./gradlew test --continue

# 2. Verify plugin compatibility
./gradlew verifyPlugin

# 3. Generate coverage report
./gradlew test koverHtmlReport

# 4. Run comprehensive check
./gradlew check

# 5. Check git status
git status
```

## Performance Tips

### Faster Iterative Development
```bash
# Use parallel testing
./gradlew test --parallel --max-workers=4

# Compile only for quick syntax checks
./gradlew compileKotlin compileTestKotlin

# Skip tests when not needed
./gradlew build -x test
```

### Memory Optimization
```bash
# Increase Gradle memory
./gradlew build -Dorg.gradle.jvmargs="-Xmx4g"
```

## Common Patterns

### Test + Coverage Combo
```bash
./gradlew test koverHtmlReport && open build/reports/kover/html/index.html
```
- Runs tests and immediately opens coverage report

### Clean Build + Test
```bash
./gradlew clean build test koverHtmlReport
```
- Fresh build with comprehensive testing
