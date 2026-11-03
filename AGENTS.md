# Flutter Mobile IDE — Spec for AI Coding Agent

By Mokhtar. Language of this document: English. Work in phases, strictly in order. Do not start a phase until the previous one meets its acceptance criteria on a real device.

## 1. Goal

An Android app that is a full Flutter IDE running on the phone itself. It embeds a Linux userland (Debian arm64 under proot), installs the Flutter SDK inside it on demand, and lets the user write, run and hot-reload Flutter apps entirely on the device using `flutter run -d web-server` with the preview shown in an in-app WebView. Producing the final APK is done off-device through GitHub Actions (later phase).

Distribution is outside Google Play (GitHub Releases / direct APK / F-Droid). Play Store policy is explicitly not a constraint.

## 2. Technology decisions (fixed, do not change without asking)

- **App framework: native Android, Kotlin.** Jetpack Compose for general UI; classic Views wrapped with `AndroidView` where a library requires them (editor, terminal). Reason: this project is mostly process control, file system work, a foreground service and a terminal, all of which need first-class native access. Flutter/Expo would only add a bridge layer around everything important.
- **Code editor component: Sora Editor** (sora-editor), including its tree-sitter or TextMate language support and its LSP client module. Verify its license before bundling.
- **Terminal: Termux terminal-emulator and terminal-view libraries** (or a clean equivalent). Verify their license before bundling and report it.
- **`targetSdkVersion` = 28**, `minSdkVersion` = 26, `compileSdk` = latest stable. This is what allows executing binaries from the app's data directory (same approach as Termux). Keep a documented fallback: shipping executables as `lib*.so` files in `jniLibs` so they live in the executable native library directory.
- **Linux environment: Debian (stable) arm64 rootfs run with proot.** The app must implement the bootstrap itself in Kotlin (download, verify, extract, configure). Do not depend on Termux being installed and do not use the `proot-distro` script as a runtime dependency; reproduce its behavior.
- **Language for Flutter inside the rootfs:** Flutter stable channel, shallow clone (`git clone` of the official repo, `-b stable`, `--depth 1`) into the rootfs home directory.
- **Min device assumption:** arm64-v8a only. Primary test range Android 12 to 15.

## 3. Global requirements

- Every long operation (download, extract, install, clone, precache) shows determinate progress where possible, supports cancel, survives app restart (resume or clean retry), and logs to a viewable log file.
- All long-running processes run inside a **foreground service** with a persistent notification and a partial wake lock, so Android does not kill them.
- Storage layout must be documented in the repo README and kept stable: app files dir contains `rootfs/`, `downloads/`, `projects/`, `logs/`. Projects live inside the rootfs home so Flutter and the editor see the same files; the editor reads and writes them from the Android side through the rootfs path.
- Networking inside proot must work out of the box (DNS via a generated `resolv.conf`).
- No telemetry. Dart/Flutter analytics disabled by default inside the rootfs.
- Code quality: small modules, no giant activities, unit tests for pure logic (progress parsing, URL detection, path handling), clear error messages with a "copy log" button.
- Ask me before adding any dependency not listed here.

## 4. Phases (most important first)

### Phase 0 — Project skeleton

Tasks: Gradle project (Kotlin, Compose), package structure (`env`, `flutter`, `editor`, `terminal`, `github`, `ui`, `service`), foreground service stub, settings storage, CI workflow that builds a debug APK on GitHub Actions, README with architecture overview. Acceptance: APK builds in CI and installs on device; app opens to an empty home screen.

### Phase 1 — Linux environment (proot + Debian rootfs)  ← most critical

Tasks:

1. Ship or download a Bionic-compatible `proot` binary (and its loader/talloc dependencies) for arm64. Document exactly where it comes from and its version.
2. Download the Debian arm64 rootfs archive, verify checksum, extract with correct permissions and symlinks (handle extraction in Kotlin without relying on missing system tools; if a bundled `tar` is needed, ship it).
3. Launch commands inside proot with proper flags: fake root (`-0`), bind mounts for `/dev`, `/proc`, `/sys`, shared storage (optional), `--link2symlink`, correct `PATH`, `HOME`, `TERM`, `LANG`, `TMPDIR`.
4. First-run setup: generate `resolv.conf`, run `apt update`, install `git`, `curl`, `unzip`, `xz-utils`, `zip`, `ca-certificates`, `libglu1-mesa`, `clang`, `cmake`, `ninja-build`, `pkg-config` (only what Flutter web needs; keep the list minimal and justified).
5. Minimal built-in terminal screen attached to a shell inside the rootfs to prove everything works.
6. Reset / reinstall / delete environment actions, and a disk usage readout. Acceptance: on a fresh install the user taps "Set up environment", waits, then gets a working Debian shell where `apt`, `git` and internet access work. Killing and reopening the app keeps the environment intact.

### Phase 2 — Flutter SDK installation inside the environment

