# Archive Sentinel

Archive Sentinel is a safety-first video archive optimization application built around Tdarr. It reduces storage usage while protecting originals until optimized replacements pass strict validation, promotion, checksum, and archive-retention gates.

## Current Status

Implemented:

- Kotlin + Spring Boot backend with PostgreSQL persistence.
- React + Vite frontend with modular pages, components, hooks, typed models, and API client modules.
- Host-native mode as the default operating mode.
- Optional Docker Compose deployment with PostgreSQL, API, UI, managed Tdarr server, and managed Tdarr node.
- First-run setup and editable Settings.
- OS-native picker endpoints for Windows and Linux folder/file selection when the backend is running interactively on the host.
- Storage roots plus file/folder policies with recursive matching, exclusion, extension exclusions, retention, deletion mode, and output mode overrides.
- Precheck scanning against all enabled roots or a selected subset of roots, with async progress, metadata caching for unchanged files, parent/child root de-duplication, storage-root navigation, collapsed folder master/detail rows, durable server-side selection state, and enabled extensions configured from Settings.
- Dedicated HTML and log reports for each precheck and optimization run.
- Failure reasons in generated reports/logs for both precheck and optimization runs.
- Run details, progress bars, report/log artifact links, server-side pagination, filtering, and aggregate report views.
- Per-root effective policy visibility, editable storage roots, one-run root overrides, automation rules for newly discovered files, and run-first archive restore flows.
- SMTP settings in the UI for scheduled aggregate emails.
- Tdarr-backed candidate generation, validation, staged promotion, archive retention, restore, manual approval, and automatic deletion.
- Configurable Tdarr queue batch size, worker-thread settings, media tool paths, output container, validation tolerance, and Tdarr encoding arguments.
- Real Tdarr scan submission batching, so a configured batch size of `10` is submitted to Tdarr as up to 10 files in one scan request.
- NVIDIA GPU wiring for Docker-managed Tdarr nodes plus GPU-first host-native Tdarr defaults using `hevc_nvenc`.

## Safety Model

The core safety rule is unchanged: an original video is not moved out of its live location until the replacement has already been generated, validated, promoted, and validated again.

Current validation checks include:

- candidate exists
- video stream count matches
- audio stream count matches
- subtitle stream count matches
- stream topology matches
- chapter count matches
- critical metadata is preserved
- duration delta is no more than `0.5` seconds
- candidate is smaller than the original
- video and audio streams decode without ffmpeg errors
- promoted file checksum matches the staged candidate checksum

After candidate validation, the staged file is moved into the optimized location rather than left behind as an extra copy.

## Host-Native Quick Start

### 1. Install prerequisites

