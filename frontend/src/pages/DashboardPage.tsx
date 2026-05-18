import { useEffect, useMemo, useState } from 'react'
import type { Dispatch, FormEvent, KeyboardEvent, SetStateAction } from 'react'
import { Archive, ChevronDown, CircleCheck, Gauge, HardDrive, Play, RefreshCw, Search, ShieldCheck, Square, Trash2 } from 'lucide-react'
import { api, type ApiHeaders } from '../api/client'
import { ArtifactLinks } from '../components/ArtifactLinks'
import { MetricCard } from '../components/MetricCard'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { PathPickerButton } from '../components/PathPickerButton'
import { ProgressBar } from '../components/ProgressBar'
import { RunMeta } from '../components/RunMeta'
import { bytes, gigabytes } from '../domain/defaults'
import type { MonitoringDto, PrecheckDto, PrecheckRunDto, RootDto, RunDto, StorageRootScanProgressDto } from '../domain/types'

type RootOverride = { retentionDays: string; deletionMode: string; outputMode: string; optimizedRootOverride: string; archiveRootOverride: string }
type RootDraft = Pick<RootDto, 'label' | 'path' | 'optimizedRootOverride' | 'archiveRootOverride'>

export function DashboardPage({
  headers,
  roots,
  setRoots,
  monitoring,
  precheck,
  setPrecheck,
  runs,
  rootScans,
  setRootScans,
  runningPrechecks,
  refresh,
  setMessage,
  onOpenRun,
}: {
  headers: ApiHeaders
  roots: RootDto[]
  setRoots: Dispatch<SetStateAction<RootDto[]>>
  monitoring: MonitoringDto | null
  precheck: PrecheckDto | null
  setPrecheck: (precheck: PrecheckDto | null) => void
  runs: RunDto[]
  rootScans: StorageRootScanProgressDto[]
  setRootScans: Dispatch<SetStateAction<StorageRootScanProgressDto[]>>
  runningPrechecks: PrecheckRunDto[]
  refresh: () => Promise<void>
  setMessage: (message: string) => void
  onOpenRun: (id: string) => void
}) {
  const [targetRootIds, setTargetRootIds] = useState<string[]>([])
  const [rootPath, setRootPath] = useState('')
  const [rootDrafts, setRootDrafts] = useState<Record<string, RootDraft>>({})
  const [untrackableRootIds, setUntrackableRootIds] = useState<string[]>([])
  const [expandedRootIds, setExpandedRootIds] = useState<string[]>([])
  const [overrides, setOverrides] = useState<Record<string, RootOverride>>({})
  const [selectedRootId, setSelectedRootId] = useState('')
  const [precheckSearch, setPrecheckSearch] = useState('')
  const [precheckRunning, setPrecheckRunning] = useState(false)
  const [expandedPrecheckFolders, setExpandedPrecheckFolders] = useState<string[]>([])
  const [folderFilesByPath, setFolderFilesByPath] = useState<Record<string, PrecheckDto['files']>>({})
  const [loadingFolderPaths, setLoadingFolderPaths] = useState<string[]>([])
  const [directoryLimit, setDirectoryLimit] = useState(10)
  const [directoryPage, setDirectoryPage] = useState(0)
  const [scanHistoryByRoot, setScanHistoryByRoot] = useState<Record<string, PrecheckRunDto[]>>({})
  const [showCompletedScanNotice, setShowCompletedScanNotice] = useState(false)
  const effectiveRootIds = useMemo(
    () => pruneRootIds(roots, targetRootIds.length > 0 ? targetRootIds : roots.filter((root) => root.enabled).map((root) => root.id)),
    [roots, targetRootIds],
  )
  const selectedRootOverrides = useMemo(
    () =>
      effectiveRootIds.map((rootId) => ({
        rootId,
        retentionDays: overrides[rootId]?.retentionDays ? Number(overrides[rootId].retentionDays) : null,
        deletionMode: overrides[rootId]?.deletionMode || null,
        outputMode: overrides[rootId]?.outputMode || null,
        optimizedRootOverride: overrides[rootId]?.optimizedRootOverride || null,
        archiveRootOverride: overrides[rootId]?.archiveRootOverride || null,
      })),
    [effectiveRootIds, overrides],
  )
  const activeRootScans = rootScans.filter((scan) => ['QUEUED', 'RUNNING'].includes(scan.status))
  const visibleRunningPrecheck = precheck?.status === 'RUNNING' ? precheck : runningPrechecks[0]
  const canStartOptimization =
    Boolean(precheck) &&
    precheck?.status === 'COMPLETED' &&
    precheck.selectedFiles > 0

  useEffect(() => {
    if (!activeRootScans.length) return undefined
    const id = window.setInterval(async () => {
      setRootScans(await api.rootScans(headers))
    }, 1500)
    return () => window.clearInterval(id)
  }, [activeRootScans.length, headers, setRootScans])

  useEffect(() => {
    if (!precheckRunning && runningPrechecks.length === 0) return undefined
    const id = window.setInterval(async () => {
      setRoots(await api.roots(headers))
    }, 1500)
    return () => window.clearInterval(id)
  }, [headers, precheckRunning, runningPrechecks.length, setRoots])

  async function addRoot(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!event.currentTarget.checkValidity()) {
      event.currentTarget.classList.add('validated')
      event.currentTarget.reportValidity()
      return
    }
    const form = new FormData(event.currentTarget)
    const root = await api.addRoot({ label: String(form.get('label') ?? ''), path: rootPath || String(form.get('path') ?? '') }, headers)
    setRoots((current) => {
      const existing = current.findIndex((item) => item.id === root.id)
      if (existing === -1) return [...current, root]
      return current.map((item) => (item.id === root.id ? root : item))
    })
    event.currentTarget.reset()
    event.currentTarget.classList.remove('validated')
    setRootPath('')
    setRootScans(await api.rootScans(headers))
    await refresh()
  }

  async function toggleRoot(root: RootDto, enabled: boolean) {
    const updated = await api.updateRoot({ ...root, enabled }, headers)
    setRoots((current) => current.map((item) => (item.id === updated.id ? updated : item)))
    await refresh()
  }

  async function scanRoot(root: RootDto) {
    setTargetRootIds(pruneRootIds(roots, [root.id]))
    await startScan([root.id])
    setMessage(`Started scan for ${root.label}.`)
  }

  async function toggleAutoRescan(root: RootDto, enabled: boolean) {
    const updated = await api.updateRoot({ ...root, autoRescanEnabled: enabled }, headers)
    setRoots((current) => current.map((item) => (item.id === updated.id ? updated : item)))
  }

  function rootToDraft(root: RootDto): RootDraft {
    return {
      label: root.label,
      path: root.path,
      optimizedRootOverride: root.optimizedRootOverride,
      archiveRootOverride: root.archiveRootOverride,
    }
  }

  function draftFor(root: RootDto): RootDraft {
    return rootDrafts[root.id] ?? rootToDraft(root)
  }

  function setRootDraft(root: RootDto, patch: Partial<RootDraft>) {
    setRootDrafts((current) => ({ ...current, [root.id]: { ...(current[root.id] ?? rootToDraft(root)), ...patch } }))
  }

  async function persistRoot(root: RootDto, patch: Partial<RootDraft>) {
    const next = { ...root, ...draftFor(root), ...patch }
    next.label = next.label.trim()
    next.path = next.path.trim()
    next.optimizedRootOverride = next.optimizedRootOverride?.trim() || null
    next.archiveRootOverride = next.archiveRootOverride?.trim() || null
    if (!next.label || !next.path) return
    const changed =
      next.label !== root.label ||
      next.path !== root.path ||
      next.optimizedRootOverride !== root.optimizedRootOverride ||
      next.archiveRootOverride !== root.archiveRootOverride
    if (!changed) return
    const updated = await api.updateRoot(next, headers)
    setRoots((current) => current.map((item) => (item.id === updated.id ? updated : item)))
    setRootDrafts((current) => ({
      ...current,
      [updated.id]: {
        label: updated.label,
        path: updated.path,
        optimizedRootOverride: updated.optimizedRootOverride,
        archiveRootOverride: updated.archiveRootOverride,
      },
    }))
    if (updated.path !== root.path) {
      setPrecheck(null)
      await refresh()
    }
    setMessage('Storage root saved.')
  }

  function commitRootOnEnter(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') return
    event.currentTarget.blur()
  }

  async function loadScanHistory(root: RootDto) {
    const history = await api.rootScanHistory(root.id, headers)
    setScanHistoryByRoot((current) => ({ ...current, [root.id]: history }))
    setExpandedRootIds((current) => (current.includes(root.id) ? current : [...current, root.id]))
  }

  async function dismissRootScan(rootId: string) {
    await api.dismissRootScan(rootId, headers)
    setRootScans((current) => current.filter((scan) => scan.rootId !== rootId))
  }

  async function deleteRoot(root: RootDto) {
    const response = await api.deleteRoot(root.id, headers)
    if (!response.ok) {
      setUntrackableRootIds((current) => (current.includes(root.id) ? current : [...current, root.id]))
      setMessage(`"${root.label}" has tracked file history. Use Untrack & delete to remove it from tracking and delete the root.`)
      return
    }
    setRoots((current) => current.filter((item) => item.id !== root.id))
    setTargetRootIds((current) => current.filter((id) => id !== root.id))
    setUntrackableRootIds((current) => current.filter((id) => id !== root.id))
    await refresh()
  }

  async function untrackAndDeleteRoot(root: RootDto) {
    const result = await api.untrackAndDeleteRoot(root.id, headers)
    setRoots((current) => current.filter((item) => item.id !== root.id))
    setTargetRootIds((current) => current.filter((id) => id !== root.id))
    setUntrackableRootIds((current) => current.filter((id) => id !== root.id))
    setMessage(`Untracked ${result.untrackedMediaFiles} files and deleted "${root.label}".`)
    await refresh()
  }

  async function startScan(rootIds = effectiveRootIds) {
    setPrecheckRunning(true)
    setMessage('Scan is running. Large folders may take a while while metadata is checked safely.')
    try {
      const rootOverrides = selectedRootOverrides.filter((override) => rootIds.includes(override.rootId))
      let body = await api.precheck(rootIds, rootOverrides, headers)
      setPrecheck(body)
      if (body.roots.length > 0) setTargetRootIds(body.roots.map((root) => root.id))
      setSelectedRootId(body.roots[0]?.id ?? '')
      setExpandedPrecheckFolders([])
      setFolderFilesByPath({})
      setDirectoryPage(0)
      while (body.status === 'RUNNING') {
        await delay(1000)
        body = await api.precheckDetails(body.id, headers, 0, body.files.size, '', precheckSearch)
        setPrecheck(body)
        if (body.roots[0]?.id) setSelectedRootId(body.roots[0].id)
      }
      if (body.roots[0]?.id) {
        setSelectedRootId(body.roots[0].id)
        body = await api.precheckDetails(body.id, headers, 0, body.files.size, body.roots[0].id, precheckSearch)
        setPrecheck(body)
        if (body.roots.length > 0) setTargetRootIds(body.roots.map((root) => root.id))
      }
      setShowCompletedScanNotice(body.status === 'COMPLETED')
      setMessage(body.status === 'FAILED' ? 'Scan failed.' : 'Scan complete.')
      setRoots(await api.roots(headers))
      await refresh()
    } finally {
      setPrecheckRunning(false)
    }
  }

  async function loadPrecheck(page: number, rootId = selectedRootId, search = precheckSearch) {
    if (!precheck) return
    setPrecheck(await api.precheckDetails(precheck.id, headers, page, precheck.files.size, rootId, search))
  }

  async function loadFolderFiles(folderPath: string, page = 0) {
    if (!precheck) return
    setLoadingFolderPaths((current) => (current.includes(folderPath) ? current : [...current, folderPath]))
    try {
      const files = await api.precheckFolderFiles(precheck.id, headers, folderPath, page, 100, selectedRootId, precheckSearch)
      setFolderFilesByPath((current) => ({ ...current, [folderPath]: files }))
    } finally {
      setLoadingFolderPaths((current) => current.filter((path) => path !== folderPath))
    }
  }

  async function toggleSelection(itemId: string, selected: boolean) {
    if (!precheck) return
    await api.updatePrecheckSelection(itemId, selected, headers)
    setFolderFilesByPath((current) =>
      Object.fromEntries(
        Object.entries(current).map(([path, files]) => [
          path,
          {
            ...files,
            content: files.content.map((file) => (file.id === itemId ? { ...file, selected } : file)),
          },
        ]),
      ),
    )
    await loadPrecheck(0)
  }

  async function toggleFolderSelection(folderPath: string, selected: boolean) {
    if (!precheck) return
    await api.updatePrecheckFolderSelection(precheck.id, selectedRootId, folderPath, selected, headers)
    await loadPrecheck(0)
    if (folderFilesByPath[folderPath]) {
      await loadFolderFiles(folderPath, folderFilesByPath[folderPath].page)
    }
  }

  async function startRun() {
    if (!precheck || precheck.selectedFiles === 0) return
    await api.startRun({ precheckId: precheck.id }, headers)
    setMessage('Optimization run queued.')
    await refresh()
  }

  async function updateAllSelections(selected: boolean) {
    if (!precheck) return
    await api.updateAllPrecheckSelections(precheck.id, selected, headers)
    await loadPrecheck(0)
    await Promise.all(
      expandedPrecheckFolders
        .filter((folderPath) => folderFilesByPath[folderPath])
        .map((folderPath) => loadFolderFiles(folderPath, folderFilesByPath[folderPath].page)),
    )
  }

  async function cancelRun(run: RunDto) {
    if (!window.confirm(`Cancel run ${run.id.slice(0, 8)} and restore unfinished files to their normal state?`)) return
    await api.cancelRun(run.id, headers)
    setMessage('Run cancelled.')
    await refresh()
  }

  async function deleteRun(run: RunDto) {
    if (!window.confirm(`Delete run ${run.id.slice(0, 8)} from history? Report/log artifacts and this run's compression history will be removed.`)) return
    try {
      await api.deleteRun(run.id, headers)
      setMessage('Run deleted and file compression history recalculated.')
      await refresh()
    } catch {
      setMessage('Could not delete that run. Cancel active runs before deleting them.')
    }
  }

  return (
    <>
      <PageHeader
        title="Operations Dashboard"
        description="Dense archive workflow controls with validation-first progress tracking."
        actions={
          <button onClick={() => void refresh()}>
            <RefreshCw size={16} /> Refresh
          </button>
        }
      />

      <section className="metrics-grid">
        <MetricCard icon={<HardDrive size={18} />} label="Storage roots" value={roots.length} />
        <MetricCard icon={<Archive size={18} />} label="Archived originals" value={monitoring?.archivedFiles ?? 0} />
        <MetricCard icon={<CircleCheck size={18} />} label="Historical savings" value={bytes(monitoring?.historicalSavingsBytes ?? 0)} />
        <MetricCard icon={<ShieldCheck size={18} />} label="Pending approvals" value={monitoring?.pendingDeletionApprovals ?? 0} />
      </section>

      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Storage roots</h2>
            <p>Each root shows its effective policy and can take one-run overrides before precheck.</p>
          </div>
          <div className="button-row">
            <button disabled={precheckRunning || effectiveRootIds.length === 0} onClick={() => void startScan()}>
              <Search size={16} /> {precheckRunning ? 'Scan running' : 'Scan selected roots'}
            </button>
          </div>
        </div>
        <form onSubmit={addRoot} className="inline-form root-form" noValidate>
          <input name="label" placeholder="Label" required />
          <span className="input-with-action">
            <input name="path" value={rootPath} onChange={(e) => setRootPath(e.target.value)} placeholder="E:\Videos or /mnt/archive" required />
            <PathPickerButton headers={headers} initialPath={rootPath} onPick={(paths) => setRootPath(paths[0] ?? '')} />
          </span>
          <button type="submit">Add root</button>
        </form>
        <div className="root-list">
          {rootScans.length > 0 && (
            <div className="scan-progress-list">
              {rootScans.map((scan) => {
                const root = roots.find((item) => item.id === scan.rootId)
                return (
                  <div key={scan.rootId} className="scan-progress-row">
                    <span>
                      <strong>{root?.label ?? scan.rootId.slice(0, 8)}</strong>
                      <small>{scan.message}</small>
                    </span>
                    <ProgressBar value={scan.progressPercent} />
                    <small>
                      {scan.scannedFiles}/{scan.totalFiles} scanned, {scan.remainingFiles} left
                    </small>
                    {scan.status === 'COMPLETED' && (
                      <button type="button" onClick={() => void dismissRootScan(scan.rootId)}>
                        Dismiss
                      </button>
                    )}
                  </div>
                )
              })}
            </div>
          )}
          {roots.map((root) => {
            const expanded = expandedRootIds.includes(root.id)
            const override = overrides[root.id] ?? { retentionDays: '', deletionMode: '', outputMode: '', optimizedRootOverride: '', archiveRootOverride: '' }
            const draft = draftFor(root)
            const disabledByParent = hasSelectedAncestor(root, roots, effectiveRootIds)
            return (
              <div key={root.id} className="root-row">
                <div className="root-main">
                  <label className="check-row">
                    <input
                      type="checkbox"
                      checked={effectiveRootIds.includes(root.id)}
                      disabled={!root.enabled || disabledByParent}
                      onChange={(event) =>
                        setTargetRootIds((current) => {
                          const base = current.length > 0 ? current : effectiveRootIds
                          return event.target.checked
                            ? pruneRootIds(roots, [...base, root.id])
                            : pruneRootIds(roots, base.filter((id) => id !== root.id))
                        })
                      }
                    />
                    <span>
                      <strong>{root.label}</strong>
                      <small>{root.path}</small>
                      <small>{root.scanStatus === 'SCANNED' ? 'Scanned' : 'Not yet scanned'}</small>
                      {root.changesDetected && <small>Changes detected</small>}
                      {disabledByParent && <small>Covered by selected parent root</small>}
                    </span>
                  </label>
                  <span>{root.effectiveRetentionDays} days</span>
                  <span>{root.effectiveDeletionMode}</span>
                  <span>{root.effectiveOutputMode}</span>
                  <button type="button" onClick={() => void toggleRoot(root, !root.enabled)}>
                    {root.enabled ? 'Enabled' : 'Disabled'}
                  </button>
                  <button type="button" onClick={() => void scanRoot(root)}>
                    <Search size={15} /> Scan
                  </button>
                  <button type="button" onClick={() => void loadScanHistory(root)}>
                    History
                  </button>
                  <button type="button" onClick={() => void deleteRoot(root)}>
                    <Trash2 size={15} /> Delete
                  </button>
                  {untrackableRootIds.includes(root.id) && (
                    <button type="button" onClick={() => void untrackAndDeleteRoot(root)}>
                      <Trash2 size={15} /> Untrack & delete
                    </button>
                  )}
                  <button type="button" onClick={() => setExpandedRootIds((current) => (expanded ? current.filter((id) => id !== root.id) : [...current, root.id]))}>
                    <ChevronDown size={15} /> {expanded ? 'Hide' : 'Details'}
                  </button>
                </div>
                {expanded && (
                  <div className="root-detail">
                    <div>
                      <strong>Active policies</strong>
                      <p>{root.activePolicies.length ? root.activePolicies.map((policy) => policy.path).join(', ') : 'Default settings only'}</p>
                    </div>
                    <div className="root-field-grid">
                      <label>
                        Name
                        <input
                          value={draft.label}
                          onChange={(e) => setRootDraft(root, { label: e.target.value })}
                          onBlur={() => void persistRoot(root, { label: draft.label })}
                          onKeyDown={commitRootOnEnter}
                          aria-label="Root label"
                          required
                        />
                      </label>
                      <label>
                        Path
                        <span className="input-with-action">
                          <input
                            value={draft.path}
                            onChange={(e) => setRootDraft(root, { path: e.target.value })}
                            onBlur={() => void persistRoot(root, { path: draft.path })}
                            onKeyDown={commitRootOnEnter}
                            aria-label="Root path"
                            required
                          />
                          <PathPickerButton
                            headers={headers}
                            initialPath={draft.path}
                            onPick={(paths) => {
                              const path = paths[0] ?? root.path
                              setRootDraft(root, { path })
                              void persistRoot(root, { path })
                            }}
                          />
                        </span>
                      </label>
                      <label>
                        Custom optimized results folder
                        <span className="input-with-action">
                          <input
                            value={draft.optimizedRootOverride ?? ''}
                            onChange={(e) => setRootDraft(root, { optimizedRootOverride: e.target.value || null })}
                            onBlur={() => void persistRoot(root, { optimizedRootOverride: draft.optimizedRootOverride })}
                            onKeyDown={commitRootOnEnter}
                            aria-label="Optimized target folder override"
                            placeholder="Default optimized root"
                          />
                          <PathPickerButton
                            headers={headers}
                            initialPath={draft.optimizedRootOverride ?? ''}
                            onPick={(paths) => {
                              const optimizedRootOverride = paths[0] ?? null
                              setRootDraft(root, { optimizedRootOverride })
                              void persistRoot(root, { optimizedRootOverride })
                            }}
                          />
                        </span>
                      </label>
                      <label>
                        Custom archive folder
                        <span className="input-with-action">
                          <input
                            value={draft.archiveRootOverride ?? ''}
                            onChange={(e) => setRootDraft(root, { archiveRootOverride: e.target.value || null })}
                            onBlur={() => void persistRoot(root, { archiveRootOverride: draft.archiveRootOverride })}
                            onKeyDown={commitRootOnEnter}
                            aria-label="Archive target folder override"
                            placeholder="Default archive root"
                          />
                          <PathPickerButton
                            headers={headers}
                            initialPath={draft.archiveRootOverride ?? ''}
                            onPick={(paths) => {
                              const archiveRootOverride = paths[0] ?? null
                              setRootDraft(root, { archiveRootOverride })
                              void persistRoot(root, { archiveRootOverride })
                            }}
                          />
                        </span>
                      </label>
                      <label className="check-row">
                        <input
                          type="checkbox"
                          checked={root.autoRescanEnabled}
                          onChange={(event) => void toggleAutoRescan(root, event.target.checked)}
                        />
                        <span>Automatic rescanning</span>
                      </label>
                    </div>
                    <div className="form-grid">
                      <label>
                        Run retention override
                        <select
                          value={override.retentionDays}
                          onChange={(e) => setOverrides((current) => ({ ...current, [root.id]: { ...override, retentionDays: e.target.value } }))}
                        >
                          <option value="">Use active policy</option>
                          <option value="0">0 days</option>
                          <option value="7">7 days</option>
                          <option value="14">14 days</option>
                          <option value="30">30 days</option>
                          <option value="90">90 days</option>
                        </select>
                      </label>
                      <label>
                        Run deletion override
                        <select
                          value={override.deletionMode}
                          onChange={(e) => setOverrides((current) => ({ ...current, [root.id]: { ...override, deletionMode: e.target.value } }))}
                        >
                          <option value="">Use active policy</option>
                          <option value="MANUAL">Manual</option>
                          <option value="AUTOMATIC">Automatic</option>
                        </select>
                      </label>
                      <label>
                        Run output override
                        <select
                          value={override.outputMode}
                          onChange={(e) => setOverrides((current) => ({ ...current, [root.id]: { ...override, outputMode: e.target.value } }))}
                        >
                          <option value="">Use active policy</option>
                          <option value="PARALLEL">Parallel</option>
                          <option value="SAME_LIBRARY">Same library</option>
                        </select>
                      </label>
                      <label>
                        One-run optimized results folder
                        <span className="input-with-action">
                          <input
                            value={override.optimizedRootOverride}
                            onChange={(e) => setOverrides((current) => ({ ...current, [root.id]: { ...override, optimizedRootOverride: e.target.value } }))}
                            placeholder="Use root/default target"
                          />
                          <PathPickerButton
                            headers={headers}
                            initialPath={override.optimizedRootOverride}
                            onPick={(paths) => setOverrides((current) => ({ ...current, [root.id]: { ...override, optimizedRootOverride: paths[0] ?? '' } }))}
                          />
                        </span>
                      </label>
                      <label>
                        One-run archive folder
                        <span className="input-with-action">
                          <input
                            value={override.archiveRootOverride}
                            onChange={(e) => setOverrides((current) => ({ ...current, [root.id]: { ...override, archiveRootOverride: e.target.value } }))}
                            placeholder="Use root/default target"
                          />
                          <PathPickerButton
                            headers={headers}
                            initialPath={override.archiveRootOverride}
                            onPick={(paths) => setOverrides((current) => ({ ...current, [root.id]: { ...override, archiveRootOverride: paths[0] ?? '' } }))}
                          />
                        </span>
                      </label>
                    </div>
                    {scanHistoryByRoot[root.id] && (
                      <div className="root-scan-history">
                        <strong>Included in scans</strong>
                        {scanHistoryByRoot[root.id].length === 0 && <small>No scans yet.</small>}
                        {scanHistoryByRoot[root.id].map((scan) => (
                          <div key={scan.id}>
                            <span>{scan.id.slice(0, 8)}</span>
                            <small>{scan.status}</small>
                            <small>{scan.totalFiles} files</small>
                            <ArtifactLinks reportUrl={scan.reportUrl} logUrl={scan.logUrl} />
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </section>

      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Scan results</h2>
            <p>Latest scan results are grouped by storage root and directory. Files never compressed are selected by default; previously compressed files stay unselected.</p>
          </div>
          <div className="button-row">
            <button disabled={!canStartOptimization} onClick={() => void startRun()}>
              <Play size={16} /> Start optimization
            </button>
          </div>
        </div>
        {(precheckRunning || visibleRunningPrecheck) && (
          <div className="running-notice">
            <ProgressBar value={visibleRunningPrecheck?.progressPercent ?? 100} />
            <span>
              {visibleRunningPrecheck?.progressMessage ?? 'Scan is running.'}{' '}
              {visibleRunningPrecheck &&
                `${visibleRunningPrecheck.scannedFiles}/${visibleRunningPrecheck.totalFiles} scanned, ${Math.max(0, visibleRunningPrecheck.totalFiles - visibleRunningPrecheck.scannedFiles)} left.`}
            </span>
          </div>
        )}
        {!precheckRunning && !visibleRunningPrecheck && showCompletedScanNotice && precheck?.status === 'COMPLETED' && (
          <div className="running-notice">
            <ProgressBar value={100} />
            <span>Scan complete. {precheck.scannedFiles}/{precheck.totalFiles} files available.</span>
            <button type="button" onClick={() => setShowCompletedScanNotice(false)}>
              Dismiss
            </button>
          </div>
        )}
        {precheck && (
          <>
            <div className="summary-strip">
              <span>{precheck.totalFiles} files</span>
              <span>{gigabytes(precheck.totalBytes)} current</span>
              <span>{gigabytes(precheck.estimatedOutputBytes)} estimated optimized</span>
              <span>{gigabytes(precheck.estimatedSavingsBytes)} total savings</span>
              <span>{precheck.selectedFiles} selected</span>
              <ArtifactLinks reportUrl={precheck.reportUrl} logUrl={precheck.logUrl} />
            </div>
            <div className="button-row">
              <button type="button" onClick={() => void updateAllSelections(true)}>
                Select all
              </button>
              <button type="button" onClick={() => void updateAllSelections(false)}>
                Clear all
              </button>
            </div>
            <div className="split-grid">
              <div className="tree-list">
                {precheck.roots.map((root) => (
                  <button
                    key={root.id}
                    type="button"
                    className={selectedRootId === root.id ? 'active' : ''}
                    onClick={() => {
                      setSelectedRootId(root.id)
                      setDirectoryPage(0)
                      setExpandedPrecheckFolders([])
                      setFolderFilesByPath({})
                      void loadPrecheck(0, root.id)
                    }}
                  >
                    <span>{root.label}</span>
                    <small>{root.path}</small>
                    <small>
                      {root.selectedFiles} selected, {root.unselectedFiles} unselected
                    </small>
                  </button>
                ))}
              </div>
              <div className="tree-files">
                <form
                  className="search-row scan-results-search-row"
                  onSubmit={(event) => {
                    event.preventDefault()
                    setDirectoryPage(0)
                    setExpandedPrecheckFolders([])
                    setFolderFilesByPath({})
                    void loadPrecheck(0, selectedRootId, precheckSearch)
                  }}
                >
                  <input value={precheckSearch} onChange={(e) => setPrecheckSearch(e.target.value)} placeholder="Search filenames" />
                  <button type="submit">Search</button>
                  <select
                    value={directoryLimit}
                    onChange={(e) => {
                      setDirectoryLimit(Number(e.target.value))
                      setDirectoryPage(0)
                    }}
                    aria-label="Directories shown"
                  >
                    <option value={10}>10 dirs</option>
                    <option value={50}>50 dirs</option>
                    <option value={100}>100 dirs</option>
                  </select>
                </form>
                <div className="folder-detail-list">
                  {(precheck.folders ?? [])
                    .slice(directoryPage * directoryLimit, directoryPage * directoryLimit + directoryLimit)
                    .map((folder) => {
                    const expanded = expandedPrecheckFolders.includes(folder.path)
                    const folderFiles = folderFilesByPath[folder.path]
                    const selectedCount = folder.selectedFiles
                    const totalCount = folder.totalFiles
                    return (
                      <div key={folder.path} className="folder-detail">
                        <div className="folder-detail-head">
                          <input
                            type="checkbox"
                            checked={selectedCount > 0 && selectedCount === totalCount}
                            ref={(input) => {
                              if (input) input.indeterminate = selectedCount > 0 && selectedCount < totalCount
                            }}
                            onChange={(event) => void toggleFolderSelection(folder.path, event.target.checked)}
                          />
                          <button
                            type="button"
                            className="link-button"
                            onClick={() => {
                              setExpandedPrecheckFolders((current) =>
                                expanded ? current.filter((path) => path !== folder.path) : [...current, folder.path],
                              )
                              if (!expanded && !folderFilesByPath[folder.path]) void loadFolderFiles(folder.path)
                            }}
                          >
                            <ChevronDown size={15} /> {folder.path}
                          </button>
                          <small>
                            {selectedCount}/{totalCount} selected
                          </small>
                        </div>
                        {expanded && (
                          <div className="table compact-table">
                            {loadingFolderPaths.includes(folder.path) && (
                              <div>
                                <span>Loading files...</span>
                              </div>
                            )}
                            {!loadingFolderPaths.includes(folder.path) && folderFiles && folderFiles.content.length === 0 && (
                              <div>
                                <span>No matching files in this folder.</span>
                              </div>
                            )}
                            {!loadingFolderPaths.includes(folder.path) &&
                              folderFiles?.content.map((file) => (
                                <div key={file.id}>
                                  <input type="checkbox" checked={file.selected} onChange={(event) => void toggleSelection(file.id, event.target.checked)} />
                                  <span title={file.path}>{file.relativePath || file.path}</span>
                                  <span>{bytes(file.sizeBytes)}</span>
                                  <span className="status-pill">{file.everOptimized ? 'Already compressed' : file.status}</span>
                                </div>
                              ))}
                            {!loadingFolderPaths.includes(folder.path) && folderFiles && folderFiles.totalPages > 1 && (
                              <Pagination page={folderFiles.page} totalPages={folderFiles.totalPages} onPage={(page) => void loadFolderFiles(folder.path, page)} />
                            )}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
                <Pagination
                  page={directoryPage}
                  totalPages={Math.ceil((precheck.folders?.length ?? 0) / directoryLimit)}
                  onPage={(page) => {
                    setDirectoryPage(page)
                    setExpandedPrecheckFolders([])
                    setFolderFilesByPath({})
                  }}
                />
              </div>
            </div>
          </>
        )}
      </section>

      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Runs</h2>
            <p>Every run gets a progress bar plus report and log artifacts after completion.</p>
          </div>
        </div>
        <div className="run-list">
          {runs.map((run) => (
            <div key={run.id} className="run-row">
              <div>
                <button type="button" className="link-button" onClick={() => onOpenRun(run.id)}>
                  <Gauge size={15} /> {run.id.slice(0, 8)}
                </button>
                <RunMeta run={run} />
              </div>
              <span className="status-pill">{run.status}</span>
              <div>
                <ProgressBar value={run.progressPercent} />
                <small>
                  {run.completedFiles}/{run.totalFiles} complete, {run.failedFiles} failed
                </small>
              </div>
              <span>{bytes(run.savedBytes)} saved</span>
              <ArtifactLinks reportUrl={run.reportUrl} logUrl={run.logUrl} />
              {['QUEUED', 'RUNNING'].includes(run.status) && (
                <button type="button" onClick={() => void cancelRun(run)}>
                  <Square size={15} /> Cancel
                </button>
              )}
              {!['QUEUED', 'RUNNING'].includes(run.status) && (
                <button type="button" onClick={() => void deleteRun(run)}>
                  <Trash2 size={15} /> Delete
                </button>
              )}
            </div>
          ))}
        </div>
      </section>
    </>
  )
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function pruneRootIds(roots: RootDto[], ids: string[]) {
  const unique = [...new Set(ids)]
  const selectedRoots = unique.map((id) => roots.find((root) => root.id === id)).filter((root): root is RootDto => Boolean(root))
  return selectedRoots
    .sort((a, b) => normalizePathForCompare(a.path).length - normalizePathForCompare(b.path).length)
    .reduce<string[]>((accepted, root) => {
      if (accepted.some((id) => isPathAncestor(roots.find((candidate) => candidate.id === id)?.path ?? '', root.path))) return accepted
      return [...accepted, root.id]
    }, [])
}

function hasSelectedAncestor(root: RootDto, roots: RootDto[], selectedIds: string[]) {
  return selectedIds.some((id) => id !== root.id && isPathAncestor(roots.find((candidate) => candidate.id === id)?.path ?? '', root.path))
}

function isPathAncestor(parent: string, child: string) {
  const normalizedParent = normalizePathForCompare(parent)
  const normalizedChild = normalizePathForCompare(child)
  if (normalizedParent === '/') return normalizedChild !== '/'
  return normalizedParent.length > 0 && normalizedChild !== normalizedParent && normalizedChild.startsWith(`${normalizedParent}/`)
}

function normalizePathForCompare(path: string) {
  const normalized = path.trim().replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
  return normalized || (path.trim().startsWith('/') ? '/' : normalized)
}
