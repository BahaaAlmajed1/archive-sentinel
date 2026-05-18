export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type SettingsDto = {
  setupCompleted: boolean
  executionMode: 'HOST_NATIVE' | 'DOCKER'
  tdarrMode: 'EXISTING' | 'MANAGED'
  tdarrBaseUrl: string
  managedTdarrEnabled: boolean
  archiveRoot: string
  optimizedRoot: string
  stagingEnabled: boolean
  stagingRoot: string | null
  defaultRetentionDays: number
  defaultDeletionMode: 'MANUAL' | 'AUTOMATIC'
  defaultOutputMode: 'PARALLEL' | 'SAME_LIBRARY'
  precheckExtensionOptions: string[]
  enabledExtensions: string[]
  estimatedOutputRatioPercent: number
  outputContainerExtension: string
  durationToleranceSeconds: number
  tdarrCodecsToExclude: string
  tdarrTranscodeArguments: string
  mediaFfmpegPath: string
  mediaFfprobePath: string
  reportIntervalHours: number
  reportRecipients: string
  smtpHost: string
  smtpPort: number
  smtpUsername: string
  smtpPassword: string
  smtpAuth: boolean
  smtpStartTls: boolean
  smtpFrom: string
  scanThreads: number
  analysisThreads: number
  validationThreads: number
  fileOpsThreads: number
  tdarrSubmissionConcurrency: number
  backgroundScanRefreshMinutes: number
  maxAvailableThreads: number
}

export type RootDto = {
  id: string
  label: string
  path: string
  enabled: boolean
  optimizedRootOverride: string | null
  archiveRootOverride: string | null
  lastScannedAt: string | null
  scanStatus: 'SCANNED' | 'NOT_SCANNED'
  changesDetected: boolean
  autoRescanEnabled: boolean
  effectiveRetentionDays: number
  effectiveDeletionMode: 'MANUAL' | 'AUTOMATIC'
  effectiveOutputMode: 'PARALLEL' | 'SAME_LIBRARY'
  activePolicies: PolicyDto[]
}

export type PrecheckDto = {
  id: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  scannedFiles: number
  progressMessage: string
  progressPercent: number
  totalFiles: number
  totalBytes: number
  estimatedOutputBytes: number
  estimatedSavingsBytes: number
  reportUrl: string | null
  logUrl: string | null
  selectedFiles: number
  roots: Array<{ id: string; label: string; path: string; totalFiles: number; selectedFiles: number; unselectedFiles: number }>
  folders: Array<{ path: string; totalFiles: number; selectedFiles: number; unselectedFiles: number }>
  files: PageResponse<{
    id: string
    mediaFileId: string
    storageRootId: string
    path: string
    sizeBytes: number
    relativePath: string
    folderPath: string
    status: string
    selected: boolean
    defaultSelected: boolean
    everOptimized: boolean
  }>
}

export type StorageRootScanProgressDto = {
  rootId: string
  status: string
  totalFiles: number
  scannedFiles: number
  remainingFiles: number
  progressPercent: number
  message: string
}

export type RunDto = {
  id: string
  status: string
  totalFiles: number
  completedFiles: number
  failedFiles: number
  totalInputBytes: number
  totalOutputBytes: number
  savedBytes: number
  progressPercent: number
  startedAt: string
  completedAt: string | null
  reportUrl: string | null
  logUrl: string | null
}

export type RunItemDto = {
  id: string
  mediaFileId: string | null
  storageRootId: string | null
  storageRootLabel: string | null
  storageRootPath: string | null
  optimizedRoot: string | null
  archiveRoot: string | null
  status: string
  originalPath: string
  candidatePath: string | null
  optimizedPath: string | null
  archivedPath: string | null
  inputBytes: number
  outputBytes: number
  savedBytes: number
  errorMessage: string | null
  validationJson: string | null
}

export type RunDetailsDto = { run: RunDto; items: PageResponse<RunItemDto> }

export type MediaDto = {
  id: string
  originalPath: string
  optimizedPath: string | null
  archivedPath: string | null
  status: string
  sizeBytes: number
  retentionUntil: string | null
  validationJson: string | null
}

export type PolicyDto = {
  id: string
  path: string
  targetType: 'FOLDER' | 'FILE'
  excluded: boolean
  recursive: boolean
  excludedExtensions: string | null
  retentionDays: number | null
  deletionMode: 'MANUAL' | 'AUTOMATIC' | null
  outputMode: 'PARALLEL' | 'SAME_LIBRARY' | null
}

export type MonitoringDto = {
  totalFiles: number
  archivedFiles: number
  failedFiles: number
  completedRuns: number
  pendingDeletionApprovals: number
  historicalSavingsBytes: number
}

export type PrecheckRunDto = {
  id: string
  status: string
  startedAt: string
  completedAt: string | null
  totalFiles: number
  scannedFiles: number
  progressPercent: number
  progressMessage: string
  totalBytes: number
  estimatedSavingsBytes: number
  reportUrl: string | null
  logUrl: string | null
  errorMessage: string | null
}

export type NativePickerResponse = { paths: string[]; cancelled: boolean; message: string | null }

export type AutomationDto = {
  id: string
  storageRootId: string
  enabled: boolean
  intervalMinutes: number
  lastRunAt: string | null
}

export type ArchiveRunDto = {
  run: RunDto
  recoverableFiles: number
  restoredFiles: number
}

export type ArchiveRunTreeDto = {
  folders: Array<{ path: string; totalFiles: number; selectedFiles: number; unselectedFiles: number }>
  files: PageResponse<MediaDto>
}