| Prerequisite | Install link | Credentials or values to keep |
| --- | --- | --- |
| Java 21 JDK | [Eclipse Temurin JDK 21 downloads](https://adoptium.net/temurin/releases?version=21) or `winget install EclipseAdoptium.Temurin.21.JDK` | None |
| Node.js | [Node.js downloads](https://nodejs.org/en/download) | None |
| PostgreSQL | [PostgreSQL downloads](https://www.postgresql.org/download/) and [Windows installer page](https://www.postgresql.org/download/windows/) | The PostgreSQL `postgres` admin password you choose during install |
| Tdarr | [Tdarr native install guide](https://docs.tdarr.io/docs/installation/windows-linux-macos/) | No Archive Sentinel credential is needed; keep the Tdarr server URL |
| FFmpeg / FFprobe | [FFmpeg downloads](https://ffmpeg.org/download.html) | None |

Archive Sentinel can auto-detect the ffmpeg and ffprobe binaries bundled with a native Tdarr updater install, so a separate FFmpeg install is optional when using that layout.

### 2. Create the Archive Sentinel database

Create the database once. If PostgreSQL is installed but its `bin` directory is not on `PATH`, use the full paths to `createuser`, `createdb`, and `psql` from the PostgreSQL install directory.

```powershell
createuser -U postgres archive_sentinel
createdb -U postgres -O archive_sentinel archive_sentinel
psql -U postgres -d archive_sentinel -c "alter user archive_sentinel with password 'archive_sentinel'; grant all privileges on database archive_sentinel to archive_sentinel;"
```

Keep these Archive Sentinel database credentials:

```text
database: archive_sentinel
username: archive_sentinel
password: archive_sentinel
```

### 3. Start and link Tdarr

For a native Tdarr install, download the updater, place it in its own folder, run the updater, then start both the Tdarr server and a Tdarr node. Confirm the server answers at `http://localhost:8266/api/v2/status`.

In Archive Sentinel Settings use:

```text
Tdarr mode: EXISTING
Tdarr URL: http://localhost:8266
```

The default host-native transcode arguments use NVIDIA NVENC:

```text
,-map 0 -map_metadata 0 -map_chapters 0 -c:v hevc_nvenc -preset p5 -cq 28 -c:a copy -c:s copy
```

If the host-native node does not have a supported NVIDIA GPU, change the Tdarr arguments in Settings before running production jobs.

For GPU encoding, the native Tdarr node must have at least one GPU transcode worker available. Archive Sentinel now switches compatible host-native nodes from CPU transcode workers to a GPU transcode worker automatically when the configured arguments request a GPU encoder such as `hevc_nvenc`.

### 4. Start Archive Sentinel

Start the backend:

```powershell
cd backend
.\gradlew.bat bootRun
```

Start the UI:

```powershell
cd frontend
npm install
npm run dev
```

If the backend is running on a non-default port during testing, set `VITE_API_PROXY_TARGET` before starting Vite, for example `http://localhost:8081`.

Open `http://localhost:5173` and sign in with:

```text
username: admin
password: admin
```

Host-native defaults use normal system paths:

```text
runtime/archive
runtime/optimized
runtime/staging
```

Add your real media folders as storage roots from the dashboard. Each root can optionally override its optimized-output and archive destinations; when no override is set, parallel mode writes under the global optimized/archive roots plus a namespace based on the storage root path, for example `runtime/optimized/CLIPS/Game Name/...`.

### Linux one-command setup
Install Git, clone the repo, enter it, then run the helper from the repository root:

```bash
sudo apt update
sudo apt install -y git
git clone https://github.com/BahaaAlmajed1/archive-sentinel.git
cd archive-sentinel
```

The helper can set up either the host-native app path or the full Docker Compose stack:

```bash
bash scripts/setup-linux.sh host
bash scripts/setup-linux.sh docker
```

Host mode installs Java 21, Node.js 22, PostgreSQL, FFmpeg, and Zenity where the package manager supports it, creates the `archive_sentinel` database/user, builds the app, and starts backend/frontend processes with logs in `runtime/logs`. It also writes `.env.local` with the backend port, UI proxy target, Tdarr URL, and LAN-safe CORS origins so `http://<server-ip>:5173` works from another machine on the same network.

Host mode expects an existing Tdarr server and at least one Tdarr node at `TDARR_URL`:

```bash
TDARR_URL=http://localhost:8266 bash scripts/setup-linux.sh host
curl http://localhost:8266/api/v2/status
```

The default host encoding arguments target NVIDIA NVENC. On a CPU-only Linux server, open Settings before starting real jobs and use HEVC CPU arguments while keeping `Codecs to skip` as `hevc`:

```text
,-map 0 -map_metadata 0 -map_chapters 0 -c:v libx265 -preset ultrafast -crf 32 -c:a aac -b:a 96k -c:s copy
```

Docker mode builds and starts PostgreSQL, API, UI, managed Tdarr server, and managed Tdarr node through Compose.

### 5. First login

Sign in with:

```text
username: admin
password: admin
```

Then change the password from Settings.

## Docker Compose Optional Path

Docker remains available for users who prefer isolated services:

```powershell
docker compose up -d --build
```

Open:

- UI: `http://localhost:3000`
- API: `http://localhost:8080`
- managed Tdarr UI: `http://localhost:8265`

In Docker mode, container paths map to host folders:

```text
/data/originals  -> ./runtime/originals
/data/optimized  -> ./runtime/optimized
/data/archive    -> ./runtime/archive
/data/staging    -> ./runtime/staging
```

Override them with:

```text
ORIGINALS_HOST_PATH
OPTIMIZED_HOST_PATH
ARCHIVE_HOST_PATH
STAGING_HOST_PATH
```

## Restart And Reinstall Safety

Restarting the backend, UI, Tdarr, PostgreSQL, or Docker Compose does not delete media files. Archive Sentinel stores operational state in PostgreSQL and writes media to the configured system folders. A reinstall is safe for files as long as you do not manually delete the configured media folders, the PostgreSQL data directory, or the Docker `postgres_data` volume.

For production, keep PostgreSQL backups and put archive, optimized, staging, and originals folders outside disposable build directories.

## Reports

Each precheck and optimization run produces:

- a modern HTML report with collapsed folder/file sections
- a plain-text log
- links that ask the backend to reveal the file in the host file manager instead of trying to open the media file in the browser
- old size, new size, saved space, status, and failure messages

The dashboard keeps storage roots on the left and collapsed folder groups on the right while the server stores the authoritative selection state. Files that were never compressed are selected by default, files already optimized once are still shown but unselected by default, and pagination does not change what will run. Folder groups default to 10 visible directories with 50 and 100 directory options, folder paging advances directory groups rather than unrelated file pages, and expanding a directory loads that directory's own matching files on demand.

If a parent storage root is selected, child roots are disabled in the selector and skipped as separate scan targets. The files are scanned once through the parent and reuse existing compression history, so previously optimized files remain unselected instead of being rediscovered as new work.

Adding a storage root starts an asynchronous scan immediately. Each storage root shows whether it has been scanned yet, updates that state when a scan completes, keeps its recent scan history, and can opt into automatic rescanning. The dashboard and sidebar process menu show scan progress, including scanned files, total files, and remaining files. Multiple selected roots can be scanned together with the Dashboard's Scan selected roots button; using a row-level Scan button makes that single root the active run target for the visible result set. Repeated scan requests reuse cached results immediately when the root has not changed; changed roots reuse cached metadata for unchanged files and probe only changed files again.

Running jobs can be cancelled from the dashboard or run details page. Cancellation stops further processing, clears tracked Tdarr queue state for unfinished files, removes unfinished candidates, and returns unfinished files to their normal analyzed state. Settings -> Tdarr encoding also has a force-clear button for tracked Tdarr queue state.

Failed or cancelled runs do not mark a file as already optimized unless the validated output was fully promoted and the original was successfully archived.

## Example Policy Folders

The repo includes generated media examples:

```text
runtime/examples/originals/camera-masters
runtime/examples/originals/review-proxies
runtime/examples/originals/client-deliverables
```

Use them to try exclusion, retention, output-mode, recursive, and extension-exclusion policies.

On startup the app seeds real example policies:

- `camera-masters`: excluded recursively.
- `review-proxies`: 14-day automatic deletion.
- `client-deliverables`: `mov,mxf` extension exclusions.
- Windows: `C:\System Volume Information` excluded recursively, or Linux: `/proc` excluded recursively, showing that system-level exclusions work anywhere under a scanned root.

The example storage root is resolved from the current workspace, so it is not tied to one Windows user profile or drive.

## Automation And Archive Restore

- The Automation tab can watch a storage root on an interval and start runs only for newly discovered files.
- Already optimized files remain unselected during later prechecks unless an operator manually checks them for recompression.
- The Archive tab is run-first: restore a whole run, or expand a run and restore selected files under its storage-root/folder tree.

## Verified Host Flow

The host-native flow has been exercised with copied sample media under `runtime/e2e`, including precheck, Tdarr optimization, validation, archive retention, restore, and manual deletion approval.

On May 18, 2026, the `E:\CLIPS` host flow was also verified with a custom optimized/archive target under `E:\archive-sentinel`, a Base Profile-only optimization selection, and a 10-file Tdarr batch submission that completed successfully.

## Documentation

- [instructions.md](./instructions.md): operator runbook
- [future_plans.md](./future_plans.md): completed implementation checklist and further optimization ideas
