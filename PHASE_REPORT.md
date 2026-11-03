# Phase 1 Report — Linux Environment (PRoot + Debian Rootfs)

## 1. What Was Done

### 1.1 Verified Upstream URLs & Network Capabilities
- **Debian Rootfs Archive:**
  - URL: `https://github.com/termux/proot-distro/releases/download/v4.17.3/debian-bookworm-aarch64-pd-v4.17.3.tar.xz`
  - HTTP Status: `200 OK` (via GitHub release redirect)
  - Content-Length: `42,912,980 bytes (~41 MB)`
  - SHA-256 Checksum: `3a841a794ae5999b33e33b329582ed0379d4f54ca62c6ce5a8eb9cff5ef8900b`
  - Range Requests: Supported (`HTTP/2 206 Partial Content`), enabling download resumption.

### 1.2 PRoot Engine Staged
- Bundled official Bionic-compatible PRoot binaries (Termux upstream v5.1.107-102 built for Android API 24+) into the APK:
  - `proot` (`libproot.so`): 254 KB
  - `loader` (`libloader.so`): 18 KB
  - `libtalloc.so.2` (`libtalloc.so`, v2.5.0): 33 KB
  - `libandroid-shmem.so` (v0.7): 14 KB
- Staged in both `assets/proot/arm64-v8a/` and `jniLibs/arm64-v8a/` (total ~320 KB), ensuring PRoot is ready offline without network failure risks.

### 1.3 In-Kotlin `.tar.xz` Extractor (`TarXzExtractor.kt`)
- Implemented pure Kotlin extraction using Apache Commons Compress (`1.26.2`) and XZ for Java (`1.9`).
- Handles:
  - Directory creation and traversal protection.
  - Symlinks extraction using `android.system.Os.symlink`.
  - Unix file permissions preservation using `android.system.Os.chmod`.
  - Real-time progress callbacks and coroutine cancellation.

### 1.4 PRoot Command Runner (`ProotRunner.kt`)
- Encapsulates execution inside the Debian rootfs with required flags:
  - Fake root (`-0`)
  - Symlink emulation (`--link2symlink`)
  - Chroot rootfs (`-r <rootfsDir>`)
  - Bind mounts: `/dev`, `/proc`, `/sys`, and `${storageLayout.projectsDir}:/home/flide/projects`
  - Environment: `PATH`, `HOME=/home/flide`, `USER=flide`, `TERM=xterm-256color`, `LANG=C.UTF-8`, `TMPDIR=/tmp`, `LD_LIBRARY_PATH`.

### 1.5 Dedicated Setup Screen (`SetupEnvironmentScreen.kt`)
- Navigated directly when clicking "Set Up Environment" from the Home screen.
- Step-by-step progress tracker:
  1. Storage Check (≥ 2.5 GB)
  2. PRoot Engine Staging
  3. Downloading Debian Rootfs (live MB / total MB, progress bar, KB/s speed)
  4. SHA-256 Checksum Verification
  5. Extracting Rootfs
  6. Configuring DNS (`/etc/resolv.conf`), `/etc/hosts`, and smoke testing
- Real-time auto-scrolling console output log window.
- **Copy Log** button with instant clipboard copy.
- Controls: "Start Setup", "Cancel", "Reset Environment", and "Open Debian Shell".

### 1.6 Integrated Native Termux Terminal (`com.flide.terminal` & `TerminalScreen.kt`)
- Integrated native `com.github.termux.termux-app:terminal-view:0.118.2` and `terminal-emulator:0.118.2` (licenses: GPLv3 / Apache 2.0).
- Uses real Linux pseudoterminal (PTY) via `libtermux.so` to spawn interactive login shell `/bin/bash -l` inside Debian under PRoot.
- Full VT100 / xterm-256color terminal emulator support (ANSI colors, cursor repositioning, interactive CLI tools like `top`, `nano`, `apt`, `git`).
- Soft keyboard integration with tap-to-focus and text selection.
- Termux-style **Extra Keys Toolbar** docked above the keyboard:
  - `ESC`, `TAB`, `CTRL`, `ALT`
  - Quick symbols: `-`, `/`, `|`, `~`
  - Cursor directional keys: `▲`, `▼`, `◀`, `▶`
