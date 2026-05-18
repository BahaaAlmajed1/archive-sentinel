# Archive Sentinel UI

React + TypeScript + Vite frontend for Archive Sentinel.

Useful commands:

```powershell
npm install
npm run dev
npm run build
npm run lint
```

The UI is organized into:

- `src/api`: API client helpers
- `src/components`: reusable operational UI components
- `src/domain`: shared TypeScript models and defaults
- `src/hooks`: data-loading hooks
- `src/pages`: dashboard, archive, policies, reports, run details, login, and settings pages

Recent UI behavior:

- Settings includes Precheck settings, Output and validation, Tdarr encoding, Media tools, SMTP, and worker sections.
- Policies use an appended path list, picker buttons, retention presets/custom value, and one row per actual policy.
- Dashboard scan actions target selected enabled roots asynchronously, prune selected child roots under selected parents, show progress, show collapsed folder master/detail groups with a 10/50/100 directory selector, and start optimization from server-side persisted selections.
- Storage roots start background scans when added, show scanned/not-scanned state, can reveal their scan history, and expose opt-in automatic rescanning. Progress is shown on the dashboard and global Processes menu.
- Dashboard root details include persistent custom optimized/archive folders, one-run custom target folders with native pickers, and active run cancellation.
- Settings exposes the Tdarr queue batch size, defaulting to 10, max available thread budget, dynamic thread limits, plus a force-clear tracked Tdarr queue action. The backend now submits actual Tdarr scan batches up to that size.
- Run details auto-refresh active runs and group paginated files by storage root.
- Archive exposes pending manual deletion approvals separately and restores retained originals by run or individual file under a storage-root/folder tree.
- Automation manages recurring root scans that process only newly discovered files.
