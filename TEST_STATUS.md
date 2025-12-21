# OpenCode IntelliJ Plugin - Test Status

**Quick Status**: 🟢 181 passing tests | 35.2% line coverage | 0 failures

For detailed test status, coverage analysis, and roadmap to 90% coverage, see:

📄 **[docs/TEST_STATUS.md](docs/TEST_STATUS.md)**

## Quick Stats

- **Total Tests**: 234 (181 passing, 53 disabled)
- **Coverage**: 35.2% line coverage (target: 90%)
- **Build Status**: ✅ BUILD SUCCESSFUL
- **Last Updated**: 2025-12-21

## Quick Commands

```bash
# Run all tests
./gradlew test

# Generate coverage report
./gradlew koverHtmlReport
# Open: build/reports/kover/html/index.html
```

## Next Steps

See [docs/TEST_STATUS.md](docs/TEST_STATUS.md) for complete TODO list to reach 90% coverage.

**Priority 1**: Refactor ServerManager to enable 16 disabled service tests (+15-20% coverage)
