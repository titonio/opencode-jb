#!/bin/bash

# Auto-release script for OpenCode IntelliJ Plugin
#
# This script automatically:
# 1. Determines the next semantic version (patch increment)
# 2. Updates gradle.properties with the new version
# 3. Commits the version change
# 4. Creates and pushes a git tag
#
# Usage: ./release.sh [--dry-run]
#
# Examples:
#   ./release.sh                    # Bump patch version (e.g., 1.0.0 -> 1.0.1)
#   ./release.sh --dry-run          # Show what would be done without executing

set -euo pipefail

# Configuration
GRADLE_PROPERTIES="gradle.properties"
PLUGIN_VERSION_PREFIX="pluginVersion = "
DRY_RUN=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Parse command line arguments
if [[ $# -gt 0 ]]; then
    if [[ "$1" == "--dry-run" ]]; then
        DRY_RUN=true
        log_info "DRY RUN MODE - No changes will be made"
    else
        log_error "Unknown argument: $1"
        echo "Usage: $0 [--dry-run]"
        exit 1
    fi
fi

# Check if we're in a git repository
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
    log_error "Not inside a git repository"
    exit 1
fi

# Check if gradle.properties exists
if [[ ! -f "$GRADLE_PROPERTIES" ]]; then
    log_error "$GRADLE_PROPERTIES not found"
    exit 1
fi

# Get current version from gradle.properties
get_current_version() {
    local line=$(grep "^$PLUGIN_VERSION_PREFIX" "$GRADLE_PROPERTIES")
    if [[ -z "$line" ]]; then
        log_error "Could not find plugin version in $GRADLE_PROPERTIES"
        exit 1
    fi
    
    # Extract version number (remove prefix and quotes)
    local version=$(echo "$line" | sed -E "s/^$PLUGIN_VERSION_PREFIX[\"']?([^'\"]+)[\"']?.*/\1/")
    
    if [[ -z "$version" ]]; then
        log_error "Could not parse version from: $line"
        exit 1
    fi
    
    echo "$version"
}

# Calculate next patch version
get_next_patch_version() {
    local current="$1"
    
    # Validate current version format (semantic versioning)
    if ! [[ "$current" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-[a-zA-Z0-9]+)?$ ]]; then
        log_error "Invalid semantic version format: $current"
        log_error "Expected format: X.Y.Z or X.Y.Z-suffix"
        exit 1
    fi
    
    local major="${BASH_REMATCH[1]}"
    local minor="${BASH_REMATCH[2]}"
    local patch="${BASH_REMATCH[3]}"
    
    # Increment patch version
    ((patch++))
    
    echo "${major}.${minor}.${patch}"
}

# Update gradle.properties with new version
update_gradle_properties() {
    local new_version="$1"
    
    log_info "Updating $GRADLE_PROPERTIES to version: $new_version"
    
    if [[ "$DRY_RUN" == true ]]; then
        return 0
    fi
    
    # Use sed to replace the version line
    sed -i.bak "s/^$PLUGIN_VERSION_PREFIX.*/$PLUGIN_VERSION_PREFIX\"$new_version\"/" "$GRADLE_PROPERTIES"
    rm -f "${GRADLE_PROPERTIES}.bak"  # Remove backup file
}

# Commit version change
commit_version_change() {
    local new_version="$1"
    
    log_info "Committing version bump to $new_version"
    
    if [[ "$DRY_RUN" == true ]]; then
        return 0
    fi
    
    git add "$GRADLE_PROPERTIES"
    git commit -m "Bump version to $new_version"
}

# Create and push tag
create_and_push_tag() {
    local new_version="$1"
    
    log_info "Creating and pushing tag: v$new_version"
    
    if [[ "$DRY_RUN" == true ]]; then
        return 0
    fi
    
    git tag "v$new_version"
    git push --tags origin
}

# Main script execution
main() {
    log_info "Starting OpenCode IntelliJ Plugin release process"
    
    # Get current version
    local current_version
    current_version=$(get_current_version)
    log_success "Current version: $current_version"
    
    # Calculate next version
    local new_version
    new_version=$(get_next_patch_version "$current_version")
    log_success "Next patch version: $new_version"
    
    # Show what will be done
    echo ""
    log_info "Release plan:"
    echo "  - Update gradle.properties to version: $new_version"
    echo "  - Commit change with message: 'Bump version to $new_version'"
    echo "  - Create tag: v$new_version"
    echo "  - Push tag to origin"
    
    if [[ "$DRY_RUN" == true ]]; then
        log_info "Dry run complete. No changes were made."
        exit 0
    fi
    
    # Ask for confirmation
    echo ""
    read -p "Proceed with release? (y/N): " -n 1 -r
    echo ""
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Release cancelled"
        exit 0
    fi
    
    # Execute release steps
    update_gradle_properties "$new_version"
    commit_version_change "$new_version"
    create_and_push_tag "$new_version"
    
    echo ""
    log_success "✅ Release completed successfully!"
    log_success "📦 Version $new_version has been released"
    log_info "GitHub Actions will automatically build and create the release"
}

# Run main function
main "$@"