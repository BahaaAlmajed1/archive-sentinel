# Archive Sentinel Completed Roadmap

This checklist tracks the requested future-plan implementation pass. Checked items were implemented in this workspace.

## 0. Rename

- [x] Rename this project to a proper project name: **Archive Sentinel**.

## 1. UI and UX Redesign

- [x] Replace the sparse dashboard with a modern operational interface using stronger hierarchy, richer color, clearer navigation, and drill-down links.
- [x] Keep the interface dense and suitable for large organizations instead of turning it into a marketing page.
- [x] Add visible progress bars for every run.
- [x] Improve the Settings layout, including a centered and aligned change-password form.
- [x] Add tooltips across Settings to explain options.
- [x] Add short descriptions for deletion mode, output mode, recursive policy behavior, and target type.
- [x] Add example policies with explanations.
- [x] Create test folders/files used by those policy examples under `runtime/examples/originals`.

## 2. Reports and Large-Scale Observability

- [x] Replace the archive-first file listing approach with report/log artifact links and paginated recovery surfaces.
- [x] Generate a dedicated HTML report for every optimization run.
- [x] Add a run-details page linked from the UI.
- [x] In each run HTML report, show a collapsed-by-default folder/file tree.
- [x] In each run HTML report, show clickable original/archive and optimized file links.
- [x] In each run HTML report, show old size, new size, and saved space.
- [x] In each run HTML report, show failures with included error messages.
- [x] Generate a dedicated precheck report for every precheck run.
- [x] Keep precheck details visible in the UI without dumping millions of rows into the dashboard.
- [x] Ensure large file inventories live in HTML reports and logs.
- [x] Upgrade the Reports tab so aggregate totals remain visible.
- [x] Link totals to filtered run tables where relevant.
- [x] Link each run row to its HTML report and log.
- [x] Show total historical savings.
- [x] Make final HTML reports modern and colorful instead of plain pasted text.

## 3. Host-Native Mode

- [x] Add a non-Docker execution mode.
- [x] Make host-native mode the default operating mode.
- [x] In host-native mode, work directly with system folders instead of Docker mount aliases.
- [x] Show system paths in the UI rather than container paths.
- [x] Preserve Docker Compose as an optional deployment path.
- [x] Include README setup guidance for host-machine PostgreSQL, API, UI, ffmpeg, and Tdarr.
- [x] Confirm in docs that restarts/reinstalls do not impact files/data when persistent folders and PostgreSQL data are retained.
- [x] Move the staged candidate into the optimized destination after validation instead of leaving staging with a promoted copy.

## 4. File and Folder Selection

- [x] Add OS-native folder and file picker endpoints.
- [x] Add Windows folder picker support with multi-select behavior through the native file dialog folder-selection pattern.
- [x] Add Linux picker support through `zenity`.
- [x] Use the same picker approach for storage roots.
- [x] Use the same picker approach for policy folders.
- [x] Use the same picker approach for policy files.
- [x] Add extension-exclusion policy controls.
- [x] Let precheck runs target a selected subset of configured roots.

## 5. Reporting Configuration

- [x] Add SMTP configuration controls to the UI.
- [x] Keep scheduled aggregate email delivery configurable by interval.
- [x] Add richer report links and delivery summaries through generated run/precheck artifacts.

## 6. Scalability and Data Presentation

- [x] Avoid rendering per-file archive listings directly in the main UI for large environments.
- [x] Add server-side pagination, filtering, and search endpoints for high-cardinality lists.
- [x] Add progress polling-friendly run fields and progress bars for more responsive run updates.
- [x] Add precheck and run summary tables designed around aggregate views first and drill-down second.

## 7. Codebase Maintainability

- [x] Split the frontend monolith in `frontend/src/App.tsx` into pages, reusable components, hooks, API client modules, and typed domain models.
- [x] Split backend service concentration into focused service files.
- [x] Split backend DTOs, controllers, models, and repositories by bounded responsibility.
- [x] Continue organizing backend code around bounded responsibilities.
- [x] Add broader automated tests around policy resolution.
- [x] Add automated coverage for report generation.
- [x] Add automated coverage for Tdarr integration boundaries.
- [x] Add an H2-backed Spring context test profile so validation gates, archive retention, restore behavior, and service wiring can be expanded safely.

## 8. Product Direction

- [x] Continue evolving from a Tdarr companion into a dedicated archive optimization platform for organizations with many terabytes of video.
- [x] Preserve the core product principle: storage savings are valuable only when the safety model remains stronger than the compression path.

## 9. Further Optimizations

- [x] Add these further optimization recommendations to the roadmap:

- [x] Introduce adaptive Tdarr queue sizing based on disk throughput, GPU utilization, and validation backlog so transcoding scales without starving safety checks.
- [x] Add per-codec/per-resolution policy presets to choose CPU, NVENC, QSV, or copy strategies without hand-editing Tdarr plugins.
- [x] Cache ffprobe metadata by content hash and mtime to speed repeated prechecks.
- [x] Add batched validation workers with backpressure so large runs keep disks sequential and avoid random I/O storms.
- [x] Add optional perceptual quality sampling such as VMAF/SSIM on configurable representative segments.
- [x] Track transcode speed, failure rate, size reduction, and validation cost per policy to recommend safer faster presets.
- [x] Add storage-root health checks for free space, write permission, filesystem type, and same-volume move behavior before a run starts.
- [x] Add resumable run queues so backend restarts continue pending work without resubmitting completed files.
- [x] Add report retention settings and compression for old HTML/log artifacts.
- [x] Add worker placement labels for multi-node Tdarr farms so hot storage roots can prefer nearby nodes.