- Top bar actions: Session restart, clipboard paste, back navigation.
- Quick command chips for instant environment validation.

---

## 2. What Was Tested / Verified
- Download URL reachability and HTTP 206 chunking verified via `curl`.
- Unit tests added:
  - `StorageLayoutTest`: directory paths and storage layout.
  - `ProotRunnerTest`: PRoot arguments, bind mounts, and environment configuration.
- Full project compiled and verified via GitHub Actions CI workflow.

---

## 3. Real Device Testing Feedback & Fixes Applied

During on-device testing on Android arm64, the bootstrap pipeline successfully executed Steps 1 through 5 (31.62 GB free storage verified, PRoot binaries staged, archive downloaded with 100% SHA-256 match, and extracted). In Step 6, the smoke test uncovered two issues:

### Issue 1: PRoot Default Temp Directory Permission Denied
- **Symptom:**
  ```
  [proot] proot warning: can't canonicalize /data/data/com.termux/files/usr/tmp/: Permission denied
  [proot] proot error: can't create temporary directory: Permission denied
  [proot] proot info: Please set PROOT_TMP_DIR env. variable to an alternate location (with write permission).
  ```
- **Cause:** Upstream Termux PRoot binaries default to `/data/data/com.termux/files/usr/tmp/`. Flide runs under its own package ID (`com.flide.debug`), where accessing Termux's data directory is prohibited by Android sandbox permissions.
- **Fix:** Added `tmpDir` (`files/tmp`) in `StorageLayout.kt`, ensure `chmod 0755` permissions, bind `-b ${storageLayout.tmpDir.absolutePath}:/tmp`, and explicitly pass `PROOT_TMP_DIR` and `TMPDIR` pointing to this private directory in `ProotRunner.kt`.

### Issue 2: Rootfs Container Directory Prefix (`/bin/sh` not found)
- **Symptom:**
  ```
  [proot] proot error: '/bin/sh' not found (root = /data/user/0/com.flide.debug/files/rootfs, cwd = /home/flide)
  ```
- **Cause:** The official `debian-bookworm-aarch64-pd-v4.17.3.tar.xz` archive packages all files under a top-level directory `debian-bookworm-aarch64/` (e.g. `debian-bookworm-aarch64/bin -> usr/bin`). Without stripping this prefix, files were extracted to `rootfs/debian-bookworm-aarch64/` instead of `rootfs/`.
- **Fix:** Implemented `stripLeadingComponent(entryName)` in `TarXzExtractor.kt` to strip container directory prefixes during extraction, added handling for hard links and recursive directory cleanup, and added pre-extraction cleanup in `EnvironmentManager.kt` to purge any stale nested extractions.
- **Unit Tests:** Added `TarXzExtractorTest.kt` verifying prefix stripping for both prefixed archives and flat tarballs.

---

## 4. Phase 1 Verification Summary
- **Storage & Engine:** Verified on-device (31.62 GB available, PRoot staged, Debian Bookworm arm64 extracted).
- **PRoot Userland:** Smoke test passed (`Debian Userland Smoke Test OK` and `uname -a` output).
- **Native Terminal:** Termux `TerminalView` + `TerminalSession` connected via Linux PTY with interactive login shell, soft keyboard focus, and extra keys row.

---

# Phase 2 Report — Flutter SDK Installation Inside the Environment

## 1. What Was Done

### 1.1 Flutter Environment & Path Configuration
- Configured persistent `PATH` across all userland interfaces:
  - Exported `PATH="/home/flide/flutter/bin:$PATH"` in `/etc/profile.d/flide_env.sh` and `/home/flide/.bashrc`.
  - Set default `PATH` in `ProotRunner.kt` to include `/home/flide/flutter/bin`.
  - Exported `FLUTTER_ROOT="/home/flide/flutter"` and `PUB_CACHE="/home/flide/.pub-cache"`.
