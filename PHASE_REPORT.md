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

# Phase 4 Report — Code Editor, File Management & Background Mode

## 1. What Was Done

### 1.1 Sora Editor & TextMate Syntax Highlighting
- Integrated modern Sora Editor (`io.github.rosemoe:editor-bom:0.24.5`, `editor`, and `language-textmate`).
- Configured VS Code Dark+ theme (`dark_plus.json`).
- Bundled TextMate language grammars in `app/src/main/assets/textmate/`:
  - **Dart** (`dart.tmLanguage.json`): Keywords, classes, methods, annotations, strings, operators.
  - **YAML** (`yaml.tmLanguage.json`): Keys, values, comments for `pubspec.yaml`.
  - **JSON** (`json.tmLanguage.json`): Key-value pairs, primitives.
  - **Markdown** (`markdown.tmLanguage.json`): Headers, code blocks, formatting.
  - Master languages manifest (`languages.json`).
- **`EditorThemeManager.kt`**: Initializes `FileProviderRegistry` with `AssetsFileResolver`, loads Dark+ theme into `ThemeRegistry`, registers grammars, and maps file extensions to language definitions dynamically.

### 1.2 Programmatic Editor Controller (`EditorController.kt`)
- Direct controls for the active `CodeEditor`:
  - `undo()` and `redo()`.
  - `insertSymbol(symbol)`: Inserts characters/brackets/keywords at cursor position.
  - `gotoLine(line)`: Navigates cursor to requested 1-based line number.
  - `search(query, matchCase, regex)`, `findNext()`, `findPrevious()`, `stopSearch()`.
  - `replace(replacement)` and `replaceAll(replacement)`.

### 1.3 Tabbed Editor Interface (`TabbedEditorView.kt` & `EditorTab.kt`)
- Multi-document tab bar with:
  - Language-specific color icons (Dart: blue, YAML: orange, JSON: green).
  - Dirty indicator (`•`) showing unsaved modifications.
  - Tab close buttons and tab switching.
  - Docked floating Find & Replace search bar with previous/next matches and replace all.
  - "Go to Line" numerical jump dialog.

