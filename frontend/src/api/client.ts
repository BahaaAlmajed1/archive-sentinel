import type {
  MediaDto,
  AutomationDto,
  ArchiveRunDto,
  ArchiveRunTreeDto,
  MonitoringDto,
  NativePickerResponse,
  PageResponse,
  PolicyDto,
  PrecheckDto,
  PrecheckRunDto,
  RootDto,
  RunDetailsDto,
  RunDto,
  SettingsDto,
  StorageRootScanProgressDto,
} from '../domain/types'

export type ApiHeaders = Record<string, string>

export async function fetchJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const response = await fetch(input, init)
  if (!response.ok) throw response
  const body = await response.text()
  return body ? (JSON.parse(body) as T) : (null as T)
}

export const authHeaders = (token: string | null): ApiHeaders => ({
  'Content-Type': 'application/json',
  ...(token ? { Authorization: `Bearer ${token}` } : {}),
})

export const api = {
  setupStatus: () => fetchJson<{ setupCompleted: boolean }>('/api/setup/status'),
  login: (username: FormDataEntryValue | null, password: FormDataEntryValue | null) =>
    fetchJson<{ token: string; username: string }>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    }),
  settings: (headers: ApiHeaders) => fetchJson<SettingsDto>('/api/settings', { headers }),
  saveSettings: (settings: SettingsDto, headers: ApiHeaders) =>
    fetchJson<SettingsDto>('/api/settings', { method: 'PUT', headers, body: JSON.stringify(settings) }),
  roots: (headers: ApiHeaders) => fetchJson<RootDto[]>('/api/storage-roots', { headers }),
  addRoot: (
    root: {
      label: string
      path: string
      enabled?: boolean
      optimizedRootOverride?: string | null
      archiveRootOverride?: string | null
      autoRescanEnabled?: boolean
    },
    headers: ApiHeaders,
  ) =>
    fetchJson<RootDto>('/api/storage-roots', { method: 'POST', headers, body: JSON.stringify({ enabled: true, ...root }) }),
  updateRoot: (root: RootDto, headers: ApiHeaders) =>
    fetchJson<RootDto>(`/api/storage-roots/${root.id}`, { method: 'PUT', headers, body: JSON.stringify(root) }),
  deleteRoot: (id: string, headers: ApiHeaders) => fetch(`/api/storage-roots/${id}`, { method: 'DELETE', headers }),
  policies: (headers: ApiHeaders) => fetchJson<PolicyDto[]>('/api/policies', { headers }),
  addPolicy: (policy: Omit<PolicyDto, 'id'>, headers: ApiHeaders) =>
    fetchJson<PolicyDto>('/api/policies', { method: 'POST', headers, body: JSON.stringify(policy) }),
  updatePolicy: (policy: PolicyDto, headers: ApiHeaders) =>
    fetchJson<PolicyDto>(`/api/policies/${policy.id}`, { method: 'PUT', headers, body: JSON.stringify(policy) }),
  deletePolicy: (id: string, headers: ApiHeaders) => fetch(`/api/policies/${id}`, { method: 'DELETE', headers }),
  precheck: (
    rootIds: string[],
    overrides: Array<{
      rootId: string
      retentionDays: number | null
      deletionMode: string | null
      outputMode: string | null
      optimizedRootOverride: string | null
      archiveRootOverride: string | null
    }>,
    headers: ApiHeaders,
  ) =>
    fetchJson<PrecheckDto>('/api/precheck/async', { method: 'POST', headers, body: JSON.stringify({ rootIds, overrides }) }),
  precheckDetails: (id: string, headers: ApiHeaders, page = 0, size = 50, rootId = '', search = '') =>
    fetchJson<PrecheckDto>(
      `/api/precheck/${id}?page=${page}&size=${size}${rootId ? `&rootId=${encodeURIComponent(rootId)}` : ''}${search ? `&search=${encodeURIComponent(search)}` : ''}`,
      { headers },
    ),
  precheckFolderFiles: (id: string, headers: ApiHeaders, folderPath: string, page = 0, size = 100, rootId = '', search = '') =>
    fetchJson<PrecheckDto['files']>(
      `/api/precheck/${id}/folders/files?page=${page}&size=${size}&folderPath=${encodeURIComponent(folderPath)}${rootId ? `&rootId=${encodeURIComponent(rootId)}` : ''}${search ? `&search=${encodeURIComponent(search)}` : ''}`,
      { headers },
    ),
  updatePrecheckSelection: (id: string, selected: boolean, headers: ApiHeaders) =>
    fetchJson<PrecheckDto['files']['content'][number]>(`/api/precheck/items/${id}/selection`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ selected }),
    }),
  updateAllPrecheckSelections: (id: string, selected: boolean, headers: ApiHeaders) =>
    fetchJson<PrecheckDto>(`/api/precheck/${id}/selection`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ selected }),
    }),
  updatePrecheckFolderSelection: (id: string, rootId: string, folderPath: string, selected: boolean, headers: ApiHeaders) =>
    fetchJson<PrecheckDto>(`/api/precheck/${id}/folders/selection`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ rootId, folderPath, selected }),
    }),
  startRun: (body: { mediaFileIds?: string[]; precheckId?: string }, headers: ApiHeaders) =>
    fetchJson<RunDto>('/api/runs', { method: 'POST', headers, body: JSON.stringify(body) }),
  cancelRun: (id: string, headers: ApiHeaders) => fetchJson<RunDto>(`/api/runs/${id}/cancel`, { method: 'POST', headers }),
  runs: (headers: ApiHeaders, status = '', page = 0, size = 25, search = '') =>
    fetchJson<PageResponse<RunDto>>(
      `/api/runs?page=${page}&size=${size}${status ? `&status=${status}` : ''}${search ? `&search=${encodeURIComponent(search)}` : ''}`,
      { headers },
    ),
  runDetails: (id: string, headers: ApiHeaders, page = 0, size = 100, search = '') =>
    fetchJson<RunDetailsDto>(`/api/runs/${id}?page=${page}&size=${size}${search ? `&search=${encodeURIComponent(search)}` : ''}`, { headers }),
  archive: (headers: ApiHeaders, page = 0, size = 25) =>
    fetchJson<PageResponse<MediaDto>>(`/api/archive?page=${page}&size=${size}`, { headers }),
  archiveRuns: (headers: ApiHeaders, page = 0, size = 25) =>
    fetchJson<PageResponse<ArchiveRunDto>>(`/api/archive/runs?page=${page}&size=${size}`, { headers }),
  archiveRunFiles: (id: string, headers: ApiHeaders, page = 0, size = 50, search = '', folder = '') =>
    fetchJson<ArchiveRunTreeDto>(
      `/api/archive/runs/${id}?page=${page}&size=${size}${search ? `&search=${encodeURIComponent(search)}` : ''}${folder ? `&folder=${encodeURIComponent(folder)}` : ''}`,
      { headers },
    ),
  pendingDeletion: (headers: ApiHeaders) => fetchJson<MediaDto[]>('/api/archive/pending-deletion', { headers }),
  restore: (id: string, headers: ApiHeaders) => fetchJson<MediaDto>(`/api/archive/${id}/restore`, { method: 'POST', headers }),
  restoreRun: (id: string, headers: ApiHeaders) =>
    fetchJson<MediaDto[]>(`/api/archive/runs/${id}/restore`, { method: 'POST', headers }),
  approveDeletion: (id: string, headers: ApiHeaders) =>
    fetchJson<MediaDto>(`/api/archive/${id}/approve-deletion`, { method: 'POST', headers }),
  monitoring: (headers: ApiHeaders) => fetchJson<MonitoringDto>('/api/reports/summary', { headers }),
  prechecks: (headers: ApiHeaders, page = 0, size = 25, search = '') =>
    fetchJson<PageResponse<PrecheckRunDto>>(`/api/reports/prechecks?page=${page}&size=${size}${search ? `&search=${encodeURIComponent(search)}` : ''}`, {
      headers,
    }),
  automations: (headers: ApiHeaders) => fetchJson<AutomationDto[]>('/api/automations', { headers }),
  upsertAutomation: (body: { storageRootId: string; enabled: boolean; intervalMinutes: number }, headers: ApiHeaders) =>
    fetchJson<AutomationDto>('/api/automations', { method: 'POST', headers, body: JSON.stringify(body) }),
  deleteAutomation: (id: string, headers: ApiHeaders) => fetch(`/api/automations/${id}`, { method: 'DELETE', headers }),
  pickFolders: (headers: ApiHeaders, initialPath?: string) =>
    fetchJson<NativePickerResponse>('/api/pickers/folders', {
      method: 'POST',
      headers,
      body: JSON.stringify({ multiple: true, initialPath }),
    }),
  pickFiles: (headers: ApiHeaders, initialPath?: string) =>
    fetchJson<NativePickerResponse>('/api/pickers/files', {
      method: 'POST',
      headers,
      body: JSON.stringify({ multiple: true, initialPath }),
    }),
  changePassword: (body: { currentPassword: FormDataEntryValue | null; newPassword: FormDataEntryValue | null }, headers: ApiHeaders) =>
    fetch('/api/auth/change-password', { method: 'POST', headers, body: JSON.stringify(body) }),
  clearTdarrQueue: (headers: ApiHeaders) => fetchJson<{ clearedPaths: number }>('/api/tdarr/clear-queue', { method: 'POST', headers }),
  rootScans: (headers: ApiHeaders) => fetchJson<StorageRootScanProgressDto[]>('/api/storage-roots/scans', { headers }),
  scanRoot: (id: string, headers: ApiHeaders) => fetchJson<StorageRootScanProgressDto>(`/api/storage-roots/${id}/scan`, { method: 'POST', headers }),
  dismissRootScan: (id: string, headers: ApiHeaders) => fetch(`/api/storage-roots/scans/${id}`, { method: 'DELETE', headers }),
  rootScanHistory: (id: string, headers: ApiHeaders) => fetchJson<PrecheckRunDto[]>(`/api/storage-roots/${id}/scan-history`, { headers }),
}