Tasks: shallow clone of Flutter stable, run `flutter --version`, `flutter config --no-analytics`, `flutter config --enable-web`, `flutter precache --web` (skip Android and iOS artifacts), handle first-run Dart SDK download for linux-arm64, show progress and disk usage, detect and report failure reasons (network, space, memory). Provide an "Update Flutter" and "Switch channel/version" action (version pinning optional, stable is default). Acceptance: `flutter doctor` runs and reports the web toolchain as healthy; `flutter create` produces a project that analyzes cleanly with `dart analyze`.

### Phase 3 — Run and preview (`flutter run -d web-server`)

Tasks: project list (create from template, open existing, delete); a run controller that starts `flutter run -d web-server --web-hostname 127.0.0.1 --web-port <fixed port>` inside proot, parses its output to detect when the server is ready, shows the app in an in-app WebView, and keeps stdin attached so the app can send hot reload (`r`) and hot restart (`R`) and quit (`q`). Show the run console with filterable output. Handle port conflicts and process cleanup on stop or crash. Foreground service keeps it alive. Acceptance: create a new project, tap Run, see the counter app in the WebView, edit a file externally, trigger hot reload from the app and see the change.

### Phase 4 — Code editor and file management

Tasks: file tree with create, rename, delete, move; tabbed editor on Sora Editor; Dart syntax highlighting (plus YAML, JSON, Markdown); auto-save with debounce; find and replace, go to line, undo/redo, bracket matching, auto-indent, a symbols row above the keyboard (`{ } ( ) ; < > =` and tab); font size and theme settings; save triggers hot reload automatically when a run session is active (toggle in settings). Acceptance: user can write Dart code comfortably on a phone and see the result hot-reload on save without leaving the app.

### Phase 5 — Dart language server (LSP)

Tasks: run `dart language-server` inside proot, bridge its stdio to the editor's LSP client, support diagnostics (errors and warnings underlined and listed in a problems panel), completion, hover docs, go to definition, signature help, quick fixes, organize imports and `dart format` on save. Handle server crash and restart, and show an indexing indicator. Watch memory use and offer a "lightweight mode" that disables the server. Acceptance: typing a widget name shows real completions from the analyzer; introducing a type error shows a diagnostic within a few seconds.

### Phase 6 — Integrated terminal and git

Tasks: multiple terminal tabs inside the rootfs, extra keys row (ctrl, alt, esc, arrows), copy/paste; git UI basics (status, diff, commit, branch) backed by the real `git` in the rootfs; credential handling that reuses the token from Phase 7 once available. Acceptance: user can run `flutter pub add`, `dart test` and git commands from the in-app terminal and the file tree reflects changes.

### Phase 7 — GitHub integration and cloud APK builds

Tasks: GitHub OAuth Device Flow login (no custom server), token in encrypted storage with minimal scopes; push/pull for the current project; generate a ready `.github/workflows` file that builds the APK (cached Flutter and Gradle, `--split-per-abi` option) and uploads it as an artifact; trigger via push or `workflow_dispatch`; poll run status, show logs, download the APK artifact, and offer to install it through the package installer. Acceptance: from a project in the IDE, tap "Build APK", watch the Actions run, download and install the resulting APK, all without leaving the app.

### Phase 8 — Polish and resilience

Tasks: first-run guide for Android 12+ (Phantom Process Killer and battery optimization, with instructions for the user), crash and log export, backup and restore of projects, storage cleaner (pub cache, build cache), multiple Flutter versions, split view (editor + preview), keyboard shortcuts for hardware keyboards, localization (Arabic and English UI), and an in-app updater that checks GitHub Releases. Acceptance: environment survives low-memory conditions with clear recovery paths; a new user can follow the guide and succeed unaided.

## 5. Known risks the agent must handle explicitly

- Android 12+ may kill heavy background processes; the foreground service, wake lock and the user guide are mandatory mitigations.
- Flutter web compilation is CPU and RAM heavy; surface clear errors on out-of-memory and offer to restart.
- proot is slower than native for file-heavy operations; avoid unnecessary file scans, and exclude `build/` and `.dart_tool/` from editor indexing.
- Android-only plugins do not work in the web preview; show a note in the UI and point to the Phase 7 cloud build for full testing.
- Storage: the full environment is roughly 2 to 3 GB; check free space before each install step.

## 6. Non-goals

Local APK builds (Gradle, Android SDK), iOS builds, Play Store compliance, a plugin marketplace, collaboration features, desktop and tablet-specific layouts (until Phase 8).

## 7. Working rules for the agent

- After each phase, write a short `PHASE_REPORT.md` covering what was done, what was tested on device, known issues, and exact next steps. Stop and wait for my confirmation before starting the next phase.
- If a requirement is ambiguous or a library choice fails, stop and ask instead of silently substituting.
- Keep the README current: build instructions, storage layout, how the environment is bootstrapped, and the licenses of bundled components.
