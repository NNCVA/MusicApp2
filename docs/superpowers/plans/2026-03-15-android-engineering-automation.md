# Android Engineering Automation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android project reproducible across Windows and Linux by shipping a complete Gradle Wrapper and a CI pipeline that runs lint, unit tests, and debug APK assembly with downloadable reports.

**Architecture:** Keep product code behavior unchanged and focus only on repository tooling. Treat the build pipeline as the deliverable: first restore repository-owned Gradle execution, then normalize ignore rules, then harden the GitHub Actions workflow, and finally run local verification to capture evidence.

**Tech Stack:** Gradle 8.2 wrapper, Android Gradle Plugin 8.2.0, Kotlin DSL, GitHub Actions, JDK 17

---

## File Map

- Modify: `.gitignore`
- Modify: `.github/workflows/ci.yml`
- Create or replace: `gradlew.bat`
- Create or replace: `gradle/wrapper/gradle-wrapper.jar`
- Verify: `gradlew`
- Verify: `gradle/wrapper/gradle-wrapper.properties`
- Modify only if required by verification: `app/build.gradle.kts`
- Modify only if required by verification: `build.gradle.kts`

## Chunk 1: Restore Gradle Wrapper Integrity

### Task 1: Capture the current wrapper baseline

**Files:**
- Verify: `gradlew`
- Verify: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Inspect the existing wrapper files**

Run:

```powershell
Get-Item .\gradlew
Get-Content .\gradle\wrapper\gradle-wrapper.properties
```

Expected:

- `gradlew` exists
- `distributionUrl` points to Gradle `8.2`
- missing Windows or JAR components are confirmed

- [ ] **Step 2: Record the wrapper gap in working notes**

Capture:

- missing `gradlew.bat`
- missing `gradle-wrapper.jar`

- [ ] **Step 3: Commit only if the baseline inspection changes files**

```bash
# No commit expected for read-only verification
```

### Task 2: Recreate and track the complete wrapper

**Files:**
- Create or replace: `gradlew.bat`
- Create or replace: `gradle/wrapper/gradle-wrapper.jar`
- Verify: `gradlew`
- Verify: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Generate or restore the missing wrapper files**

Preferred command:

```powershell
gradle wrapper --gradle-version 8.2
```

Fallback if no system Gradle exists:

- obtain `gradlew.bat` and `gradle-wrapper.jar` from a clean Gradle `8.2` wrapper generation in an isolated temp project

- [ ] **Step 2: Verify all wrapper files are present**

Run:

```powershell
Get-ChildItem .\gradle\wrapper
Get-Item .\gradlew, .\gradlew.bat
```

Expected:

- `gradlew`
- `gradlew.bat`
- `gradle-wrapper.properties`
- `gradle-wrapper.jar`

- [ ] **Step 3: Run wrapper version checks on Windows entrypoint**

Run:

```powershell
.\gradlew.bat --version
```

Expected:

- Gradle prints version information without requiring a globally installed Gradle

- [ ] **Step 4: Commit the wrapper restoration**

```bash
git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties
git commit -m "build: restore complete gradle wrapper"
```

## Chunk 2: Normalize Repository Tracking Rules

### Task 3: Fix `.gitignore` wrapper and build output rules

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Write a focused ignore policy**

Keep:

- build outputs ignored
- local caches ignored
- wrapper scripts and wrapper JAR explicitly tracked

Remove or simplify:

- contradictory `gradlew` / `gradlew.bat` ignore and unignore combinations
- duplicate build directory rules where possible

- [ ] **Step 2: Verify git now tracks wrapper files correctly**

Run:

```powershell
git check-ignore -v gradlew
git check-ignore -v gradlew.bat
git check-ignore -v gradle/wrapper/gradle-wrapper.jar
```

Expected:

- wrapper files are not ignored

- [ ] **Step 3: Verify generated outputs remain ignored**

Run:

```powershell
git check-ignore -v app/build/outputs/apk/debug/app-debug.apk
git check-ignore -v app/build/reports/tests
```

