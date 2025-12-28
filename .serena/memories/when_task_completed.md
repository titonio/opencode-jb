# OpenCode IntelliJ Plugin - What To Do When Task is Completed

## Mandatory Steps

### 1. Run All Tests
```bash
./gradlew test --continue
```
- Ensure all tests pass
- Fix any failures before proceeding
- **Critical**: Maintain 100% test pass rate

### 2. Verify Plugin Compatibility
```bash
./gradlew verifyPlugin
```
- Checks compatibility with IntelliJ IDEA 2025.3
- Ensures plugin descriptor is valid
- Must pass before committing

### 3. Generate and Review Coverage Report
```bash
./gradlew test koverHtmlReport
```
- Generate HTML coverage report
- Review coverage for affected components
- Target: Maintain 72%+ overall coverage
- View report: `build/reports/kover/html/index.html`

### 4. Run Comprehensive Check
```bash
./gradlew check
```
- Runs linting, tests, and verification
- Final quality gate
- Must pass before committing

## Code Quality Checks

### Linting
The project uses Kotlin compiler for linting:
- Ensure no compilation warnings
- Fix any deprecation warnings
- All code must compile cleanly

### Type Safety
- Verify all null safety checks
- Remove unnecessary `!!` assertions
- Use explicit types for public APIs

### Error Handling
- Ensure all exceptions are caught appropriately
- Use `LOG.warn()` instead of `LOG.error()` for recoverable errors
- Verify no TestLoggerAssertionError in tests

## Documentation

### Update AGENTS.md (If Needed)
- Update code examples if patterns changed
- Document new testing patterns
- Update coverage statistics

### Update KDoc (Public APIs)
- Document new public methods/classes
- Update parameter descriptions
- Add usage examples if helpful

### Update README.md (If Needed)
- Update feature list if new features added
- Update keyboard shortcuts if actions changed
- Update installation instructions if process changed

## Testing Specifics

### ViewModel Changes
- Add comprehensive unit tests for new methods
- Test state transitions
- Test callback invocations
- Target: 90%+ coverage for ViewModels

### Service Changes
- Test success and error paths
- Test concurrent operations if applicable
- Test network failures
- Target: 70%+ coverage for services

### Action Changes
- Test action enablement logic
- Test action performance
- Test error handling
- Target: 70%+ coverage for actions

### Model Changes
- Test serialization/deserialization
- Test edge cases (null, empty values)
- Test equals/hashCode
- Target: 85%+ coverage for models

## Platform Tests (If Implemented)

### Run Platform Tests
```bash
./gradlew test --tests "*PlatformTest"
```
- Requires Xvfb on headless systems
- Tests UI-dependent functionality

### Flaky Test Handling
- Use `@RepeatedIfExceptionsTest(repeats = 5)` for platform tests
- Do not use for unit tests
- Platform tests may need retries due to timing

## Before Committing

### Review Changes
```bash
git status
git diff
```
- Review all changes
- Ensure no unintended modifications
- Remove debug code

### Check for TODO/FIXME
```bash
grep -r "TODO\|FIXME" src/
```
- Remove or address TODO comments
- Document intentional TODOs in GitHub issues

### Verify No Secrets
```bash
# Check for common secret patterns
grep -r "password\|secret\|api_key\|token" src/ | grep -v "//"
```
- Never commit secrets or API keys
- Use environment variables or settings for sensitive data

### Check Dependencies
```bash
./gradlew dependencies
```
- Review dependency tree for conflicts
- Ensure coroutines use `compileOnly` configuration
- No version conflicts expected

## Build Verification

### Final Build
```bash
./gradlew clean build
```
- Must succeed without errors
- All tests must pass
- No warnings

### Build Plugin Distribution
```bash
./gradlew buildPlugin
```
- Verify plugin ZIP is created
- Output: `build/distributions/opencode-jb-1.0.0.zip`
- Test manual installation if needed

## Optional Steps

### Performance Testing
- Run tests multiple times to check for flakiness
- Profile slow operations if any
- Ensure no memory leaks

### Integration Testing
- Test plugin in actual IntelliJ IDEA instance
- Run `./gradlew runIde` for interactive testing
- Verify UI components work as expected

### Documentation Review
- Ensure code comments are clear
- Verify KDoc is accurate
- Check for outdated documentation

## Common Pitfalls to Avoid

### ❌ Don't Skip Tests
- Always run full test suite
- Fix failures before committing
- Flaky tests should be fixed, not disabled

### ❌ Don't Commit With Compilation Errors
- Code must compile cleanly
- No warnings
- All dependencies must resolve

### ❌ Don't Lower Coverage
- Maintain or improve coverage
- Add tests for new code
- Remove dead code if coverage drops

### ❌ Don't Use LOG.error() in Testable Code
- Use LOG.warn() for recoverable errors
- LOG.error() will cause TestLoggerAssertionError
- Reserve LOG.error() for truly unrecoverable situations

### ❌ Don't Force Coroutines Version
- Use `compileOnly` for coroutines dependencies
- Let IntelliJ Platform provide runtime version
- Prevents version conflicts

### ❌ Don't Add Star Imports
- Always use explicit imports
- Remove unused imports
- Keep import order consistent

## Verification Checklist

Before marking task complete, verify:

- [ ] All tests pass (`./gradlew test --continue`)
- [ ] Plugin verification passes (`./gradlew verifyPlugin`)
- [ ] Coverage report generated and reviewed
- [ ] No compilation warnings
- [ ] No TestLoggerAssertionError
- [ ] All KDoc added for public APIs
- [ ] Git status shows only intended changes
- [ ] Dependencies are correct (coroutines use `compileOnly`)
- [ ] Code follows project conventions
- [ ] Comprehensive check passes (`./gradlew check`)
- [ ] Build succeeds (`./gradlew clean build`)

## When to Ask for Review

- Complex architecture changes
- Breaking changes to public APIs
- Significant performance implications
- New dependencies added
- Testing patterns changed
- Coverage significantly decreased

## Success Criteria

Task is complete when:

1. ✅ All 1,024+ tests pass
2. ✅ Plugin verification succeeds
3. ✅ Coverage maintained at 72%+ (no significant decrease)
4. ✅ Code compiles cleanly without warnings
5. ✅ All public APIs documented
6. ✅ Follows project code style and conventions
7. ✅ Comprehensive check passes
8. ✅ Ready for code review
