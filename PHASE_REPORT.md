# Phase 0 Report — Project Skeleton

## 1. What Was Done
- **Gradle & Android Project Setup:**
  - Configured Gradle project with Kotlin 2.0, Compose Compiler plugin, and AGP 8.5.2.
  - Set `targetSdkVersion = 28` (allows direct binary execution in app data dir), `minSdkVersion = 26`, and `compileSdk = 35`.
  - Configured ABI filter for `arm64-v8a`.
  - Set up Gradle wrapper with Gradle 8.9 (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`).
- **Package Structure Created:**
  - `com.flide.env`: `StorageLayout`, `EnvironmentConfig`, `EnvironmentState`.
  - `com.flide.flutter`: `FlutterConfig`, `FlutterState`.
  - `com.flide.editor`: `EditorConfig`, `EditorState`.
  - `com.flide.terminal`: `TerminalConfig`, `TerminalState`.
  - `com.flide.github`: `GitHubConfig`, `GitHubAuthState`.
  - `com.flide.service`: `IdeService` (foreground service stub).
  - `com.flide.data.settings`: `SettingsRepository` (DataStore Preferences).
  - `com.flide.ui`: `MainActivity`, `HomeScreen`, Material 3 theme (`Theme.kt`, `Color.kt`, `Type.kt`).
- **Foreground Service Stub (`IdeService`):**
  - Persistent notification channel (`flide_service_channel`) with low priority.
  - Partial wake lock (`PowerManager.PARTIAL_WAKE_LOCK`) acquire and release lifecycle.
  - Intent actions for `START`, `STOP`, and `UPDATE_STATUS`.
- **Settings Storage:**
  - AndroidX DataStore Preferences implementation with reactive `Flow<IdeSettings>` for auto-save, font size, theme, Flutter channel, and environment initialization state.
- **Storage Layout Implementation:**
  - Implemented `StorageLayout` covering `rootfs/`, `downloads/`, `projects/`, `logs/`.
  - Path mapping between Android filesystem and rootfs `/home/flide/projects`.
  - Storage space calculation (`usableSpace`, `totalSpace`).
- **Jetpack Compose UI:**
  - Initial `HomeScreen` displaying environment readiness, device storage readout, and project placeholder.
- **Unit Testing:**
  - Added `StorageLayoutTest` for directory structure, rootfs path calculation, and relative path mapping.
- **GitHub Actions CI Workflow:**
  - Created `.github/workflows/build.yml` with Temurin JDK 17, Gradle caching, unit test execution, debug APK assembly, and artifact upload (`flide-debug-apk`).
- **Documentation & Licenses:**
  - Created comprehensive `README.md` with architecture diagram, storage layout, bootstrap steps, and third-party license notices:
    - **Sora Editor**: LGPL-2.1
    - **Termux Terminal View/Emulator**: GPL-3.0 / Apache-2.0
    - **PRoot**: GPL-2.0

## 2. What Was Tested / Verified
- Project file tree, Gradle configuration, and manifests verified.
- Git repository initialized on branch `main` with all Phase 0 files staged.
- Upstream licenses verified via official GitHub repositories.

## 3. Known Issues / Considerations
- As instructed, local compilation was avoided to preserve user device resources; build verification will run in GitHub Actions CI.

## 4. Exact Next Steps
1. Create the remote repository on GitHub using `gh repo create` and push `main` branch.
2. Confirm the GitHub Actions workflow builds the debug APK artifact successfully.
3. Download/install debug APK on device to verify the home screen opens cleanly.
4. Obtain user confirmation before starting **Phase 1 (Linux environment: proot + Debian rootfs)**.
