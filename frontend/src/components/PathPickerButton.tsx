import { Check, ChevronUp, File, Folder, FolderOpen, HardDrive, RefreshCw, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { api, type ApiHeaders } from '../api/client'
import type { ServerBrowserEntry, ServerBrowserResponse } from '../domain/types'

export function PathPickerButton({
  headers,
  initialPath,
  onPick,
  kind = 'folder',
  multiple = false,
}: {
  headers: ApiHeaders
  initialPath?: string
  onPick: (paths: string[]) => void
  kind?: 'folder' | 'file'
  multiple?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [browser, setBrowser] = useState<ServerBrowserResponse | null>(null)
  const [pathInput, setPathInput] = useState(initialPath ?? '')
  const [selectedPaths, setSelectedPaths] = useState<string[]>([])
  const [showHidden, setShowHidden] = useState(false)
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!open) return
    void load(initialPath)
  }, [open, initialPath, showHidden])

  async function load(path?: string) {
    setMessage('')
    try {
      const result = await api.browseServerFiles(headers, { path: path || undefined, kind, showHidden })
      setBrowser(result)
      setPathInput(result.currentPath)
      setSelectedPaths([])
      if (result.message) setMessage(result.message)
    } catch {
      setMessage('Could not browse that server path.')
    }
  }

  function toggleSelection(path: string) {
    if (!multiple) {
      setSelectedPaths([path])
      return
    }
    setSelectedPaths((current) => (current.includes(path) ? current.filter((item) => item !== path) : [...current, path]))
  }

  function choose(paths: string[]) {
    const clean = paths.filter(Boolean)
    if (clean.length === 0) return
    onPick(multiple ? clean : [clean[0]])
    setOpen(false)
  }

  function chooseEntry(entry: ServerBrowserEntry) {
    if (entry.directory) {
      void load(entry.path)
      return
    }
    choose([entry.path])
  }

  return (
    <>
      <button type="button" className="icon-button" onClick={() => setOpen(true)} title={kind === 'folder' ? 'Browse server folders' : 'Browse server files'}>
        <FolderOpen size={16} />
      </button>
      {open && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label={kind === 'folder' ? 'Browse server folders' : 'Browse server files'}>
          <div className="server-picker">
            <div className="server-picker-head">
              <div>
                <h2>{kind === 'folder' ? 'Browse Server Folders' : 'Browse Server Files'}</h2>
                <p>Paths are from the machine running Archive Sentinel.</p>
              </div>
              <button type="button" className="icon-button" onClick={() => setOpen(false)} title="Close picker">
                <X size={16} />
              </button>
            </div>

            <div className="server-picker-toolbar">
              <button type="button" disabled={!browser?.parentPath} onClick={() => browser?.parentPath && void load(browser.parentPath)} title="Parent folder">
                <ChevronUp size={16} />
              </button>
              <span className="input-with-action server-picker-path">
                <input value={pathInput} onChange={(event) => setPathInput(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && void load(pathInput)} />
                <button type="button" onClick={() => void load(pathInput)} title="Open path">
                  <RefreshCw size={16} />
                </button>
              </span>
              <label className="server-picker-hidden">
                <input type="checkbox" checked={showHidden} onChange={(event) => setShowHidden(event.target.checked)} />
                Hidden
              </label>
            </div>

            {browser && browser.roots.length > 1 && (
              <div className="server-picker-roots">
                {browser.roots.map((root) => (
                  <button type="button" key={root} onClick={() => void load(root)}>
                    <HardDrive size={15} /> {root}
                  </button>
                ))}
              </div>
            )}

            {message && <p className="form-message">{message}</p>}

            <div className="server-picker-list">
              {browser?.parentPath && (
                <button type="button" className="server-picker-row" onClick={() => void load(browser.parentPath ?? undefined)}>
                  <Folder size={16} />
                  <span>..</span>
                  <small>Parent folder</small>
                </button>
              )}
              {browser?.entries.map((entry) => {
                const selectable = kind === 'file' ? !entry.directory : entry.directory
                const selected = selectedPaths.includes(entry.path)
                return (
                  <button
                    type="button"
                    key={entry.path}
                    className={`server-picker-row${selected ? ' selected' : ''}`}
                    onClick={() => (selectable ? toggleSelection(entry.path) : void load(entry.path))}
                    onDoubleClick={() => chooseEntry(entry)}
                    disabled={!entry.readable && entry.directory}
                    title={entry.path}
                  >
                    {entry.directory ? <Folder size={16} /> : <File size={16} />}
                    <span>{entry.name}</span>
                    <small>{entry.directory ? 'Folder' : formatSize(entry.sizeBytes)}</small>
                    {selectable && <Check size={15} className={selected ? 'server-picker-check active' : 'server-picker-check'} />}
                  </button>
                )
              })}
              {browser && browser.entries.length === 0 && <p className="empty-state">No {kind === 'file' ? 'files' : 'folders'} visible here.</p>}
            </div>

            <div className="server-picker-actions">
              {kind === 'folder' && browser && (
                <button type="button" onClick={() => choose([browser.currentPath])}>
                  <Check size={16} /> Select current folder
                </button>
              )}
              <button type="button" disabled={selectedPaths.length === 0} onClick={() => choose(selectedPaths)}>
                <Check size={16} /> Select {selectedPaths.length || ''}
              </button>
              <button type="button" onClick={() => setOpen(false)}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function formatSize(value: number | null) {
  if (value === null) return 'File'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`
}