- Added `/etc/apt/apt.conf.d/99sandbox` with `APT::Sandbox::User "root";` to bypass sandboxed user restrictions in PRoot during package downloads.
- Added `/.dockerenv` marker in rootfs to suppress container superuser execution warnings from Flutter.

### 1.2 Flutter SDK Manager (`FlutterManager.kt`)
- Full state machine (`NotInstalled`, `CheckingStorage`, `InstallingPackages`, `Cloning`, `Bootstrapping`, `Configuring`, `RunningDoctor`, `Ready`, `Error`).
- Pipeline tasks implemented:
  1. **Storage Check:** Verifies free device storage >= 1.5 GB.
  2. **Debian Build Tools:** Executes `apt-get update` and installs `git`, `curl`, `unzip`, `xz-utils`, `zip`, `ca-certificates`, `libglu1-mesa`, `clang`, `cmake`, `ninja-build`, `pkg-config`.
  3. **Git Configuration:** Sets `git config --global --add safe.directory "*"` to prevent ownership warnings.
  4. **Shallow Clone:** Clones `https://github.com/flutter/flutter.git` (`-b stable`, `--depth 1`) into `/home/flide/flutter`.
  5. **First-Run Bootstrap:** Runs `flutter --version`, automatically downloading the official linux-arm64 Dart SDK.
  6. **Disable Analytics:** Executes `flutter config --no-analytics` and `dart --disable-analytics`.
  7. **Web Toolchain Setup:** Executes `flutter config --enable-web` and `flutter precache --web` (skipping heavy Android/iOS SDK artifacts).
  8. **Toolchain Verification:** Executes `flutter doctor -v` and stores the report.
- Persistent logging: Streams real-time log output and writes to `logs/flutter_install.log`.

### 1.3 Flutter Setup UI Screen (`SetupFlutterScreen.kt`)
- Step-by-step progress display with determinate/indeterminate progress indicators.
- Channel selection (`stable`, `beta`).
- Live auto-scrolling terminal-style log output with syntax coloring for apt, git, flutter, and doctor.
- "Copy Log" button to clipboard.
- "Run Doctor" and "Open Terminal" buttons when Flutter is Ready.
- Integrated into `HomeScreen.kt` with a dynamic Flutter SDK status card.

### 1.4 Unit Testing
- Added `FlutterManagerTest.kt`: Tests required packages list, environment configuration (`.dockerenv`, `99sandbox`, profile PATH), and storage layout paths.
- Updated `ProotRunnerTest.kt`: Tests updated `PATH`, `FLUTTER_ROOT`, and `PUB_CACHE` variables.

---

# Phase 3 Report — Run and Preview (`flutter run -d web-server`)

## 1. What Was Done

### 1.1 Project Management Architecture (`com.flide.project`)
- **`FlutterProject.kt`**: Model representing Flutter projects with accessors for `rootDir`, `rootfsPath`, `mainDartFile`, `pubspecFile`, and validation status.
- **`ProjectManager.kt`**:
  - `scanProjects()`: Discovers and parses existing Flutter projects in `storageLayout.projectsDir` (mirrored to `/home/flide/projects` in PRoot). Reads `pubspec.yaml` description and version.
  - `validateProjectName()`: Enforces Dart identifier conventions (lowercase, underscores, valid identifier, reserved keyword protection).
  - `createProject()`: Executes `flutter create` inside PRoot with optional custom `--org` parameter.
  - `deleteProject()`: Safely purges project directory and updates state flows.
- **`CreateProjectDialog.kt`**:
  - Interactive project creation dialog.
  - Supports **"Run in Shell"** mode: switches directly into the interactive PTY terminal executing `flutter create <name>` in real-time, allowing the user to inspect the creation output directly.
  - Supports **"Quick Create"** mode: executes creation and directly transitions into the IDE workspace.

