# Flide — Flutter Mobile IDE

Flide is an on-device Flutter IDE for Android (arm64-v8a). It embeds a Linux userland (Debian arm64 under `proot`), installs the Flutter SDK inside the userland on demand, and enables writing, running, and hot-reloading Flutter apps directly on your phone using `flutter run -d web-server` with an in-app preview.

---

## 1. Architecture Overview

```
+-------------------------------------------------------------+
|                     Flide Android App                       |
|                                                             |
|  +--------------------+  +-------------------------------+  |
|  | Jetpack Compose UI |  | Sora Editor / Terminal View   |  |
|  | (Screens, Settings)|  | (AndroidView integrations)    |  |
|  +--------------------+  +-------------------------------+  |
|            |                             |                  |
|            v                             v                  |
|  +--------------------+  +-------------------------------+  |
|  | IdeService         |  | StorageLayout                 |  |
|  | (Foreground Svc +  |  | (Direct access to rootfs/     |  |
|  |  Partial WakeLock) |  |  projects/ & logs/)           |  |
|  +--------------------+  +-------------------------------+  |
+-------------------------------------------------------------+
                              |
                     proot process bridge
                              |
                              v
+-------------------------------------------------------------+
|              Debian arm64 Userland (PRoot)                  |
|                                                             |
|  - /home/flide/flutter/   (Flutter SDK on stable channel)   |
|  - /home/flide/projects/  (Flutter projects directory)      |
|  - System packages: git, curl, clang, cmake, ninja, etc.    |
|  - Dart Language Server (LSP)                               |
|  - flutter run -d web-server --web-hostname 127.0.0.1       |
+-------------------------------------------------------------+
```

### Key Technical Specifications

- **Platform:** Native Android (Kotlin). Jetpack Compose for application UI; classic Views via `AndroidView` for Sora Editor and Termux Terminal View.
- **Target SDK:** `28` (Android 9.0 Pie) — enables executing binaries from the application's internal data directory.
- **Min SDK:** `26` (Android 8.0 Oreo).
- **Compile SDK:** `35` (Android 15).
- **ABI:** `arm64-v8a` only.
- **Long-Running Process Isolation:** `IdeService` runs as an Android Foreground Service with a persistent notification and partial wake lock (`PARTIAL_WAKE_LOCK`), preventing background termination by the OS.

---

## 2. Storage Layout

The application maintains a strictly defined directory hierarchy under `Context.filesDir`:

| Directory | Path | Purpose |
| :--- | :--- | :--- |
| `rootfs/` | `<filesDir>/rootfs/` | Extracted Debian arm64 root filesystem. Contains `/home/flide/` for user environment. |
| `downloads/` | `<filesDir>/downloads/` | Download cache for Debian rootfs archives, proot binaries, and Flutter archives. |
| `projects/` | `<filesDir>/projects/` | Flutter project directories. Directly mapped/mirrored to `/home/flide/projects` inside the rootfs. |
| `logs/` | `<filesDir>/logs/` | Persistent operation logs, Flutter run logs, and diagnostic files. |
| `tmp/` | `<filesDir>/tmp/` | Private writable temporary directory for PRoot internals (`PROOT_TMP_DIR`) and guest `/tmp` mount. |

Both the Android editor and Flutter inside PRoot access the same project files on disk without network file sharing overhead.

---

## 3. Package Structure

```
com.flide
├── data
│   ├── settings    # Settings storage backed by DataStore Preferences
│   ├── storage     # Cache and storage cleaner (pub-cache, build/, downloads)
│   └── update      # In-app updater checking GitHub Releases
├── editor          # Sora Editor, TextMate themes, and Dart LSP integration
│   └── lsp         # Protocol bridge, diagnostics container, and language server manager
├── env             # PRoot environment controller, rootfs bootstrap, storage layout
├── flutter         # Flutter SDK manager, run controller, hot reload IPC
│   └── run         # Web-server process lifecycle and hot reload/restart controller
├── git             # Git repository manager, diff engine, branch switcher, and parser
├── github          # GitHub Actions cloud APK build integration and OAuth device flow
├── project         # Project discovery, template generator, and backup/restore
├── service         # IdeService foreground service and wake lock management
├── terminal        # Termux multi-tab terminal management and view bindings
└── ui              # Jetpack Compose UI screens, theme, and components
```

