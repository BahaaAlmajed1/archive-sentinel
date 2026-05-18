import { useState } from 'react'
import { Archive, CircleCheck, FileWarning, History, Hourglass, TrendingDown } from 'lucide-react'
import { api, type ApiHeaders } from '../api/client'
import { ArtifactLinks } from '../components/ArtifactLinks'
import { MetricCard } from '../components/MetricCard'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { ProgressBar } from '../components/ProgressBar'
import { RunMeta } from '../components/RunMeta'
import { bytes } from '../domain/defaults'
import type { MonitoringDto, PageResponse, PrecheckRunDto, RunDto } from '../domain/types'

export function ReportsPage({
  headers,
  monitoring,
  runs,
  prechecks,
  onOpenRun,
}: {
  headers: ApiHeaders
  monitoring: MonitoringDto | null
  runs: PageResponse<RunDto>
  prechecks: PageResponse<PrecheckRunDto>
  onOpenRun: (id: string) => void
}) {
  const [visibleRuns, setVisibleRuns] = useState<PageResponse<RunDto> | null>(null)
  const [runFilter, setRunFilter] = useState('')
  const [runSearch, setRunSearch] = useState('')
  const [visiblePrechecks, setVisiblePrechecks] = useState<PageResponse<PrecheckRunDto> | null>(null)
  const [precheckSearch, setPrecheckSearch] = useState('')
  const runPage = visibleRuns ?? runs
  const precheckPage = visiblePrechecks ?? prechecks

  async function loadRuns(page: number, status = runFilter, search = runSearch) {
    const next = await api.runs(headers, status, page, runPage.size, search)
    setRunFilter(status)
    setVisibleRuns(next)
  }

  async function loadPrechecks(page: number, search = precheckSearch) {
    setVisiblePrechecks(await api.prechecks(headers, page, precheckPage.size, search))
  }

  return (
    <>
      <PageHeader title="Reports" description="Aggregate observability first, with drill-down into run and precheck artifacts." />
      <section className="metrics-grid">
        <MetricCard icon={<Archive size={18} />} label="Archived originals" value={monitoring?.archivedFiles ?? 0} onClick={() => void loadRuns(0, 'COMPLETED')} />
        <MetricCard icon={<FileWarning size={18} />} label="Failed files" value={monitoring?.failedFiles ?? 0} onClick={() => void loadRuns(0, 'COMPLETED_WITH_FAILURES')} />
        <MetricCard icon={<History size={18} />} label="Completed runs" value={monitoring?.completedRuns ?? 0} onClick={() => void loadRuns(0, 'COMPLETED')} />
        <MetricCard icon={<Hourglass size={18} />} label="Pending approvals" value={monitoring?.pendingDeletionApprovals ?? 0} />
        <MetricCard icon={<TrendingDown size={18} />} label="Historical savings" value={bytes(monitoring?.historicalSavingsBytes ?? 0)} onClick={() => void loadRuns(0, '')} />
      </section>

      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Run reports</h2>
            <p>{runFilter ? `Filtered by ${runFilter}` : 'All runs'}</p>
          </div>
          <Pagination page={runPage.page} totalPages={runPage.totalPages} onPage={(page) => void loadRuns(page)} />
        </div>
        <div className="search-row">
          <input value={runSearch} onChange={(e) => setRunSearch(e.target.value)} placeholder="Search run IDs or filenames" />
          <button onClick={() => void loadRuns(0, runFilter, runSearch)}>Search</button>
        </div>
        <div className="run-list">
          {runPage.content.map((run) => (
            <div key={run.id} className="run-row">
              <div>
                <button type="button" className="link-button" onClick={() => onOpenRun(run.id)}>
                  <CircleCheck size={15} /> {run.id.slice(0, 8)}
                </button>
                <RunMeta run={run} />
              </div>
              <span className="status-pill">{run.status}</span>
              <div>
                <ProgressBar value={run.progressPercent} />
                <small>
                  {run.completedFiles}/{run.totalFiles} files complete, {bytes(run.savedBytes)} saved
                </small>
              </div>
              <ArtifactLinks reportUrl={run.reportUrl} logUrl={run.logUrl} />
            </div>
          ))}
        </div>
      </section>

      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Precheck reports</h2>
            <p>Large inventories stay in generated HTML and plain-text logs.</p>
          </div>
          <Pagination page={precheckPage.page} totalPages={precheckPage.totalPages} onPage={(page) => void loadPrechecks(page)} />
        </div>
        <div className="search-row">
          <input value={precheckSearch} onChange={(e) => setPrecheckSearch(e.target.value)} placeholder="Search precheck IDs or filenames" />
          <button onClick={() => void loadPrechecks(0, precheckSearch)}>Search</button>
        </div>
        <div className="table precheck-report-table">
          {precheckPage.content.map((precheck) => (
            <div key={precheck.id}>
              <span>{precheck.id.slice(0, 8)}</span>
              <span className="status-pill">{precheck.status}</span>
              <span>{precheck.totalFiles} files</span>
              <span>{bytes(precheck.estimatedSavingsBytes)} estimated savings</span>
              <ArtifactLinks reportUrl={precheck.reportUrl} logUrl={precheck.logUrl} />
            </div>
          ))}
        </div>
      </section>
    </>
  )
}