### 1.4 Symbols Row & Keyboard Toolbar (`EditorToolbarRow.kt`)
- Extra keys row docked directly above the Android soft keyboard:
  - Essential coding symbols: `TAB` (`  `), `{`, `}`, `(`, `)`, `[`, `]`, `;`, `:`, `=`, `<`, `>`, `"`, `'`, `$`, `_`, `.`, `/`, `!`, `?`, `|`, `\`, `@`, `&`.
  - Quick action buttons: Undo, Redo, Find & Replace, Go to Line, and Save.

### 1.5 Project File Explorer Drawer (`FileExplorerDrawer.kt`)
- Hierarchical tree view showing project files and directories (excluding `build/`, `.dart_tool/`, `.git/`).
- Expand / collapse folders with state retention (with `lib/` expanded by default).
- File operations:
  - **New File**: Create files with subpath support (e.g. `lib/models/user.dart`).
  - **New Folder**: Create nested directories (e.g. `lib/widgets`).
  - **Rename**: Rename any file or folder.
  - **Delete**: Recursive deletion with confirmation dialog.

### 1.6 Auto-Save & Hot Reload Integration
- Configured debounced auto-save (1.5 seconds) in `SoraEditorView.kt` using `ContentChangeEvent`.
- On save (manual or auto-save), if a `flutter run -d web-server` session is active, Flide automatically sends Hot Reload (`r`) to the runner process, updating the WebView preview immediately.

### 1.7 Background Execution Mode
- Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission in `AndroidManifest.xml`.
- Starting `IdeService` with a partial wake lock on app launch in `MainActivity.kt`.
- Added a "Background Execution" card on `HomeScreen.kt` with a direct one-tap button to exempt Flide from battery optimization, ensuring PRoot and the Flutter web server run uninterrupted when the screen is locked or the app is minimized.

### 1.8 Unit Testing
- Added `EditorTabTest.kt`: Tests editor tab data model, dirty state management, and tab properties.

---

# Phase 5 Report — Dart Language Server (LSP)

## 1. What Was Done

### 1.1 LSP Client & Protocol Bridge (`editor-lsp` & `org.eclipse.lsp4j`)
- Added `io.github.rosemoe:editor-lsp:0.24.5` and `org.eclipse.lsp4j:1.0.0` dependencies.
- **`LspMessageTranslator.kt`**:
  - Implemented framing and parsing of JSON-RPC protocol messages (`Content-Length: <n>\r\n\r\n`).
  - Container/chroot path translation: automatically translates Android host sandbox paths (`/data/user/0/com.flide/...`) to PRoot Debian guest paths (`/home/flide/projects/...`) on outgoing messages, and translates guest paths back to host paths on incoming messages.
  - Automatically recalculates `Content-Length` headers for altered JSON bodies.
  - Intercepts `$/progress` and `workDoneProgress` notifications to report indexing/analysis state to the UI.
- **`DartLspConnectionProvider.kt`**:
  - Implements Sora Editor's `StreamConnectionProvider`.
  - Spawns `/home/flide/flutter/bin/dart language-server --protocol=lsp` inside Debian under PRoot.
  - Bridges `stdin` and `stdout` using buffered piped stream pumps and path translation.
  - Captures `stderr` and monitors process lifetime with automatic crash detection.

### 1.2 Dart LSP Manager & Lifecycle (`DartLspManager.kt`)
- Integrated with `FlideApplication`:
  - `initializeForProject(project)`: Prepares the `LspProject` workspace and registers `CustomLanguageServerDefinition`.
  - `getOrCreateLspEditor(file)`: Connects active `.dart` files to the language server.
  - `formatFile(file)`: Runs `/home/flide/flutter/bin/dart format` on demand or on save.
  - `restartServer()`: Stops and restarts the analyzer process on demand or after a crash.
  - **Lightweight Mode**: Can disable the Dart analyzer to save memory on low-spec phones, allowing pure TextMate editing.

### 1.3 Diagnostics & Problems Panel (`ProblemsBottomSheet.kt` & `ProblemItem.kt`)
- Real-time diagnostic collection:
  - Error and warning underlines (squiggles) rendered directly in the code editor.
  - Interactive **Problems Bottom Sheet** listing all diagnostics across open files:
    - Filter chips: All, Errors (with count), Warnings (with count).
    - Severity indicators (Error 🔴, Warning 🟡, Info 🔵).
    - Human-readable file name and `[Line, Column]` coordinates.
    - One-tap navigation: Tapping any problem opens that file in the editor and jumps directly to that line and column.

### 1.4 UI Integration in `TabbedEditorView.kt` & `EditorToolbarRow.kt`
- **LSP Status Pill**: Displays real-time analyzer state in the tab bar:
  - `● LSP` (Ready 🟢)
  - `⏳ Indexing` (Analysis in progress 🟡)
  - `○ Starting` (Spawning process 🔵)
  - `● Error` (Crashed/failed 🔴)
  - `○ Light` (Lightweight mode ⚪)
  - Tapping the pill opens a dialog to restart the analyzer or toggle Lightweight Mode.
- **Problems Badge Button**: Displays current issue count (e.g. `⚠️ 3`). Tapping opens the Problems Bottom Sheet.
- **Code Format Button (`🪄`)**: Formats the active Dart file with `dart format` in PRoot and updates the editor buffer.

### 1.5 Unit Testing
- Added `LspMessageTranslatorTest.kt`: Tests JSON-RPC framing, outgoing/incoming path translation, and progress detection.
- Added `ProblemItemTest.kt`: Tests conversion of LSP4J `Diagnostic` objects to human-friendly problem items with 1-based indexing.

---

# Phase 6 Report — Integrated Terminal & Git Integration

## 1. What Was Done

### 1.1 Multi-Tab Integrated Terminal (`TerminalScreen.kt` & `TerminalTabItem`)
- Multi-session terminal architecture inside the Debian arm64 rootfs:
  - Dynamic scrollable tab bar supporting multiple independent bash sessions concurrently.
  - Tab management: Create new tabs (`+`), switch active tab with instant session attachment, and close tab (`X`).
  - Processes and command states persist across tab switching.
  - Extra Keys Toolbar docked above software keyboard: `ESC`, `TAB`, `CTRL`, `ALT`, `-`, `/`, `|`, `~`, `▲`, `▼`, `◀`, `▶`.
  - One-tap clipboard paste into active terminal session.
  - Fast environment validation command chips (`uname -a`, `apt update`, `git status`, `clear`).

### 1.2 Real Git Integration (`GitManager.kt` & `GitModels.kt`)
- Backed by real `git` binary inside Debian userland under PRoot:
  - Repository initialization (`git init`) and user configuration setup.
  - Robust porcelain status parser (`git status --porcelain=v1 -b`): identifies staged files, working tree changes, and untracked files.
  - Colorized diff inspection (`git diff` and `git diff --cached`).
  - Staging control: `stageAll()`, `stageFiles()`, and `unstageFiles()`.
  - Commits: `commit(message)` with automated user config fallback.
  - Branching: list branches (`git branch --list`) and branch checkout/creation (`git checkout -b <name>`).
  - Commit History: `getLog()` returning hash, short hash, author, subject, and date.

### 1.3 Interactive Git UI Sheet (`GitBottomSheet.kt`)
- Integrated into `IdeScreen.kt` top action bar via the Git branch icon (`Icons.Default.CallSplit`):
  - **Branch Pill & Switcher**: Displays current branch; tap to switch branches or create a new branch.
  - **Stage All & Refresh**: Quick staging of all project changes.
  - **Commit Box**: Commit message entry and one-tap commit execution.
  - **File Changes List**:
    - Staged changes section (green badges `M`, `A`, `D`, `R`) with unstage buttons.
    - Working tree changes section (yellow badges) with stage buttons.
    - Untracked files section (blue badges).
    - Tapping any modified file opens a built-in **Diff Viewer** modal with syntax highlighting (`+` green, `-` red, `@@` blue).
  - **Recent Commits History**: Shows recent commits with short hash and commit metadata.

### 1.4 Unit Testing
- Added `GitManagerTest.kt`: Tests status output parsing for clean repositories, fresh repositories with no commits, and complex status outputs containing staged, unstaged, and untracked files.

---

# Phase 7 Report — GitHub Integration & Cloud APK Builds

## 1. What Was Done

### 1.1 GitHub REST API & OAuth Client (`GitHubApiClient.kt` & `GitHubModels.kt`)
- Implemented pure Kotlin HTTP client with no third-party HTTP overhead:
  - **Device Flow Auth**: Device authorization via `https://github.com/login/device/code` and polling `https://github.com/login/oauth/access_token`.
  - **Personal Access Token (PAT)** support with user validation (`/user`).
  - **Workflow Dispatch**: Triggers GitHub Actions workflows via `POST /repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches`.
  - **Run Polling**: Queries active run lifecycle states (`queued`, `in_progress`, `completed`, `conclusion`) via `GET /repos/{owner}/{repo}/actions/runs`.
  - **Artifact Fetching & Download**: Enumerates artifacts and downloads ZIP archives with HTTP 302/S3 redirect handling and progress callback.