---

## 4. How the Environment Is Bootstrapped

1. **Space Verification:** Checks that free storage exceeds 2.5 GB before starting.
2. **Binary Staging:** Extracts/stages Bionic-compatible `proot` and support libraries (`arm64-v8a`).
3. **Rootfs Download & Verification:** Downloads Debian arm64 rootfs archive, verifies SHA-256 checksum, and extracts permissions and symlinks safely.
4. **Networking Setup:** Writes DNS resolvers (`1.1.1.1`, `8.8.8.8`) into `/etc/resolv.conf` within the rootfs.
5. **Initial Configuration:** Launches proot with fake root (`-0`) and mounts (`/dev`, `/proc`, `/sys`), executes `apt update`, and installs essential build tools (`git`, `curl`, `unzip`, `clang`, `cmake`, `ninja-build`).
6. **Flutter SDK Setup (Phase 2):** Performs shallow clone of Flutter stable into `/home/flide/flutter`, disables telemetry, and runs `flutter precache --web`.

---

## 5. Building the Project

### Continuous Integration (GitHub Actions)
Every push and pull request automatically builds the debug APK in GitHub Actions:
- Workflow file: `.github/workflows/build.yml`
- JDK: Temurin 17
- Artifact: `flide-debug-apk` (downloadable from GitHub Actions Run tab)

### Local Build (Command Line)
```bash
# Clone the repository
git clone https://github.com/<owner>/flide.git
cd flide

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

---

## 6. Bundled Components & Licenses

Flide incorporates open-source components with explicit licensing:

| Component | License | Notes |
| :--- | :--- | :--- |
| **Sora Editor** (`sora-editor`) | LGPL-2.1 | Code editor component with Tree-sitter and LSP client support. |
| **Termux Terminal Emulator** | GPL-3.0 / Apache-2.0 | Terminal view and emulation core adapted from Termux and Jackpal's Android-Terminal-Emulator. |
| **PRoot** | GPL-2.0 | User-space chroot/bind mount emulation engine. |
| **Debian Rootfs** | Debian Social Contract / Multi-license | Debian GNU/Linux arm64 base system packages. |

---

## 7. Features & Capabilities

- **Debian Userland inside PRoot**: Real Linux environment on Android arm64 with apt, git, clang, and cmake.
- **On-Device Flutter Toolchain**: Shallow clone of Flutter stable, linux-arm64 Dart SDK, web toolchain pre-cached.
- **Run & Live Preview**: Runs `flutter run -d web-server --web-hostname 127.0.0.1` inside userland with in-app WebView, live Hot Reload (`r`), and Hot Restart (`R`).
- **VS Code-Style Sora Editor**: Full TextMate grammar highlighting (Dart, YAML, JSON, Markdown), Dark+ theme, tabbed navigation, and soft-keyboard symbols toolbar.
- **Dart Language Server (LSP)**: Protocol bridge over stdio with container path translation, type diagnostics, problem panel, and `dart format` on demand or save.
- **Multi-Tab Terminal**: Concurrent interactive bash login shells, Termux-style extra keys row, and clipboard paste.
- **Integrated Git**: Real `git` commands, porcelain status parsing, diff viewer, commit creator, and branch switcher.
- **Cloud APK Builds**: Automatic `.github/workflows/build_apk.yml` generation, workflow dispatch, artifact polling, download, and on-device package installer launcher.
- **Storage Cleaner**: Cache cleaner for pub cache, build outputs, and downloads.
- **Backup & Restore**: Export/import projects as clean `.zip` archives.
- **Android 12+ Optimization**: Foreground service with wake lock, PPK guide, and battery optimization exemption.
- **Localization**: English and Arabic UI with native RTL layout.
