# Archive Sentinel

Archive Sentinel is a safety-first video archive optimization app built around Tdarr. It reduces storage use by generating optimized replacements, validating them, promoting them, and only then moving originals into an archive location.

The main safety rule is simple: Archive Sentinel does not move an original video out of its live location until the replacement has passed validation and has been promoted successfully.

## What Is Included

- Kotlin and Spring Boot backend with PostgreSQL persistence.
- React and Vite frontend.
- Host-native mode for running against normal Windows or Linux paths.
- Optional Docker Compose mode for running PostgreSQL, API, UI, Tdarr server, and Tdarr node together.
- First-run setup, editable Settings, storage roots, policies, precheck scans, optimization runs, reports, automation, and archive restore.
- OS-native path picker support when the backend runs interactively on the host: Windows uses a native picker, Linux uses `zenity` when available.
- Tdarr-backed candidate generation, validation, staged promotion, archive retention, restore, manual approval, and automatic deletion.

## How To Use This README

Read this file from top to bottom the first time you set up the app.

1. Choose the setup path for your host.
2. Install prerequisites for that path.
3. Start Archive Sentinel.
4. Complete first-run Settings.
5. Add storage roots and run a precheck before optimizing anything.

## Choose A Setup Path

Use host-native mode if you want Archive Sentinel to see and display the same file paths that you use on your machine.

Use Docker Compose if you want the whole stack isolated in containers, including a managed Tdarr server and node.

| Host OS | Recommended path | Notes |
| --- | --- | --- |
| Linux | `scripts/setup-linux.sh host` | Installs common dependencies and starts backend/frontend processes. |
| Linux with containers | `scripts/setup-linux.sh docker` | Starts the full Docker Compose stack. |
| Windows | Host-native manual setup | Best for normal drive paths such as `E:\Videos`. |
| Any OS with Docker | Docker Compose | Requires Docker and the Docker Compose plugin. |

## Default URLs And Login

Host-native mode:

- UI: `http://localhost:5173`
- API: `http://localhost:8080`
- Existing Tdarr server: `http://localhost:8266`

Docker Compose mode:

- UI: `http://localhost:3000`
- API: `http://localhost:8080`
- Managed Tdarr UI: `http://localhost:8265`

Default Archive Sentinel login:

```text
username: admin
password: admin
```

Change the password from Settings after your first login.

## Host-Native Setup On Linux

For a new Linux host-native install, start here. From the repository root:

```bash
bash scripts/setup-linux.sh host
```

The script installs common host packages with `apt`, `dnf`, `yum`, or `pacman`: Java 21, Node.js/npm, FFmpeg, Zenity, curl, and unzip. It only installs PostgreSQL packages if you choose the local PostgreSQL option.

The script also walks you through the two external services Archive Sentinel needs:

1. PostgreSQL:
   Choose `install` to install/start local PostgreSQL and create the `archive_sentinel` database/user, or choose `existing` to point Archive Sentinel at a PostgreSQL database you already manage.
2. Tdarr:
   Choose `existing` to enter the URL of an already-running Tdarr server, or choose `install` to download the native Tdarr updater, run it, and optionally start a local Tdarr server and node.

When the script finishes, it writes `.env.local`, builds the backend and frontend, and starts both processes in the background. Logs are written under:

```text
runtime/logs
```

Open `http://localhost:5173` and sign in with the default login.

### Existing Tdarr On Linux

If you already have Tdarr running on this machine or another machine, choose `existing` when prompted and enter the server URL, for example:

```text
http://localhost:8266
http://192.168.1.50:8266
```

The script writes that value to `.env.local` as `APP_TDARR_BASE_URL`, and the app uses it as:

```text
Tdarr mode: EXISTING
Tdarr URL: http://192.168.1.50:8266
```

The Tdarr server and node must be able to access the same media paths that Archive Sentinel sends to Tdarr. If Tdarr runs on a different machine or OS, configure Tdarr path translators so the server and node agree on media and transcode-cache locations.

You can skip the prompt with:

```bash
TDARR_SETUP=existing TDARR_URL=http://192.168.1.50:8266 bash scripts/setup-linux.sh host
```

### Installing Tdarr From The Linux Script

If you choose `install`, the script asks for:

- The Tdarr install directory, defaulting to `runtime/tools/tdarr`.
- The Tdarr updater download URL, defaulting to the Linux updater URL from the official Tdarr native install docs.
- The Tdarr server URL Archive Sentinel should use, defaulting to `http://localhost:8266`.
- Whether to start the Tdarr server and node after installation.

If Tdarr changes the native package URL, paste the current `linux_x64` or `linux_arm64` updater link from the official Tdarr native install docs when the script asks for the download URL.

The script downloads and unzips the updater, runs `Tdarr_Updater`, then starts:

