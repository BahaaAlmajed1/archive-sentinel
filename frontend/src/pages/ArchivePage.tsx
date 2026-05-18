import { useState } from 'react'
import { ArchiveRestore, ChevronDown, Trash2 } from 'lucide-react'
import { api, type ApiHeaders } from '../api/client'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { bytes } from '../domain/defaults'
import { RunMeta } from '../components/RunMeta'
import type { ArchiveRunDto, ArchiveRunTreeDto, MediaDto, PageResponse } from '../domain/types'

export function ArchivePage({
  headers,
  archiveRuns,
  pendingDeletion,
  setArchiveRuns,
  refresh,
}: {
  headers: ApiHeaders
  archiveRuns: PageResponse<ArchiveRunDto>
  pendingDeletion: MediaDto[]
  setArchiveRuns: (page: PageResponse<ArchiveRunDto>) => void
  refresh: () => Promise<void>
}) {
  const [expandedRunId, setExpandedRunId] = useState<string | null>(null)
  const [runTree, setRunTree] = useState<ArchiveRunTreeDto | null>(null)
  const [search, setSearch] = useState('')
  const [selectedFolder, setSelectedFolder] = useState('')

  async function restore(id: string) {
    await api.restore(id, headers)
    await refresh()
    if (expandedRunId) setRunTree(await api.archiveRunFiles(expandedRunId, headers, runTree?.files.page ?? 0, runTree?.files.size ?? 50, search, selectedFolder))
  }

  async function restoreRun(id: string) {
    await api.restoreRun(id, headers)
    await refresh()
    if (expandedRunId === id) setRunTree(await api.archiveRunFiles(id, headers, 0, runTree?.files.size ?? 50, search, selectedFolder))
  }

  async function approveDeletion(id: string) {
    await api.approveDeletion(id, headers)
    await refresh()
  }

  async function loadRuns(page: number) {
    setArchiveRuns(await api.archiveRuns(headers, page, archiveRuns.size))
  }

  async function toggleRun(id: string) {
    if (expandedRunId === id) {
      setExpandedRunId(null)
      setRunTree(null)
      return
    }
    setExpandedRunId(id)
    setSelectedFolder('')
    setRunTree(await api.archiveRunFiles(id, headers))
  }

  async function loadRunFiles(page: number, query = search, folder = selectedFolder) {
    if (!expandedRunId || !runTree) return
    setRunTree(await api.archiveRunFiles(expandedRunId, headers, page, runTree.files.size, query, folder))
  }

  return (
    <>
      <PageHeader title="Archive" description="Restore retained originals by run first, then drill into individual files when needed." />

      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Pending deletion approvals</h2>
            <p>Manual deletion mode pauses here after retention expires.</p>
          </div>
        </div>
        <div className="table archive-table">
          {pendingDeletion.map((item) => (
            <div key={item.id}>
              <span title={item.archivedPath ?? item.originalPath}>{item.archivedPath ?? item.originalPath}</span>
              <span>{item.retentionUntil ? new Date(item.retentionUntil).toLocaleDateString() : 'n/a'}</span>
              <button onClick={() => void approveDeletion(item.id)}>
                <Trash2 size={15} /> Approve deletion
              </button>
            </div>
          ))}
          {pendingDeletion.length === 0 && <p className="empty-state">No manual approvals are waiting.</p>}
        </div>
      </section>

      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Recoverable runs</h2>
            <p>Restore an entire run or open it to restore individual files.</p>
          </div>
          <Pagination page={archiveRuns.page} totalPages={archiveRuns.totalPages} onPage={(page) => void loadRuns(page)} />
        </div>
        <div className="run-archive-list">
          {archiveRuns.content.map((item) => (
            <div key={item.run.id} className="archive-run">
              <div className="archive-run-head">
                <div>
                  <button type="button" onClick={() => void toggleRun(item.run.id)}>
                    <ChevronDown size={15} /> {item.run.id.slice(0, 8)}
                  </button>
                  <RunMeta run={item.run} />
                </div>
                <span>{item.recoverableFiles} recoverable</span>
                <span>{item.restoredFiles} restored</span>
                <button onClick={() => void restoreRun(item.run.id)}>
                  <ArchiveRestore size={15} /> Restore run
                </button>
              </div>
              {expandedRunId === item.run.id && runTree && (
                <div className="archive-run-detail">
                  <div className="search-row">
                    <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search filenames" />
                    <button onClick={() => void loadRunFiles(0, search)}>Search</button>
                  </div>
                  <div className="tree-list compact-tree">
                    <button
                      type="button"
                      className={selectedFolder === '' ? 'active' : ''}
                      onClick={() => {
                        setSelectedFolder('')
                        void loadRunFiles(0, search, '')
                      }}
                    >
                      All folders <small>{item.recoverableFiles} files</small>
                    </button>
                    {runTree.folders.map((folder) => (
                      <button
                        type="button"
                        key={folder.path}
                        className={selectedFolder === folder.path ? 'active' : ''}
                        onClick={() => {
                          setSelectedFolder(folder.path)
                          void loadRunFiles(0, search, folder.path)
                        }}
                      >
                        {folder.path} <small>{folder.totalFiles} files</small>
                      </button>
                    ))}
                  </div>
                  <div className="table archive-table">
                    {runTree.files.content.map((file) => (
                      <div key={file.id}>
                        <span title={file.archivedPath ?? file.originalPath}>{file.archivedPath ?? file.originalPath}</span>
                        <span>{bytes(file.sizeBytes)}</span>
                        <span>{file.retentionUntil ? new Date(file.retentionUntil).toLocaleDateString() : 'n/a'}</span>
                        <button onClick={() => void restore(file.id)}>
                          <ArchiveRestore size={15} /> Restore file
                        </button>
                      </div>
                    ))}
                  </div>
                  <Pagination page={runTree.files.page} totalPages={runTree.files.totalPages} onPage={(page) => void loadRunFiles(page)} />
                </div>
              )}
            </div>
          ))}
        </div>
      </section>
    </>
  )
}