### 1.2 Cloud APK Build Automation (`CloudBuildManager.kt`)
- Full cloud build orchestration:
  - **Workflow Generator**: Generates production-ready `.github/workflows/build_apk.yml` in the project:
    - Sets up JDK 17 (Temurin).
    - Caches Flutter SDK and Gradle dependencies (`subosito/flutter-action@v2`).
    - Executes `flutter build apk --release --split-per-abi`.
    - Uploads `app-arm64-v8a-release.apk` artifact.
  - Commits workflow into repository and triggers the workflow.
  - Polls workflow execution every 5 seconds until `conclusion: success`.
  - Downloads release artifact ZIP into `downloadsDir`.
  - Extracts the release APK using in-memory `ZipInputStream`.
  - Launches Android Package Installer using `FileProvider` (`ACTION_VIEW`, `application/vnd.android.package-archive`).

### 1.3 Cloud Build UI Sheet (`CloudBuildBottomSheet.kt`)
- Integrated into `IdeScreen.kt` top action bar via the Cloud Build icon (`Icons.Default.CloudUpload`):
  - Config inputs for Repository Owner, Repository Name, Branch, and GitHub Token.
  - "Trigger Cloud Build" action button.
  - Live pipeline status:
    - `Preparing`: Workflow file generation and dispatch.
    - `Building`: Live run ID and runner status (`QUEUED` / `IN_PROGRESS`).
    - `Downloading`: Determinate progress bar showing download percentage.
    - `ReadyToInstall`: Success card with "Install APK on Device" button.
    - `Error`: Error banner with details and "Try Again" recovery.