```text
Tdarr_Server/Tdarr_Server
Tdarr_Node/Tdarr_Node
```

Tdarr logs are written to:

```text
runtime/logs/tdarr-server.log
runtime/logs/tdarr-node.log
```

You can run the install path without prompts:

```bash
TDARR_SETUP=install TDARR_INSTALL_DIR=/opt/tdarr TDARR_URL=http://localhost:8266 bash scripts/setup-linux.sh host
```

### Existing PostgreSQL On Linux

If you already have PostgreSQL, choose `existing` when prompted. The script asks for:

- PostgreSQL JDBC URL, for example `jdbc:postgresql://db.example.test:5432/archive_sentinel`
- PostgreSQL username
- PostgreSQL password

The database must already exist and the user must be able to create/update Archive Sentinel tables through Flyway migrations.

You can skip the prompt with:

```bash
POSTGRES_SETUP=existing \
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.test:5432/archive_sentinel \
DB_USER=archive_sentinel \
DB_PASSWORD=archive_sentinel \
bash scripts/setup-linux.sh host
```

### Linux Override Reference

Every prompt can be prefilled with environment variables:

```bash
API_PORT=8080
UI_PORT=5173

POSTGRES_SETUP=install
POSTGRES_SETUP=existing
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/archive_sentinel
DB_HOST=localhost
DB_PORT=5432
DB_NAME=archive_sentinel
DB_USER=archive_sentinel
DB_PASSWORD=archive_sentinel

TDARR_SETUP=existing
TDARR_SETUP=install
TDARR_URL=http://localhost:8266
TDARR_INSTALL_DIR=/opt/tdarr
TDARR_DOWNLOAD_URL=https://storage.tdarr.io/versions/2.17.01/linux_x64/Tdarr_Updater.zip
START_TDARR=yes
```

For example, this uses an existing PostgreSQL database and an existing Tdarr server:

```bash
POSTGRES_SETUP=existing \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/archive_sentinel \
DB_USER=archive_sentinel \
DB_PASSWORD=archive_sentinel \
TDARR_SETUP=existing \
TDARR_URL=http://localhost:8266 \
bash scripts/setup-linux.sh host
```

If the script is run without an interactive terminal, it defaults to installing local PostgreSQL and using an existing Tdarr server at `http://localhost:8266`.

## Host-Native Setup On Windows

### 1. Install Prerequisites

Install these tools first:

