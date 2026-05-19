# Archive Sentinel Instructions

This runbook describes the application after the roadmap implementation pass.

## 1. Operating Modes

Archive Sentinel defaults to host-native mode. In this mode the backend, UI, PostgreSQL, ffmpeg/ffprobe, and Tdarr run directly on the host machine, and the UI shows the same system paths the operator sees.

Docker Compose is still supported as an optional deployment mode. In Docker mode the API uses `/data/...` container paths that are mounted to host folders.

## 2. Host-Native Setup

Install prerequisites:

| Prerequisite | Install link | Keep this value |
| --- | --- | --- |
| Java 21 JDK | [Temurin JDK 21](https://adoptium.net/temurin/releases?version=21) | none |
| Node.js | [Node.js downloads](https://nodejs.org/en/download) | none |
| PostgreSQL | [Downloads](https://www.postgresql.org/download/) | the `postgres` admin password |
| Tdarr | [Native install guide](https://docs.tdarr.io/docs/installation/windows-linux-macos/) | the server URL, normally `http://localhost:8266` |
| FFmpeg / FFprobe | [FFmpeg downloads](https://ffmpeg.org/download.html) | none |

### Linux assisted setup

From a fresh Linux server:

```bash
sudo apt update
sudo apt install -y git
git clone https://github.com/BahaaAlmajed1/archive-sentinel.git
cd archive-sentinel
```

Then run the one-command host setup from the repository root:

```bash
bash scripts/setup-linux.sh host
```

The host-native script installs Java 21, Node.js 22/npm, PostgreSQL, FFmpeg, Zenity, and curl using `apt`, `dnf`, `yum`, or `pacman` when available. It starts PostgreSQL, creates the `archive_sentinel` database and user, writes `.env.local`, builds the backend and frontend, then starts both processes in the background. Logs are written under `runtime/logs`.

Useful environment overrides:

```bash
API_PORT=8080 UI_PORT=5173 TDARR_URL=http://localhost:8266 bash scripts/setup-linux.sh host
DB_NAME=archive_sentinel DB_USER=archive_sentinel DB_PASSWORD=archive_sentinel bash scripts/setup-linux.sh host
```

Host-native mode can use an existing Tdarr server and at least one existing Tdarr node:

```bash
bash scripts/setup-linux.sh host --tdarr-url http://localhost:8266
curl http://localhost:8266/api/v2/status
```

If you do not already have Tdarr, let host setup create managed Tdarr through Docker:

```bash
bash scripts/setup-linux.sh host --managed-tdarr
```

Managed Tdarr defaults to GPU workers and zero CPU workers when a GPU is detected. Override the counts if you want a different balance:

```bash
TDARR_GPU_WORKERS=2 TDARR_CPU_WORKERS=0 bash scripts/setup-linux.sh host --managed-tdarr
```

If PostgreSQL already exists, use it instead of creating a local database:

```bash
bash scripts/setup-linux.sh host \
  --postgres-url jdbc:postgresql://db-host:5432/archive_sentinel \
  --postgres-user archive_sentinel \
  --postgres-password archive_sentinel \
  --tdarr-url http://tdarr-host:8266
```

If prerequisites are already installed and you do not want the script to install packages, add `--skip-packages`.

Managed Tdarr gives the node NVIDIA `--gpus=all` access when `nvidia-smi` is available and maps `/dev/dri` when present for VAAPI/QSV-class devices. GPU hosts start with Tdarr GPU transcode and health-check workers and zero CPU workers; CPU-only hosts start with one CPU transcode and health-check worker.

The default host-native transcode arguments target NVIDIA NVENC. On a CPU-only Linux server, set Settings -> Tdarr encoding to HEVC CPU arguments before starting production work and leave `Codecs to skip` as `hevc`:

```text
,-map 0 -map_metadata 0 -map_chapters 0 -c:v libx265 -preset ultrafast -crf 32 -c:a aac -b:a 96k -c:s copy
```

If Tdarr is installed natively with the updater, Archive Sentinel can auto-detect Tdarr's bundled ffmpeg and ffprobe binaries, so separate FFmpeg installation is optional.

Create the PostgreSQL database. If the PostgreSQL CLI tools are not on `PATH`, call them by full path from the PostgreSQL install directory:

```powershell
createuser -U postgres archive_sentinel
createdb -U postgres -O archive_sentinel archive_sentinel
psql -U postgres -d archive_sentinel -c "alter user archive_sentinel with password 'archive_sentinel'; grant all privileges on database archive_sentinel to archive_sentinel;"
```

Start Tdarr on the host and confirm the API is reachable at `http://localhost:8266`.

For a local Tdarr updater install:

```powershell
Expand-Archive D:\Bahaa\Downloads\Tdarr_Updater.zip runtime\tools\tdarr\updater
runtime\tools\tdarr\updater\Tdarr_Updater.exe
runtime\tools\tdarr\updater\Tdarr_Server\Tdarr_Server.exe
runtime\tools\tdarr\updater\Tdarr_Node\Tdarr_Node.exe
```

If Windows shows a UAC or firewall prompt, allow it once. The node should point to `http://127.0.0.1:8266`; the app status endpoint should report Tdarr `good`. Host-native defaults now use `hevc_nvenc` so a supported NVIDIA GPU is used instead of CPU encoding. GPU arguments need a Tdarr GPU transcode worker; Archive Sentinel now converts compatible host-native nodes from CPU workers to a GPU worker automatically when a GPU encoder is configured.

In Archive Sentinel Settings use:

```text
Tdarr mode: EXISTING
Tdarr URL: http://localhost:8266
```

Start the backend:

```powershell
cd backend
.\gradlew.bat bootRun
```

Start the frontend:

```powershell
cd frontend
npm install
npm run dev
```

For a backend on a temporary non-default port, set `VITE_API_PROXY_TARGET` before `npm run dev`.

Open `http://localhost:5173`.

Default login:

```text
username: admin
password: admin
```

Change the password from Settings.

## 3. Docker Compose Setup

From the repository root:

```powershell
docker compose up -d --build
```

On Linux, the one-step Docker path is:

```bash
bash scripts/setup-linux.sh docker
```

Open:

- UI: `http://localhost:3000`
- API: `http://localhost:8080`
- managed Tdarr UI: `http://localhost:8265`

Docker path mappings:

```text
/data/originals  -> ./runtime/originals
/data/optimized  -> ./runtime/optimized
/data/archive    -> ./runtime/archive
/data/staging    -> ./runtime/staging
```

Override host folders in `.env`:

```dotenv
ORIGINALS_HOST_PATH=E:\Videos
OPTIMIZED_HOST_PATH=E:\ArchiveSentinel\optimized
ARCHIVE_HOST_PATH=E:\ArchiveSentinel\archive
STAGING_HOST_PATH=E:\ArchiveSentinel\staging
```

## 4. Restart And Reinstall Expectations

Restarts do not delete media files or database state.

Files and data are not impacted by restart or reinstall when:

- originals, optimized files, archive files, and staging are stored in persistent folders
- PostgreSQL data is retained or restored from backup
- Docker users keep the `postgres_data` volume and mounted runtime folders

Archive Sentinel only moves originals into the archive after validation passes. The staged candidate is moved into the optimized destination after validation, so staging does not keep a second promoted copy.

## 5. First-Run Settings

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

SMTP settings are editable in Settings:

- host
- port
- username
- password
- auth
- STARTTLS
- from address
- recipients
- report interval hours

Precheck and media settings are editable in Settings:

- enabled extensions
- additional extension options
- estimated optimized size percent
- output container extension
- duration tolerance
- Tdarr codecs to skip
- Tdarr ffmpeg transcode arguments
- Tdarr queue batch size, defaulting to `10`, which controls how many files Archive Sentinel submits to Tdarr in one scan batch while Tdarr manages its own worker queue
- ffmpeg and ffprobe paths, with blank meaning auto-detect bundled Tdarr tools or fall back to `PATH`

## 6. Storage Roots

Storage roots are folders scanned by precheck.

Host-native examples:

```text
E:\Videos
D:\ArchiveSentinel\incoming
/mnt/media/archive
```

Docker examples:

```text
/data/originals
```

Use the folder picker button to browse the server filesystem from the web UI. If the backend runs on Linux and the browser runs on Windows, the picker still lists Linux server paths such as `/home`, `/mnt`, and `/media`, not Windows client folders.

Relative storage roots are resolved from the project workspace, so paths like `runtime/e2e/cod-clips-20260518/originals` work across machines when the repo folder moves.

Each storage-root row also shows whether it has been scanned, updates that state as scans complete, whether new filesystem changes were detected, the effective active policy, and one-run overrides for retention, deletion mode, output mode, custom optimized results folder, and custom archive folder. The detail row can also persist custom optimized results and custom archive folders for that root, enable automatic rescanning for that specific root, and show the scans that included it. If no target override is set, parallel output preserves the storage-root namespace below the global optimized/archive folders instead of flattening every root into one directory.

If a selected storage root is a parent of another selected storage root, Archive Sentinel disables the child checkbox and scans the child files only through the parent. This avoids duplicate scanning and duplicate processing. Existing compression history follows the file path, so a file previously optimized under a child root is still unselected when later seen through the parent root.

Scan results are grouped by directory in a collapsed master/detail view. The directory selector defaults to 10 visible folders, with 50 and 100 folder options. Folder pagination moves through directory groups, search rebuilds the matching directory list, and expanding a folder fetches the matching files for that folder only.

The row-level `Scan` action focuses the active selection on that one storage root, so the visible scan result can be started directly. `Scan selected roots` keeps the current multi-root selection.

Deleting a root is only allowed before Archive Sentinel has tracked files under it. Once history exists, disable the root instead; this preserves reports, archive restore references, and compression history.

## 7. Policies

Policies can target a file or folder.

Policy controls:

| Control | Meaning |
| --- | --- |
| Target type | `FILE` matches one file; `FOLDER` matches files under the folder |
| Excluded | skips matching files |
| Recursive | folder policy includes descendants when enabled |
| Extension exclusions | comma-separated extensions skipped under the policy |
| Retention days | overrides default retention |
| Deletion mode | `MANUAL` approval or `AUTOMATIC` after retention |
| Output mode | `PARALLEL` optimized root or `SAME_LIBRARY` next to original |

The policy form stores paths as a list. Each manual add or picker selection appends to the list, and submitting creates one policy per selected path. Retention has common presets plus a custom value.

Example policy folders/files are included under:

```text
runtime/examples/originals/camera-masters
runtime/examples/originals/review-proxies
runtime/examples/originals/client-deliverables
```

Seeded example policies show real rows in the Policies table instead of prose-only examples.

System-level exclusions work the same way as library-local exclusions. The seeded examples include `C:\System Volume Information` on Windows or `/proc` on Linux so a root such as a whole drive can skip operating-system folders recursively.

## 8. Precheck

From Dashboard:

1. Select one or more enabled storage roots.
2. Run precheck.
3. Review aggregate counts, storage roots on the left, collapsed folder groups on the right, and report/log links.
4. Leave never-compressed files selected, keep already optimized files visible but unselected, or manually check files you want to recompress.
5. Use `Select all` or `Clear all` when you need a deliberate bulk override.

The full inventory is written to the generated HTML report and log. The UI pages through file rows grouped by folder, but selection state is stored on the server so pagination never changes what the optimization run will process. Folder-level checkboxes select or clear all matching files in that folder. The directory view defaults to 10 visible folders and can be changed to 50 or 100.

While a scan is running, the dashboard shows a running notification, progress percentage, scanned-file count, and remaining-file count. The sidebar Processes button shows running scans and optimization jobs from every page. Large repeated scans are faster because unchanged files reuse cached metadata when size and last-modified time match the previous scan. Scan threads still control concurrent metadata probes for new or changed files.

Adding a storage root starts a background scan. Existing roots can be rescanned with the root row's Scan button, and multiple selected roots can be scanned together with Scan selected roots. If a selected root has already been scanned and no new filesystem changes were recorded, Archive Sentinel shows cached results immediately instead of walking the library again.

Background rescanning is opt-in per storage root and disabled by default. Settings -> Background scans controls the refresh interval, defaulting to `15` minutes. Only roots with automatic rescanning enabled and recorded filesystem changes are eligible for scheduled rescans.

## 9. Optimization Runs

Each selected file follows this workflow:

1. Tdarr generates a candidate in staging or `.candidates`.
2. Candidate validation runs.
3. The candidate is moved to the optimized destination.
4. Final validation runs on the promoted file.
5. Candidate and final checksum are compared.
6. The original is moved into the archive folder.
7. Retention policy controls later deletion.

When a policy has `0` retention days and `MANUAL` deletion mode, archived originals appear immediately under Archive -> Pending deletion approvals.

Every run row shows a progress bar, completed-file counts, and saved storage. Open a run to view auto-refreshing paginated file details grouped by storage root, or use report/log links for the full artifact. Active runs can be cancelled from the dashboard or run details page; cancellation clears tracked Tdarr queue state and unfinished files return to `ANALYZED` after confirmation.

Files are only marked as previously optimized after the validated replacement has been promoted and the original has been archived successfully. A failed or cancelled run remains eligible for normal retry selection.

## 10. Reports

Reports tab shows:

- aggregate totals
- total historical savings
- filtered run tables linked from aggregate totals
- per-run HTML and log artifacts
- precheck HTML and log artifacts

HTML reports include storage-root groups, collapsed folder/file groups, search, sortable columns, original/archive links, optimized links, old size, new size, saved space, status, target roots, and failure messages.

Precheck reports/logs include the full scanned inventory, not only currently selected files, so probe failures and other unselected errors remain visible in the artifacts.

Report file links reveal the path in the host file manager instead of trying to stream or open the media file in the browser.

## 11. Automation

Use the Automation tab for roots that receive new files regularly. Each rule runs precheck on its interval and starts optimization only for files that have never been optimized before. Files already compressed stay unselected unless an operator manually selects them for recompression.

## 12. Archive Restore

Archive is run-first for scale. Restore a whole run, or expand a run to browse its storage-root/folder tree and restore individual retained files. Restoring one file removes it from the run's recoverable set.

## 13. Validation Gates

A file must pass all of these before the original is archived:

1. Candidate exists.
2. Video, audio, and subtitle stream counts match.
3. Stream topology matches.
4. Chapter count matches.
5. Critical metadata is preserved.
6. Duration difference is no greater than `0.5` seconds.
7. Candidate is smaller than the original.
8. Video and audio decode without ffmpeg errors.
9. Final promoted file matches the staged candidate checksum.

If any check fails, the file is marked `FAILED`, the original remains in place, and the failure appears in reports/logs.

## 14. Verification Commands

Backend:

```powershell
cd backend
.\gradlew.bat test
```

Frontend:

```powershell
cd frontend
npm run build
```

Also run:

```powershell
cd frontend
npm run lint
```