### 1.4 System Permissions & FileProvider Configuration
- Added `REQUEST_INSTALL_PACKAGES` permission in `AndroidManifest.xml`.
- Configured `androidx.core.content.FileProvider` in `AndroidManifest.xml` with `res/xml/file_paths.xml` granting content URI access for downloads.

### 1.5 Unit Testing
- Added `CloudBuildManagerTest.kt`: Tests workflow YAML generation, ensuring all essential steps (JDK 17, Flutter cache, split-per-abi, artifact upload) are present and valid.

---

# Phase 8 Report — Polish, Resilience & Final System Integration

## 1. What Was Done

### 1.1 Android 12+ First-Run Guide (`Android12GuideDialog.kt`)
- Created interactive optimization guide dialog explaining mitigations for modern Android process limits:
  - **Phantom Process Killer (PPK)**: Explains the 32 child-process limit introduced in Android 12, with one-tap copyable ADB command:
    `adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"`
    and instructions for Developer Options -> "Disable child process restrictions" on Android 12L+.
  - **Battery Optimization Exemption**: One-tap shortcut launching system battery optimization intent.

### 1.2 Storage Cleaner (`StorageCleaner.kt` & `StorageCleanerDialog.kt`)
- Full storage management suite:
  - Analyzes cleanable disk space across:
    - **Pub Package Cache**: `~/.pub-cache/`
    - **Project Build Outputs**: `build/` directories across all projects
    - **Temporary Downloads**: Cached APKs and ZIP archives in `downloads/`
  - "Clean All" action running non-interactive cleanup to free storage.

### 1.3 Project Backup & Restore (`ProjectBackupManager.kt`)
- Project portability:
  - **ZIP Export**: Exports any Flutter project as a clean `.zip` archive (excluding `build/`, `.dart_tool/`, `.git/`) into `downloads/` for backup or sharing.
  - **ZIP Import**: Extracts external project archives into the internal project workspace.

### 1.4 In-App Updater (`AppUpdateManager.kt` & `AppUpdateDialog.kt`)
- Automatic update detection:
  - Queries `https://api.github.com/repos/mkdevofficial/sora/releases/latest`.
  - Performs semver comparisons against app version (`0.1.0`).
  - Displays release title, markdown release notes, and direct download button.

### 1.5 Hardware Keyboard Shortcuts
- Added physical keyboard listener (`Modifier.onPreviewKeyEvent`):
  - `Ctrl + S`: Trigger Save & Hot Reload.
  - `Ctrl + R`: Trigger Hot Restart.

### 1.6 Localization (English & Arabic)
- Added full Arabic localization in `res/values-ar/strings.xml` with native RTL layout support alongside English defaults in `res/values/strings.xml`.

---

## 2. Project Milestone Summary (Phases 0 through 8 Complete)

| Phase | Description | Status |
| :--- | :--- | :--- |
| **Phase 0** | Project Skeleton, Compose UI, Foreground Service, DataStore, CI | ✅ Complete |
| **Phase 1** | Debian arm64 Rootfs, PRoot Engine, PTY Interactive Terminal | ✅ Complete & Verified on Device |
| **Phase 2** | Flutter SDK Bootstrap (stable), Dart arm64, Web Toolchain | ✅ Complete & Verified on Device |
| **Phase 3** | Project Manager, `flutter run -d web-server`, In-App WebView Preview, Live Hot Reload (⚡ `r`) / Hot Restart (🔄 `R`) | ✅ Complete |
| **Phase 4** | Sora Editor, TextMate Highlighting (Dart/YAML/JSON/MD), VS Code Dark+ Theme, Multi-File Tabs, Symbols Row, File Explorer | ✅ Complete |
| **Phase 5** | Dart Language Server (LSP) Bridge, Type Diagnostics, Problems Panel, Code Formatting (`dart format`), Lightweight Mode | ✅ Complete |
| **Phase 6** | Multi-Tab Terminal Sessions, Extra Keys Toolbar, Real Git Integration (`git status`, diff, commit, branch checkout) | ✅ Complete |
| **Phase 7** | GitHub Actions Cloud APK Builds, OAuth Device Flow / PAT, Artifact Downloader & On-Device APK Installer | ✅ Complete |
| **Phase 8** | Android 12+ PPK Guide, Storage Cleaner, Project Backup/Restore, In-App Updater, Keyboard Shortcuts, Arabic Localization | ✅ Complete |