| Prerequisite | Install link | Value to keep |
| --- | --- | --- |
| Java 21 JDK | [Eclipse Temurin JDK 21](https://adoptium.net/temurin/releases?version=21) or `winget install EclipseAdoptium.Temurin.21.JDK` | None |
| Node.js | [Node.js downloads](https://nodejs.org/en/download) | None |
| PostgreSQL | [PostgreSQL downloads](https://www.postgresql.org/download/) | The `postgres` admin password you choose |
| Tdarr | [Tdarr native install guide](https://docs.tdarr.io/docs/installation/windows-linux-macos/) | The Tdarr server URL |
| FFmpeg and FFprobe | [FFmpeg downloads](https://ffmpeg.org/download.html) | None |

If you install Tdarr with the native updater layout, Archive Sentinel can auto-detect Tdarr's bundled `ffmpeg` and `ffprobe`, so a separate FFmpeg install is optional.

### 2. Create The PostgreSQL Database

Open PowerShell. If PostgreSQL's `bin` directory is not on `PATH`, run these commands with the full path to `createuser`, `createdb`, and `psql` from your PostgreSQL install directory.

```powershell
createuser -U postgres archive_sentinel
createdb -U postgres -O archive_sentinel archive_sentinel
psql -U postgres -d archive_sentinel -c "alter user archive_sentinel with password 'archive_sentinel'; grant all privileges on database archive_sentinel to archive_sentinel;"
```

The default app database credentials are:

```text
database: archive_sentinel
username: archive_sentinel
password: archive_sentinel
```

### 3. Start Tdarr

For a native Tdarr install, download the Tdarr updater, place it in its own folder, run the updater, then start both the Tdarr server and a Tdarr node.

Example Windows layout:

```powershell
Expand-Archive D:\Bahaa\Downloads\Tdarr_Updater.zip runtime\tools\tdarr\updater
runtime\tools\tdarr\updater\Tdarr_Updater.exe
runtime\tools\tdarr\updater\Tdarr_Server\Tdarr_Server.exe
runtime\tools\tdarr\updater\Tdarr_Node\Tdarr_Node.exe
```

If Windows shows a UAC or firewall prompt, allow it once. Confirm Tdarr answers at:

```text
http://localhost:8266/api/v2/status
```

Keep the Tdarr URL handy. After you start Archive Sentinel and open Settings, use:

```text
Tdarr mode: EXISTING
Tdarr URL: http://localhost:8266
```

Host-native defaults use NVIDIA NVENC:

```text
,-map 0 -map_metadata 0 -map_chapters 0 -c:v hevc_nvenc -preset p5 -cq 28 -c:a copy -c:s copy
```

If your host does not have a supported NVIDIA GPU, change the Tdarr transcode arguments in Settings before running production jobs. GPU arguments need a Tdarr GPU transcode worker; Archive Sentinel can switch compatible host-native nodes from CPU workers to a GPU worker when a GPU encoder such as `hevc_nvenc` is configured.

### 4. Start Archive Sentinel

From the repository root, start the backend:

```powershell
cd backend
.\gradlew.bat bootRun
```

Open a second PowerShell window from the repository root and start the UI:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` and sign in with the default login.

If the backend is running on a temporary non-default port, set `VITE_API_PROXY_TARGET` before starting Vite:

```powershell
$env:VITE_API_PROXY_TARGET="http://localhost:8081"
cd frontend
npm run dev
```

## Docker Compose Setup

Use Docker Compose if you want PostgreSQL, API, UI, Tdarr server, and Tdarr node managed together.

From the repository root:

```powershell
docker compose up -d --build
```

On Linux, you can use the helper instead:

```bash
bash scripts/setup-linux.sh docker
```

Open:

- UI: `http://localhost:3000`
- API: `http://localhost:8080`
- managed Tdarr UI: `http://localhost:8265`

Docker maps container paths to host folders:

```text
/data/originals  -> ./runtime/originals
/data/optimized  -> ./runtime/optimized
/data/archive    -> ./runtime/archive
/data/staging    -> ./runtime/staging
```

To use different host folders, copy `.env.example` to `.env` and edit the paths:

```dotenv
ORIGINALS_HOST_PATH=E:\Videos
OPTIMIZED_HOST_PATH=E:\ArchiveSentinel\optimized
ARCHIVE_HOST_PATH=E:\ArchiveSentinel\archive
STAGING_HOST_PATH=E:\ArchiveSentinel\staging
```

On Linux, use Linux paths instead:

```dotenv
ORIGINALS_HOST_PATH=/mnt/media/originals
OPTIMIZED_HOST_PATH=/mnt/media/optimized
ARCHIVE_HOST_PATH=/mnt/media/archive
STAGING_HOST_PATH=/mnt/media/staging
```

## First-Run Settings

After logging in, open Settings and confirm the operating settings before adding production storage roots.

Recommended host-native defaults:

```text
execution mode: HOST_NATIVE
tdarr mode: EXISTING
tdarr URL: http://localhost:8266
staging enabled: true
retention days: 90
deletion mode: MANUAL
output mode: PARALLEL
```

Default host-native folders are relative to the repository:

```text
runtime/archive
runtime/optimized
runtime/staging
```

Add your real media folders as storage roots from the dashboard. Each storage root can optionally override its optimized-output and archive destinations. When no override is set, parallel mode writes under the global optimized/archive roots plus a namespace based on the storage root path.

Settings also includes:

- SMTP settings for scheduled aggregate emails.
- Enabled video extensions and additional extension options.
- Estimated optimized size percentage.
- Output container extension.
- Duration tolerance.
- Tdarr codecs to skip.
- Tdarr ffmpeg transcode arguments.
- Tdarr queue batch size, defaulting to `10`.
- ffmpeg and ffprobe paths. Blank means auto-detect bundled Tdarr tools or fall back to `PATH`.
- Background scan interval, defaulting to `15` minutes.

## Storage Roots

Storage roots are folders that Archive Sentinel scans during precheck.

Host-native examples:

```text
E:\Videos
D:\ArchiveSentinel\incoming
/mnt/media/archive
```

Docker example:

```text
/data/originals
```

Use the folder picker button when the backend is running interactively on the host. On Windows it opens a native Windows picker. On Linux it uses `zenity` when available.

Relative storage roots are resolved from the project workspace, so paths like `runtime/e2e/cod-clips-20260518/originals` can move with the repository.

Each storage root can show scan state, recent scan history, effective policy, one-run overrides, custom optimized and archive folders, and whether automatic rescanning is enabled.

If a selected storage root is a parent of another selected storage root, Archive Sentinel disables the child checkbox and scans those files only through the parent. This avoids duplicate scanning and duplicate processing.

Deleting a root is only allowed before Archive Sentinel has tracked files under it. Once history exists, disable the root instead so reports, archive restore references, and compression history remain intact.

## Policies

Policies can target files or folders.

| Control | Meaning |
| --- | --- |
| Target type | `FILE` matches one file; `FOLDER` matches files under the folder. |
| Excluded | Skips matching files. |
| Recursive | Includes descendants for folder policies. |
| Extension exclusions | Skips comma-separated extensions under the policy. |
| Retention days | Overrides default retention. |
| Deletion mode | Uses `MANUAL` approval or `AUTOMATIC` deletion after retention. |
| Output mode | Writes to `PARALLEL` optimized root or `SAME_LIBRARY` next to original. |

The policy form stores paths as a list. Each manual add or picker selection appends to the list, and submitting creates one policy per selected path.

Example policy folders are included under:

```text
runtime/examples/originals/camera-masters
runtime/examples/originals/review-proxies
runtime/examples/originals/client-deliverables
```

Seeded example policies:

- `camera-masters`: excluded recursively.
- `review-proxies`: 14-day automatic deletion.
- `client-deliverables`: `mov,mxf` extension exclusions.
- Windows: `C:\System Volume Information` excluded recursively.
- Linux: `/proc` excluded recursively.

## Precheck Workflow

Always run precheck before starting an optimization run.

1. Open Dashboard.
2. Select one or more enabled storage roots.
3. Run precheck.
4. Review aggregate counts, storage roots, collapsed folder groups, and report/log links.
5. Leave never-compressed files selected, keep already optimized files visible but unselected, or manually select files you want to recompress.
6. Use `Select all` or `Clear all` only when you want a deliberate bulk override.

The full inventory is written to the generated HTML report and log. The UI pages through file rows grouped by folder, but selection state is stored on the server so pagination does not change what the optimization run will process.

Large repeated scans are faster because unchanged files reuse cached metadata when size and last-modified time match the previous scan. Background rescanning is opt-in per storage root and disabled by default.

## Optimization Workflow

Each selected file follows this workflow:

1. Tdarr generates a candidate in staging or `.candidates`.
2. Candidate validation runs.
3. The candidate is moved to the optimized destination.
4. Final validation runs on the promoted file.
5. Candidate and final checksum are compared.
6. The original is moved into the archive folder.
7. Retention policy controls later deletion.

When a policy has `0` retention days and `MANUAL` deletion mode, archived originals appear immediately under Archive -> Pending deletion approvals.

Running jobs can be cancelled from the dashboard or run details page. Cancellation stops further processing, clears tracked Tdarr queue state, removes unfinished candidates, and returns unfinished files to their normal analyzed state.

Files are only marked as previously optimized after the validated replacement has been promoted and the original has been archived successfully. A failed or cancelled run remains eligible for normal retry selection.

## Validation Gates

A file must pass all of these checks before the original is archived:

1. Candidate exists.
2. Video stream count matches.
3. Audio stream count matches.
4. Subtitle stream count matches.
5. Stream topology matches.
6. Chapter count matches.
7. Critical metadata is preserved.
8. Duration difference is no greater than `0.5` seconds.
9. Candidate is smaller than the original.
10. Video and audio decode without ffmpeg errors.
11. Promoted file checksum matches the staged candidate checksum.

If any check fails, the file is marked `FAILED`, the original remains in place, and the failure appears in reports and logs.

## Reports

Each precheck and optimization run produces:

- A modern HTML report with collapsed folder/file sections.
- A plain-text log.
- Links that ask the backend to reveal files in the host file manager instead of trying to open media in the browser.
- Old size, new size, saved space, status, target roots, and failure messages.

The Reports tab shows aggregate totals, historical savings, filtered run tables, per-run artifacts, and precheck artifacts.

## Automation

Use the Automation tab for roots that receive new files regularly. Each rule runs precheck on its interval and starts optimization only for files that have never been optimized before. Files already compressed stay unselected unless an operator manually selects them for recompression.

## Archive Restore

The Archive tab is run-first for scale. Restore a whole run, or expand a run to browse its storage-root/folder tree and restore individual retained files. Restoring one file removes it from the run's recoverable set.

## Restart And Reinstall Safety

Restarting the backend, UI, Tdarr, PostgreSQL, or Docker Compose does not delete media files. Archive Sentinel stores operational state in PostgreSQL and writes media to configured system folders.

A reinstall is safe for files as long as you do not manually delete:

- Original, optimized, archive, or staging folders.
- PostgreSQL data.
- The Docker `postgres_data` volume when using Docker Compose.

For production, keep PostgreSQL backups and put archive, optimized, staging, and originals folders outside disposable build directories.

## Verification Commands

Backend tests:

```powershell
cd backend
.\gradlew.bat test
```

Frontend build:

```powershell
cd frontend
npm run build
```

Frontend lint:

```powershell
cd frontend
npm run lint
```

On Linux or macOS shells, use `./gradlew` instead of `.\gradlew.bat`:

```bash
cd backend
./gradlew test
```

## Additional Documentation

- [future_plans.md](./future_plans.md): completed implementation checklist and further optimization ideas.
