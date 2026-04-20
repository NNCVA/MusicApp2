# Android Engineering Automation Design

**Date:** 2026-03-15

**Project:** `MusicApp2`

## Summary

This design defines the first resume-oriented engineering upgrade for the Android music player project: make the build and CI pipeline reproducible, cross-platform, and evidence-friendly without changing product behavior.

The work focuses on shipping a complete Gradle Wrapper, cleaning repository ignore rules that interfere with wrapper tracking, and upgrading GitHub Actions so the project can run lint, unit tests, and debug APK assembly with retained artifacts and reports.

## Problem

The project already contains meaningful Android product work, but its engineering evidence is weak because the build pipeline is not fully reproducible.

Current issues:

- The repository includes `gradlew` and `gradle-wrapper.properties` but does not include a complete Gradle Wrapper set for all environments.
- The current CI workflow only attempts build and test tasks and does not preserve enough diagnostics.
- The repository ignore rules around wrapper files are inconsistent, which makes future wrapper drift more likely.
- The project cannot yet claim a strong automated quality workflow because lint and report retention are missing.

## Goals

- Make the repository self-contained for Gradle execution on Windows and Linux.
- Ensure CI performs static analysis, unit testing, and debug build verification.
- Preserve build outputs and diagnostics so failures are traceable.
- Keep the scope narrow enough to complete without touching product features.

## Non-Goals

- No feature development.
- No architecture refactors in playback, scanning, or UI code.
- No coverage gate or strict quality threshold in this phase.
- No large-scale test expansion beyond what is required to keep CI healthy.

## Proposed Approach

### 1. Ship a complete Gradle Wrapper

Add the missing wrapper files needed for cross-platform execution:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- existing `gradle/wrapper/gradle-wrapper.properties`

This allows local and CI execution to depend on repository-owned tooling rather than machine state.

### 2. Normalize repository ignore rules

Update `.gitignore` so it clearly:

- ignores generated build outputs and local caches
- allows wrapper scripts and wrapper JAR to be committed
- avoids contradictory rules that make wrapper tracking fragile

### 3. Upgrade CI to a standard verification flow

Use a single GitHub Actions workflow job that:

1. checks out the repository
2. sets up JDK 17
3. prepares wrapper execution
4. runs Android lint
5. runs unit tests
6. assembles the debug APK
7. uploads APK and HTML/XML reports

Artifact retention should include:

- debug APK
- lint reports
- unit test reports
- raw test result XML when available

The workflow should prefer predictable sequencing over multiple parallel jobs because the current project is small and the first goal is reliability, not CI throughput.

## Files Expected To Change

### Repository / build tooling

- `.gitignore`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties` if regeneration updates metadata

### CI

- `.github/workflows/ci.yml`

### Gradle build files

- `app/build.gradle.kts` only if lint or test task compatibility requires a minimal fix
- root `build.gradle.kts` only if required for CI stability

## Verification Plan

Local verification should use repository-owned commands:

- `./gradlew lint`
- `./gradlew test` or `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

On Windows, equivalent wrapper invocation should also be supported through `gradlew.bat`.

Expected evidence after implementation:

- wrapper commands execute without relying on a globally installed Gradle
- lint output is generated under `app/build/reports`
- unit test output is generated under `app/build/test-results` and/or `app/build/reports/tests`
- debug APK is generated under `app/build/outputs`

## Resume Value

This upgrade creates defensible engineering evidence in a weak area of the project:

- reproducible Android build setup
- CI-backed verification instead of local-only claims
- retained diagnostics and APK artifacts for traceability

Safe future resume phrasing after successful verification:

- Built a reproducible Android CI pipeline with Gradle Wrapper, static analysis, unit tests, and debug APK artifact retention.
- Improved project delivery reliability by standardizing cross-platform Gradle execution and preserving build diagnostics for failure analysis.

## Risks And Mitigations

- Wrapper regeneration may update tracked files unexpectedly.
  - Mitigation: limit commits to wrapper and CI files only.

- Android lint may surface pre-existing project issues.
  - Mitigation: treat lint failures as part of the engineering baseline and make only the smallest required compatibility fixes.

- Existing uncommitted user changes may coexist with this work.
  - Mitigation: avoid reverting unrelated files and stage only files related to this upgrade.

## Acceptance Criteria

- The repository contains a complete Gradle Wrapper for Windows and Linux execution.
- `.gitignore` clearly permits committed wrapper files while ignoring generated outputs.
- GitHub Actions runs lint, unit tests, and debug APK assembly.
- CI uploads APK and verification reports as artifacts.
- Local verification succeeds using repository-owned wrapper commands.