### 1.2 Flutter Run Controller (`com.flide.flutter.run`)
- **`FlutterRunController.kt`**:
  - Manages `flutter run -d web-server --web-hostname 127.0.0.1 --web-port <port>` process lifecycle inside Debian userland under PRoot.
  - Automatic port conflict detection with `findAvailablePort(8080)`.
  - ANSI escape code stripping and regex matching for Web server URLs (`http://127.0.0.1:<port>` / `http://localhost:<port>`) and DevTools URLs.
  - Attached standard input (`stdin`) pipeline for interactive commands:
    - **Hot Reload (`r`)**: Triggered on user demand or auto-save.
    - **Hot Restart (`R`)**: Triggered via toolbar action.
    - **Quit (`q`)**: Clean shutdown with fallback to `destroyForcibly()` if unresponsive.
  - Transitions through explicit states: `Idle`, `Starting`, `Compiling`, `Running`, `Reloading`, `Restarting`, `Stopped`, `Error`.
  - Integration with `IdeService`: Maintains Android foreground service and partial wake lock during active run sessions.

### 1.3 In-App WebView Preview (`WebPreviewView.kt`)
- Embedded Android `WebView` via Compose `AndroidView`:
  - JavaScript, DOM storage, database, wide viewport, and overview mode enabled.
  - `LOAD_NO_CACHE` and cache clearing enabled so hot reloads reflect immediately without stale scripts.
  - Custom `WebViewClient` and `WebChromeClient` with progress bar and connection error handling with retry button.
  - Top preview toolbar:
    - Live URL indicator and status dot.
    - Hot Reload (⚡ `r`) quick action.
    - Hot Restart (🔄 `R`) quick action.
    - WebView refresh button.
    - External browser launch intent button (`Open in Chrome / Browser`).

### 1.4 Run Console & Project File Editor (`RunConsoleView.kt` & `ProjectFileEditorView.kt`)
- **`RunConsoleView.kt`**:
  - Real-time auto-scrolling log console for `flutter run`.
  - Filter chips: `All`, `Output`, `Errors`, `System`.
  - Action buttons: Copy logs to clipboard, Clear console.
- **`ProjectFileEditorView.kt`**:
  - Embedded file browser (recursive file tree excluding `build/` and `.dart_tool/`).
  - Text editor for editing `lib/main.dart` and other project files directly on device.
  - Saving automatically triggers Hot Reload (`r`) when a run session is active.

### 1.5 Integrated IDE Screen & Home Screen Updates (`IdeScreen.kt` & `HomeScreen.kt`)
- **`IdeScreen.kt`**:
  - Top app bar with project name, live status, Run/Stop toggle (▶/⏹), and quick Hot Reload/Restart buttons.
  - Navigation tabs: **Preview**, **Editor**, **Console**, **Terminal** (embedded PTY shell running directly in `/home/flide/projects/<name>`).
- **`HomeScreen.kt`**:
  - Projects card list showing all created projects with "Open IDE & Run", "Shell", and "Delete" actions.
  - "+ New Project" button opening the creation dialog.
- **`AndroidManifest.xml`**:
  - Enabled `android:usesCleartextTraffic="true"` to permit cleartext HTTP communication with the local web server on `http://127.0.0.1:<port>`.

### 1.6 Unit Testing
- Added `ProjectManagerTest.kt`: Tests project name validation (valid, invalid, reserved Dart keywords), pubspec YAML parsing, project discovery, and deletion.
- Added `FlutterRunControllerTest.kt`: Tests ANSI sequence stripping, web server URL pattern matching, DevTools detection, and hot reload/restart output parsing.

---

## 2. Next Steps
1. Build debug APK on GitHub Actions (`mkdevofficial/sora`).
2. Install APK on real device.
3. In Flide:
   - Tap **New Project** and create a project (e.g. `counter_app`).
   - Open the IDE screen and tap **Run**.
   - Verify the counter app displays in the in-app WebView.
   - Switch to the Editor tab, make a change (e.g. modify counter text or theme color in `lib/main.dart`), tap **Save** (or tap ⚡ Hot Reload).
   - Verify the change immediately reflects in the WebView without losing state!
