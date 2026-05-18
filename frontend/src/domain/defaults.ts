import type { SettingsDto } from './types'

export const defaultSettings: SettingsDto = {
  setupCompleted: false,
  executionMode: 'HOST_NATIVE',
  tdarrMode: 'EXISTING',
  tdarrBaseUrl: 'http://localhost:8266',
  managedTdarrEnabled: false,
  archiveRoot: 'runtime/archive',
  optimizedRoot: 'runtime/optimized',
  stagingEnabled: true,
  stagingRoot: 'runtime/staging',
  defaultRetentionDays: 90,
  defaultDeletionMode: 'MANUAL',
  defaultOutputMode: 'PARALLEL',
  precheckExtensionOptions: ['mkv', 'mp4', 'mov', 'avi', 'm4v', 'webm', 'mpg', 'mpeg', 'wmv', 'm2ts', 'ts'],
  enabledExtensions: ['mkv', 'mp4', 'mov', 'avi', 'm4v', 'webm', 'mpg', 'mpeg', 'wmv', 'm2ts', 'ts'],
  estimatedOutputRatioPercent: 65,
  outputContainerExtension: 'mkv',
  durationToleranceSeconds: 0.5,
  tdarrCodecsToExclude: 'hevc',
  tdarrTranscodeArguments: ',-map 0 -map_metadata 0 -map_chapters 0 -c:v hevc_nvenc -preset p5 -cq 28 -c:a copy -c:s copy',
  mediaFfmpegPath: '',
  mediaFfprobePath: '',
  reportIntervalHours: 24,
  reportRecipients: '',
  smtpHost: '',
  smtpPort: 25,
  smtpUsername: '',
  smtpPassword: '',
  smtpAuth: false,
  smtpStartTls: false,
  smtpFrom: 'archive-sentinel@localhost',
  scanThreads: 4,
  analysisThreads: 2,
  validationThreads: 2,
  fileOpsThreads: 2,
  tdarrSubmissionConcurrency: 10,
  backgroundScanRefreshMinutes: 15,
  maxAvailableThreads: navigator.hardwareConcurrency || 4,
}

export const bytes = (value: number) => {
  const safe = Math.max(0, value)
  const gb = safe / 1024 / 1024 / 1024
  const mb = safe / 1024 / 1024
  return gb >= 1 ? `${gb.toFixed(2)} GB` : `${mb.toFixed(2)} MB`
}

export const gigabytes = (value: number) => `${(Math.max(0, value) / 1024 / 1024 / 1024).toFixed(4)} GB`
