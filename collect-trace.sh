#!/usr/bin/env bash
set -e

# Usage: ./collect-trace.sh <project-dir> <task-name> <trace-file-prefix>

PROJECT="${1?Project directory not provided}"
TASK="${2?Task not provided}"
TRACE_PREFIX="${3:-trace}"

GRADLE_CMD="${GRADLE_CMD:-./gradlew}"

# Function to print a command before running
# shellcheck disable=SC2145
exe() { echo ""; echo "\$ $@" ; "$@" ; }

cd "$PROJECT"
exe pwd

STORAGE_DIR="$PWD/_trace"
EMPTY_GRADLE_HOME="${EMPTY_GRADLE_HOME:-fresh-gradle-home}"

GRADLE_CMD="$GRADLE_CMD -g $EMPTY_GRADLE_HOME"

# Make sure no local artifact transform results are present
# shellcheck disable=SC2086
exe $GRADLE_CMD --console=plain clean

# Kill all Gradle daemons to make sure nothing is cached in memory
WRAPPER_VERSION="$($GRADLE_CMD --version | grep 'Gradle ' | awk '{print $2}')"
exe pkill -f "GradleDaemon $WRAPPER_VERSION" || true

# Clean the temporary Gradle home to make sure artifact transform results are not cached on disk
exe rm -rf "./$EMPTY_GRADLE_HOME/daemon"
# Remove everything from caches except `modules-2` which contains downloaded dependencies
exe find "./$EMPTY_GRADLE_HOME/caches" -mindepth 1 -maxdepth 1 ! -name 'modules-2' -print -exec rm -rf {} + || true

# Clean the storage dir
exe rm -rf "$STORAGE_DIR"

# Run task and collect build operations and build scan dump
# shellcheck disable=SC2086
exe $GRADLE_CMD --console=plain --no-build-cache "$TASK" \
  -Dorg.gradle.internal.operations.trace="$STORAGE_DIR/$TRACE_PREFIX" \
  --scan -Dscan.dump

# Find and move scan dump
# https://unix.stackexchange.com/a/305846
find . -name "*.scan" -maxdepth 1 -exec mv {} "$STORAGE_DIR" \;
# Change build scan name to a simple name
mv "$STORAGE_DIR"/*.scan "$STORAGE_DIR"/trace.scan

echo
echo "Collected trace files:"
find "$STORAGE_DIR"/*

cd - >/dev/null
