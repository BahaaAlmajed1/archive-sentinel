import { useEffect, useState } from 'react'
import { ArrowLeft, FileVideo, Square } from 'lucide-react'
import { api, type ApiHeaders } from '../api/client'
import { ArtifactLinks } from '../components/ArtifactLinks'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { ProgressBar } from '../components/ProgressBar'
import { RunMeta } from '../components/RunMeta'
import { bytes } from '../domain/defaults'
import type { RunDetailsDto } from '../domain/types'

export function RunDetailsPage({ id, headers, onBack }: { id: string; headers: ApiHeaders; onBack: () => void }) {
  const [details, setDetails] = useState<RunDetailsDto | null>(null)
  const [search, setSearch] = useState('')

  useEffect(() => {
    void api.runDetails(id, headers).then(setDetails)
  }, [headers, id])

  useEffect(() => {
    if (!details || !['QUEUED', 'RUNNING'].includes(details.run.status)) return undefined
    const interval = window.setInterval(() => {
      void loadPage(details.items.page)
    }, 3000)
    return () => window.clearInterval(interval)
  }, [details?.run.status, details?.items.page, headers, id])

  async function loadPage(page: number, query = search) {
    if (!details) return
    setDetails(await api.runDetails(id, headers, page, details.items.size, query))
  }

  async function cancelRun() {
    if (!details) return
    if (!window.confirm(`Cancel run ${details.run.id.slice(0, 8)} and restore unfinished files to their normal state?`)) return
    setDetails({ ...details, run: await api.cancelRun(details.run.id, headers) })
    await loadPage(details.items.page)
  }

  if (!details) {
    return <PageHeader title="Run Details" description="Loading run details..." actions={<BackButton onBack={onBack} />} />
  }
  const groupedItems = details.items.content.reduce<Record<string, typeof details.items.content>>((groups, item) => {
    const key = item.storageRootLabel ?? 'Unknown root'
    groups[key] = groups[key] ?? []
    groups[key].push(item)
    return groups
  }, {})

  return (
    <>
      <PageHeader
        title={`Run ${details.run.id.slice(0, 8)}`}
        description="Per-file status is paginated here; complete file trees live in the generated report."
        actions={
          <div className="button-row">
            {['QUEUED', 'RUNNING'].includes(details.run.status) && (
              <button type="button" onClick={() => void cancelRun()}>
                <Square size={16} /> Cancel
              </button>
            )}
            <BackButton onBack={onBack} />
          </div>
        }
      />
      <section className="panel run-detail-head">
        <span className="status-pill">{details.run.status}</span>
        <ProgressBar value={details.run.progressPercent} />
        <strong>
          {details.run.completedFiles}/{details.run.totalFiles} files complete
        </strong>
        <strong>{bytes(details.run.savedBytes)} saved</strong>
        <ArtifactLinks reportUrl={details.run.reportUrl} logUrl={details.run.logUrl} />
        <RunMeta run={details.run} />
      </section>
      <section className="panel">
        <div className="section-head">
          <div className="search-row">
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search filenames" />
            <button onClick={() => void loadPage(0, search)}>Search</button>
          </div>
          <Pagination page={details.items.page} totalPages={details.items.totalPages} onPage={(page) => void loadPage(page)} />
        </div>
        <div className="run-root-groups">
          {Object.entries(groupedItems).map(([root, items]) => (
            <div key={root} className="run-root-group">
              <div className="run-root-title">
                <strong>{root}</strong>
                <small>{items[0]?.storageRootPath}</small>
                <small>
                  Optimized: {items[0]?.optimizedRoot ?? 'default'} | Archive: {items[0]?.archiveRoot ?? 'default'}
                </small>
              </div>
              <div className="table run-item-table">
                {items.map((item) => (
                  <div key={item.id}>
                    <FileVideo size={15} />
                    <span title={item.originalPath}>{item.originalPath}</span>
                    <span className="status-pill">{item.status}</span>
                    <span>{bytes(item.inputBytes)}</span>
                    <span>{item.outputBytes > 0 ? bytes(item.outputBytes) : 'Expected after processing'}</span>
                    <span>{item.errorMessage}</span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>
    </>
  )
}

function BackButton({ onBack }: { onBack: () => void }) {
  return (
    <button type="button" onClick={onBack}>
      <ArrowLeft size={16} /> Back
    </button>
  )
}