Expected:

- build outputs and reports are still ignored by git

- [ ] **Step 4: Commit the ignore cleanup**

```bash
git add .gitignore
git commit -m "build: normalize gitignore for wrapper and outputs"
```

## Chunk 3: Harden GitHub Actions Verification

### Task 4: Redesign the CI job flow

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Update the workflow steps**

Required sequence:

1. checkout
2. setup Java 17 with Gradle cache
3. make `gradlew` executable on Linux
4. run `./gradlew lint --no-daemon`
5. run `./gradlew testDebugUnitTest --no-daemon`
6. run `./gradlew assembleDebug --no-daemon`
7. upload APK artifact
8. upload lint and test reports, ideally under `if: always()`

- [ ] **Step 2: Use artifact uploads for both success and failure diagnostics**

Include paths such as:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/reports/lint-results-debug.html
app/build/reports/tests/
app/build/test-results/
```

- [ ] **Step 3: Validate workflow syntax and intent locally**

Run:

```powershell
Get-Content .github/workflows/ci.yml
```

Manual verification checklist:

- correct YAML indentation
- artifact upload step uses `if: always()` for reports
- APK upload runs only when the file should exist or tolerates absence cleanly

- [ ] **Step 4: Commit the workflow upgrade**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add lint test and artifact retention"
```

## Chunk 4: Minimal Build Compatibility Fixes

### Task 5: Make the Gradle build compatible with CI if verification exposes issues

**Files:**
- Modify only if required: `app/build.gradle.kts`
- Modify only if required: `build.gradle.kts`

- [ ] **Step 1: Run the first failing verification command**

Run:

```powershell
.\gradlew.bat lint --no-daemon
```

Expected:

- either success, or a concrete build error that points to configuration incompatibility

- [ ] **Step 2: Apply the smallest possible configuration fix**

Examples only if needed:

- fix malformed version catalog entry usage
- remove invalid dependency declaration formatting
- adjust plugin or repository configuration

Do not:

- refactor unrelated production code
- introduce new tooling unrelated to this CI scope

- [ ] **Step 3: Re-run the exact failing command**

Run:

```powershell
.\gradlew.bat lint --no-daemon
```

Expected:

- the previous configuration error is gone

- [ ] **Step 4: Commit only the minimal compatibility fix**

```bash
git add app/build.gradle.kts build.gradle.kts gradle/libs.versions.toml
git commit -m "build: fix gradle configuration for CI verification"
```

## Chunk 5: End-to-End Verification

### Task 6: Run local verification with repository-owned tooling

**Files:**
- Verify: `.github/workflows/ci.yml`
- Verify: `app/build.gradle.kts`
- Verify generated outputs under `app/build/`

- [ ] **Step 1: Run lint**

Run:

```powershell
.\gradlew.bat lint --no-daemon
```

Expected:

- lint task completes
- reports are written under `app/build/reports`

- [ ] **Step 2: Run unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

Expected:

- unit tests pass
- XML or HTML test reports are generated

- [ ] **Step 3: Build the debug APK**

Run:

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

Expected:

- `app/build/outputs/apk/debug/app-debug.apk` exists

- [ ] **Step 4: Inspect the evidence directories**

Run:

```powershell
Get-ChildItem app/build/outputs/apk/debug
Get-ChildItem app/build/reports -Recurse
Get-ChildItem app/build/test-results -Recurse
```

Expected:

- APK and reports are present for CI upload

- [ ] **Step 5: Commit any final verification-safe adjustments**

```bash
git add .github/workflows/ci.yml .gitignore gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties app/build.gradle.kts build.gradle.kts
git commit -m "build: finalize android automation pipeline"
```

## Execution Notes

- Preserve unrelated user changes already present in the working tree.
- Stage only files directly related to wrapper restoration, ignore cleanup, CI updates, and minimal build compatibility fixes.
- If lint surfaces pre-existing product issues, document them and decide whether they should be fixed now or temporarily scoped out of CI.
- Do not add coverage tooling in this phase unless explicitly requested later.