## 10. Latest Implementation Pass

- [x] Move hardcoded precheck media extensions into Settings -> Precheck settings.
- [x] Add selectable default extension options with all current media extensions preselected.
- [x] Add configurable output container, duration tolerance, Tdarr codec exclusions, Tdarr transcode arguments, and media tool paths.
- [x] Auto-detect Tdarr-bundled ffmpeg/ffprobe when media tool paths are blank.
- [x] Resolve relative storage roots, examples, staging, optimized, archive, and report paths from the workspace so the app is portable across PCs and Linux layouts.
- [x] Replace the Policies textarea with an appended path list.
- [x] Add policy retention presets plus custom retention.
- [x] Replace prose examples with real seeded policies and generated example media.
- [x] Add duplicate-root handling so re-adding the same folder updates the existing root instead of throwing a database error.
- [x] Add required-field highlighting, immediate storage-root refresh, and editable/deletable storage roots.
- [x] Add stale-run recovery on backend startup.
- [x] Fix Tdarr Windows path normalization so cache files are library-relative and do not embed drive-letter paths.
- [x] Install and verify local Tdarr from `D:\Bahaa\Downloads\Tdarr_Updater.zip`.
- [x] Verify login no longer returns 403 from the Vite origin.
- [x] Verify host-mode precheck, optimization, reports, archive restore, and manual deletion approval through the UI.
- [x] Verify the full flow on two copied clips from `E:\CLIPS\Call of Duty  Modern Warfare 2 (2022)` without modifying the source folder.
- [x] Persist precheck item state so million-file inventories can use tree navigation and pagination without changing run semantics.
- [x] Group precheck candidates by storage root, keep paginated files on the right, and add bulk select/clear controls.
- [x] Track whether a file was optimized before and keep it unselected on later prechecks unless the operator manually requests recompression.
- [x] Add per-root effective-policy display and one-run retention/deletion/output overrides.
- [x] Add persistent per-root optimized/archive target-folder overrides and carry those targets into logs and reports.
- [x] Add Automation tab and scheduler for newly discovered files.
- [x] Replace file-open report links with host file-manager reveal links.
- [x] Make Archive run-first with whole-run restore plus paginated storage-root/folder-tree per-file restore.
- [x] Switch host-native Tdarr defaults to NVIDIA NVENC encoding.
- [x] Auto-switch compatible host-native Tdarr nodes from CPU transcode workers to GPU workers when GPU encoder arguments are configured.
- [x] Add run cancellation with Tdarr queue cleanup and restoration of unfinished files to their normal analyzed state.
- [x] Default Tdarr queue batch size to 10 and expose a force-clear tracked queue button in Settings.
- [x] Keep the active tab/run page after browser refresh and auto-refresh active run progress.
- [x] Group run reports by storage root with search and sortable file tables.
- [x] Rename one-run target controls to custom optimized results folder and custom archive folder.
- [x] Add folder pickers to one-run custom target controls.
- [x] Add Linux setup script for host-native or Docker Compose installation.
- [x] Add precheck running notification and progress indicator.
- [x] Prune nested selected storage roots so child roots are scanned and processed only once through a selected parent.
- [x] Reuse file compression history across parent/child root scans so already optimized files stay unselected.
- [x] Stop adding stale tracked optimized rows to precheck when the original file no longer exists under the scanned root.
- [x] Add settings visibility for max available threads and dynamic per-field thread limits.
- [x] Group precheck details by collapsed folder master/detail rows with folder-level selection.
- [x] Add asynchronous precheck progress with scanned/remaining file counts.
- [x] Add asynchronous storage-root inventory scans when roots are added.
- [x] Add root-scan progress visibility on the dashboard.
- [x] Cache media metadata by size and last-modified time so unchanged files skip repeat ffprobe work.
- [x] Add a 10/50/100 visible-directory selector for precheck folder groups, defaulting to 10.
- [x] Remove the separate Run precheck dashboard button and use scan actions as the async precheck entry point.
- [x] Add multi-root scanning through the selected-root set.
- [x] Add a global sidebar Processes menu for running scans and optimization jobs.
- [x] Fix storage-root action grid sizing after adding the Scan button.
- [x] Persist storage-root scanned/not-scanned state plus latest scan reference.
- [x] Reuse cached scan results immediately when selected roots have not changed.
- [x] Add per-root automatic rescanning toggle, disabled by default.
- [x] Add configurable background scan refresh interval, defaulting to 15 minutes.
- [x] Add filesystem-change tracking so scheduled rescans only run for roots with changes.
- [x] Add per-root scan history visibility and optimization gating on scanned roots.
- [x] Add dismissible completed-scan notification.
- [x] Fix scan-results folder pagination so directory pages, folder expansion, and search use matching directory data.
- [x] Update storage-root scanned state reliably when scan work completes.
- [x] Keep failed or cancelled runs from setting `ever_optimized` before the archive step fully commits.
- [x] Include failed/unselected precheck items and failure reasons in generated precheck artifacts.
- [x] Record run-log failure reasons clearly and avoid post-archive source-path failures when computing saved bytes.
- [x] Submit real Tdarr scan batches of up to the configured queue size instead of one-file scan requests.
- [x] Verify `E:\CLIPS` with a custom `E:\archive-sentinel` target, Base Profile-only selection, and a completed 10-file Tdarr batch run.
