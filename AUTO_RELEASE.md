# Auto-Release Documentation for OpenCode IntelliJ Plugin

This document explains how the automated release system works and how to create releases.

## Overview

The project now has a comprehensive auto-release system that automatically creates GitHub releases when you push git tags following semantic versioning patterns.

## Release Workflow Files

### 1. `.github/workflows/build.yml`
- **Purpose**: Main build pipeline for CI/CD
- **Triggers**: Push to main, pull requests, release events, manual dispatch
- **Jobs**: Build, Test, Verify
- **Output**: Plugin artifacts uploaded as GitHub Actions artifacts

### 2. `.github/workflows/release.yml` 
- **Purpose**: Automated release creation pipeline
- **Triggers**: Git tags (v*.*.*, v*.*), manual dispatch
- **Features**:
  - Automatic plugin building from tag
  - Dynamic changelog generation
  - GitHub release creation with plugin artifacts
  - Pre-release detection for alpha/beta/rc versions

## How to Create a Release

### Method 1: Using Git Tags (Recommended)

1. **Update version in `gradle.properties`**:
   ```bash
   # Update the version property
   pluginVersion = 1.0.1
   ```

2. **Commit your changes**:
   ```bash
   git add gradle.properties
   git commit -m "Bump version to 1.0.1"
   ```

3. **Create and push a tag**:
   ```bash
   # Create release tag
   git tag v1.0.1
   
   # Push both commit and tag
   git push origin main
   git push --tags origin
   ```

### Method 2: Pre-releases (Alpha/Beta/RC)

For pre-release versions, use additional suffixes:

```bash
# Alpha release
git tag v1.0.1-alpha

# Beta release  
git tag v1.0.1-beta

# Release candidate
git tag v1.0.1-rc1

# Push the tag
git push --tags origin
```

### Method 3: Manual Trigger

You can also trigger releases manually:

1. Go to **Actions** tab in your GitHub repository
2. Select **Release** workflow from the sidebar
3. Click **Run workflow**
4. Enter the desired version/tag name (e.g., `v1.0.1`)
5. Click **Run workflow**

## Release Process Flow

```
1. Git Tag Push → 2. Release Workflow Trigger → 3. Plugin Build → 
4. Changelog Generation → 5. GitHub Release Creation
```

### Step-by-Step Breakdown:

1. **Tag Detection**: The release workflow detects git tags starting with `v`
2. **Version Extraction**: Extracts version from tag (removes `v` prefix)
3. **Plugin Build**: Builds the plugin using Gradle
4. **Changelog Generation**: 
   - Finds previous tag automatically
   - Generates changelog from commit messages since last release
5. **Release Creation**:
   - Creates GitHub release with generated name and description
   - Uploads built plugin artifacts
   - Marks as pre-release if version contains alpha/beta/rc

## Versioning Guidelines

### Semantic Versioning Format: `MAJOR.MINOR.PATCH`

- **Major (X.0.0)**: Incompatible changes, major new features
- **Minor (X.Y.0)**: Backward-compatible new features  
- **Patch (X.Y.Z)**: Backward-compatible bug fixes

### Pre-release Formats:
- `v1.0.0-alpha` - Alpha version
- `v1.0.0-beta` - Beta version
- `v1.0.0-rc1` - Release candidate 1
- `v1.0.0-snapshot` - Development snapshot

## What Gets Released

Each release includes:
- **Plugin ZIP file** (`OpenCode-vX.Y.Z.zip`)
- **Release notes/changelog**
- **Automatic pre-release detection**

## Security and Permissions

The workflows require these permissions:
- `contents: write` - To create releases and upload assets
- `packages: write` - For potential future package registry integration

## Troubleshooting

### Common Issues:

1. **Release not triggering**:
   - Ensure tag follows format (v*.*.*, v*.*)
   - Check that you're pushing the tag (`git push --tags`)
   
2. **Plugin build fails in release workflow**:
   - Verify `gradle.properties` has correct version
   - Test local build: `./gradlew buildPlugin`

3. **Changelog empty**:
   - This happens for first releases (no previous tag)
   - Subsequent releases will show commit history

4. **Pre-release detection not working**:
   - Ensure your tag contains `-`, `rc`, `alpha`, or `beta`
   - Example: `v1.0.0-alpha` vs `v1.0.0`

### Manual Testing:

To test the workflow locally without pushing:
```bash
# Create a test tag (detached HEAD)
git checkout --detach
git tag v-test-$(date +%s)

# You can inspect what would be released
echo "Version: $(echo v-test-$(date +%s) | cut -c2-)"
```

## Best Practices

1. **Always test locally first**:
   ```bash
   ./gradlew buildPlugin
   ```

2. **Keep commit messages clear and descriptive** for better changelogs

3. **Use pre-release tags** for testing before final release

4. **Update version in gradle.properties BEFORE creating tag**

5. **Review the generated release** before publishing (first few times)

## Integration with Existing CI/CD

The build workflow now also supports:
- **Release events**: Triggers when a GitHub release is published
- **Manual dispatch**: Can be run manually for testing
- **Artifact handling**: Different upload logic for vs. non-release builds

This ensures compatibility while adding the new auto-release functionality.