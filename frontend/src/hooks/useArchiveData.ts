import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, authHeaders } from '../api/client'
import { defaultSettings } from '../domain/defaults'
import type {
  ArchiveRunDto,
  AutomationDto,
  MediaDto,
  MonitoringDto,
  PageResponse,
  PolicyDto,
  PrecheckDto,
  PrecheckRunDto,
  RootDto,
  RunDto,
  SettingsDto,
  StorageRootScanProgressDto,
} from '../domain/types'

const emptyPage = <T,>(size = 25): PageResponse<T> => ({ content: [], page: 0, size, totalElements: 0, totalPages: 0 })

export function useArchiveData(token: string | null, onUnauthorized: (message: string) => void) {
  const [settings, setSettings] = useState<SettingsDto>(defaultSettings)
  const [roots, setRoots] = useState<RootDto[]>([])
  const [precheck, setPrecheck] = useState<PrecheckDto | null>(null)
  const [runs, setRuns] = useState<PageResponse<RunDto>>(emptyPage())
  const [archive, setArchive] = useState<PageResponse<MediaDto>>(emptyPage())
  const [archiveRuns, setArchiveRuns] = useState<PageResponse<ArchiveRunDto>>(emptyPage())
  const [pendingDeletion, setPendingDeletion] = useState<MediaDto[]>([])
  const [policies, setPolicies] = useState<PolicyDto[]>([])
  const [monitoring, setMonitoring] = useState<MonitoringDto | null>(null)
  const [prechecks, setPrechecks] = useState<PageResponse<PrecheckRunDto>>(emptyPage())
  const [automations, setAutomations] = useState<AutomationDto[]>([])
  const [rootScans, setRootScans] = useState<StorageRootScanProgressDto[]>([])
  const [message, setMessage] = useState('')

  const headers = useMemo(() => authHeaders(token), [token])

  const refresh = useCallback(async () => {
    if (!token) return
    try {
      const [settingsRes, rootsRes, runsRes, archiveRes, archiveRunsRes, pendingDeletionRes, policiesRes, monitoringRes, prechecksRes, automationsRes, rootScansRes] = await Promise.all([
        api.settings(headers),
        api.roots(headers),
        api.runs(headers),
        api.archive(headers),
        api.archiveRuns(headers),
        api.pendingDeletion(headers),
        api.policies(headers),
        api.monitoring(headers),
        api.prechecks(headers),
        api.automations(headers),
        api.rootScans(headers),
      ])
      setSettings(settingsRes)
      setRoots(rootsRes)
      setRuns(runsRes)
      setArchive(archiveRes)
      setArchiveRuns(archiveRunsRes)
      setPendingDeletion(pendingDeletionRes)
      setPolicies(policiesRes)
      setMonitoring(monitoringRes)
      setPrechecks(prechecksRes)
      setAutomations(automationsRes)
      setRootScans(rootScansRes)
    } catch (error) {
      if (error instanceof Response && error.status === 401) {
        onUnauthorized('Session expired. Sign in again.')
        return
      }
      setMessage('Could not refresh data.')
    }
  }, [headers, onUnauthorized, token])

  useEffect(() => {
    const id = window.setTimeout(() => {
      void refresh()
    }, 0)
    return () => window.clearTimeout(id)
  }, [refresh])

  return {
    headers,
    settings,
    setSettings,
    roots,
    setRoots,
    precheck,
    setPrecheck,
    runs,
    setRuns,
    archive,
    setArchive,
    archiveRuns,
    setArchiveRuns,
    pendingDeletion,
    policies,
    monitoring,
    prechecks,
    automations,
    rootScans,
    setRootScans,
    message,
    setMessage,
    refresh,
  }
}
