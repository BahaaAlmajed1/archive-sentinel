import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Archive, CalendarClock, CircleCheck, FolderKanban, Gauge, ListChecks, LogOut, RefreshCw, Settings, ShieldCheck } from 'lucide-react'
import { api } from './api/client'
import './App.css'
import { useArchiveData } from './hooks/useArchiveData'
import { ArchivePage } from './pages/ArchivePage'
import { AutomationPage } from './pages/AutomationPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { PoliciesPage } from './pages/PoliciesPage'
import { ReportsPage } from './pages/ReportsPage'
import { RunDetailsPage } from './pages/RunDetailsPage'
import { SettingsForm, SettingsPage } from './pages/SettingsPage'

type Tab = 'dashboard' | 'policies' | 'archive' | 'automation' | 'reports' | 'settings' | 'runDetails'

export default function App() {
  const [token, setToken] = useState(localStorage.getItem('token'))
  const [setupCompleted, setSetupCompleted] = useState<boolean | null>(null)
  const [activeTab, setActiveTab] = useState<Tab>(() => (localStorage.getItem('activeTab') as Tab | null) ?? 'dashboard')
  const [selectedRunId, setSelectedRunId] = useState<string | null>(() => localStorage.getItem('selectedRunId'))
  const [authMessage, setAuthMessage] = useState('')
  const [dashboardMessage, setDashboardMessage] = useState('')
  const [showProcesses, setShowProcesses] = useState(false)
  const previousRunningProcessCount = useRef(0)

  const logout = useCallback((message?: string) => {
    localStorage.removeItem('token')
    setToken(null)
    if (message) setAuthMessage(message)
  }, [])

  const data = useArchiveData(token, logout)
  const runningRuns = data.runs.content.filter((run) => ['QUEUED', 'RUNNING'].includes(run.status))
  const runningPrechecks = data.prechecks.content.filter((precheck) => precheck.status === 'RUNNING')
  const runningRootScans = data.rootScans.filter((scan) => ['QUEUED', 'RUNNING'].includes(scan.status))
  const runningProcessCount = runningRuns.length + runningPrechecks.length + runningRootScans.length

  useEffect(() => {
    api.setupStatus().then((result) => setSetupCompleted(result.setupCompleted))
  }, [])

  useEffect(() => {
    localStorage.setItem('activeTab', activeTab)
  }, [activeTab])

  useEffect(() => {
    if (selectedRunId) localStorage.setItem('selectedRunId', selectedRunId)
    else localStorage.removeItem('selectedRunId')
  }, [selectedRunId])

  useEffect(() => {
    if (!token || runningProcessCount === 0) return undefined
    const id = window.setInterval(() => {
      void data.refresh()
    }, 4000)
    return () => window.clearInterval(id)
  }, [data.refresh, runningProcessCount, token])

  useEffect(() => {
    if (token && previousRunningProcessCount.current > 0 && runningProcessCount === 0) {
      void data.refresh()
    }
    previousRunningProcessCount.current = runningProcessCount
  }, [data.refresh, runningProcessCount, token])

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!event.currentTarget.checkValidity()) {
      event.currentTarget.classList.add('validated')
      event.currentTarget.reportValidity()
      return
    }
    const form = new FormData(event.currentTarget)
    try {
      const body = await api.login(form.get('username'), form.get('password'))
      localStorage.setItem('token', body.token)
      setAuthMessage('')
      setToken(body.token)
    } catch {
      data.setMessage('Login failed.')
    }
  }

  async function saveSettings(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!event.currentTarget.checkValidity()) {
      event.currentTarget.classList.add('validated')
      event.currentTarget.reportValidity()
      return
    }
    try {
      data.setSettings(await api.saveSettings(data.settings, data.headers))
      setSetupCompleted(true)
      data.setMessage('Settings saved.')
      await data.refresh()
    } catch {
      data.setMessage('Could not save settings.')
    }
  }

  function openRun(id: string) {
    setSelectedRunId(id)
    setActiveTab('runDetails')
  }

  if (setupCompleted === null) return <div className="centered">Loading...</div>
  if (!token) return <LoginPage message={data.message || authMessage} onLogin={login} />

  if (!setupCompleted) {
    return (
      <main className="setup-shell">
        <SettingsForm title="First-run setup" settings={data.settings} setSettings={data.setSettings} onSubmit={saveSettings} headers={data.headers} />
      </main>
    )
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <ShieldCheck size={22} />
          <span>Archive Sentinel</span>
        </div>
        <nav>
          <NavButton active={activeTab === 'dashboard'} onClick={() => setActiveTab('dashboard')} icon={<Gauge size={18} />} label="Dashboard" />
          <NavButton active={activeTab === 'archive'} onClick={() => setActiveTab('archive')} icon={<Archive size={18} />} label="Archive" />
          <NavButton active={activeTab === 'policies'} onClick={() => setActiveTab('policies')} icon={<FolderKanban size={18} />} label="Policies" />
          <NavButton active={activeTab === 'automation'} onClick={() => setActiveTab('automation')} icon={<CalendarClock size={18} />} label="Automation" />
          <NavButton active={activeTab === 'reports' || activeTab === 'runDetails'} onClick={() => setActiveTab('reports')} icon={<CircleCheck size={18} />} label="Reports" />
          <NavButton active={activeTab === 'settings'} onClick={() => setActiveTab('settings')} icon={<Settings size={18} />} label="Settings" />
        </nav>
        <div className="process-popover-wrap">
          <button className="process-button" onClick={() => setShowProcesses((current) => !current)}>
            <ListChecks size={18} /> Processes {runningProcessCount > 0 && <span>{runningProcessCount}</span>}
          </button>
          {showProcesses && (
            <div className="process-popover">
              <strong>Running processes</strong>
              {runningProcessCount === 0 && <small>No running work.</small>}
              {runningPrechecks.map((precheck) => (
                <div key={precheck.id}>
                  <span>Scan {precheck.id.slice(0, 8)}</span>
                  <small>{precheck.progressMessage}</small>
                  <small>
                    {precheck.scannedFiles}/{precheck.totalFiles} files, {precheck.progressPercent}%
                  </small>
                </div>
              ))}
              {runningRootScans.map((scan) => (
                <div key={scan.rootId}>
                  <span>Root scan {scan.rootId.slice(0, 8)}</span>
                  <small>{scan.message}</small>
                  <small>
                    {scan.scannedFiles}/{scan.totalFiles} files, {scan.progressPercent}%
                  </small>
                </div>
              ))}
              {runningRuns.map((run) => (
                <div key={run.id}>
                  <span>Optimization {run.id.slice(0, 8)}</span>
                  <small>
                    {run.completedFiles}/{run.totalFiles} files, {run.progressPercent}%
                  </small>
                </div>
              ))}
            </div>
          )}
        </div>
        <button className="logout" onClick={() => logout()}>
          <LogOut size={18} /> Logout
        </button>
      </aside>

      <section className="workspace">
        {(activeTab === 'dashboard' ? dashboardMessage : data.message) && (
          <div className="toast">
            <span>{activeTab === 'dashboard' ? dashboardMessage : data.message}</span>
            <button type="button" onClick={() => (activeTab === 'dashboard' ? setDashboardMessage('') : data.setMessage(''))}>
              <RefreshCw size={14} /> Clear
            </button>
          </div>
        )}
        {activeTab === 'dashboard' && (
          <DashboardPage
            headers={data.headers}
            roots={data.roots}
            setRoots={data.setRoots}
            monitoring={data.monitoring}
            precheck={data.precheck}
            setPrecheck={data.setPrecheck}
            runs={data.runs.content}
            rootScans={data.rootScans}
            setRootScans={data.setRootScans}
            runningPrechecks={runningPrechecks}
            refresh={data.refresh}
            setMessage={setDashboardMessage}
            onOpenRun={openRun}
          />
        )}
        {activeTab === 'archive' && (
          <ArchivePage
            headers={data.headers}
            archiveRuns={data.archiveRuns}
            pendingDeletion={data.pendingDeletion}
            setArchiveRuns={data.setArchiveRuns}
            refresh={data.refresh}
          />
        )}
        {activeTab === 'policies' && <PoliciesPage headers={data.headers} policies={data.policies} settings={data.settings} refresh={data.refresh} />}
        {activeTab === 'automation' && (
          <AutomationPage headers={data.headers} roots={data.roots} automations={data.automations} refresh={data.refresh} />
        )}
        {activeTab === 'reports' && (
          <ReportsPage headers={data.headers} monitoring={data.monitoring} runs={data.runs} prechecks={data.prechecks} onOpenRun={openRun} refresh={data.refresh} />
        )}
        {activeTab === 'settings' && (
          <SettingsPage
            headers={data.headers}
            settings={data.settings}
            setSettings={data.setSettings}
            onSubmit={saveSettings}
            onPasswordMessage={data.setMessage}
            onDataReset={data.refresh}
          />
        )}
        {activeTab === 'runDetails' && selectedRunId && <RunDetailsPage id={selectedRunId} headers={data.headers} onBack={() => setActiveTab('reports')} />}
        {activeTab === 'runDetails' && !selectedRunId && (
          <ReportsPage headers={data.headers} monitoring={data.monitoring} runs={data.runs} prechecks={data.prechecks} onOpenRun={openRun} refresh={data.refresh} />
        )}
      </section>
    </main>
  )
}

function NavButton({ active, onClick, icon, label }: { active: boolean; onClick: () => void; icon: ReactNode; label: string }) {
  return (
    <button onClick={onClick} className={active ? 'active' : ''}>
      {icon} {label}
    </button>
  )
}
