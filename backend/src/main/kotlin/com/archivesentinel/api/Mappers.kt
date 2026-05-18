package com.archivesentinel.api

import com.archivesentinel.domain.AppSettings
import com.archivesentinel.domain.MediaFile
import com.archivesentinel.domain.OptimizationRun
import com.archivesentinel.domain.OptimizationRunItem
import com.archivesentinel.domain.PolicyTarget
import com.archivesentinel.domain.PrecheckRun
import com.archivesentinel.domain.StorageRoot
import com.archivesentinel.domain.AutomationRule
import com.archivesentinel.service.ReportService
import com.archivesentinel.service.parseExtensions
import org.springframework.data.domain.Page

fun AppSettings.toResponse() = SettingsResponse(
    setupCompleted = setupCompleted,
    executionMode = executionMode,
    tdarrMode = tdarrMode,
    tdarrBaseUrl = tdarrBaseUrl,
    managedTdarrEnabled = managedTdarrEnabled,
    archiveRoot = archiveRoot,
    optimizedRoot = optimizedRoot,
    stagingEnabled = stagingEnabled,
    stagingRoot = stagingRoot,
    defaultRetentionDays = defaultRetentionDays,
    defaultDeletionMode = defaultDeletionMode,
    defaultOutputMode = defaultOutputMode,
    precheckExtensionOptions = parseExtensions(precheckExtensionOptions).sorted(),
    enabledExtensions = parseExtensions(enabledExtensions).sorted(),
    estimatedOutputRatioPercent = estimatedOutputRatioPercent,
    outputContainerExtension = outputContainerExtension,
    durationToleranceSeconds = durationToleranceSeconds,
    tdarrCodecsToExclude = tdarrCodecsToExclude,
    tdarrTranscodeArguments = tdarrTranscodeArguments,
    mediaFfmpegPath = mediaFfmpegPath,
    mediaFfprobePath = mediaFfprobePath,
    reportIntervalHours = reportIntervalHours,
    reportRecipients = reportRecipients,
    smtpHost = smtpHost,
    smtpPort = smtpPort,
    smtpUsername = smtpUsername,
    smtpPassword = smtpPassword,
    smtpAuth = smtpAuth,
    smtpStartTls = smtpStartTls,
    smtpFrom = smtpFrom,
    scanThreads = scanThreads,
    analysisThreads = analysisThreads,
    validationThreads = validationThreads,
    fileOpsThreads = fileOpsThreads,
    tdarrSubmissionConcurrency = tdarrSubmissionConcurrency,
    backgroundScanRefreshMinutes = backgroundScanRefreshMinutes,
    maxAvailableThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
)

fun StorageRoot.toResponse(
    effectiveRetentionDays: Int,
    effectiveDeletionMode: com.archivesentinel.domain.DeletionMode,
    effectiveOutputMode: com.archivesentinel.domain.OutputMode,
    activePolicies: List<PolicyTarget>,
    changesDetected: Boolean = false,
) = StorageRootResponse(
    id,
    label,
    path,
    enabled,
    optimizedRootOverride,
    archiveRootOverride,
    lastScannedAt,
    if (lastScannedAt == null) "NOT_SCANNED" else "SCANNED",
    changesDetected,
    autoRescanEnabled,
    effectiveRetentionDays,
    effectiveDeletionMode,
    effectiveOutputMode,
    activePolicies.map { it.toResponse() },
)

fun PolicyTarget.toResponse() = PolicyTargetResponse(
    id,
    path,
    targetType,
    excluded,
    recursive,
    excludedExtensions,
    retentionDays,
    deletionMode,
    outputMode,
)

fun OptimizationRun.toResponse(reportService: ReportService, savedBytesOverride: Long? = null) = RunResponse(
    id = id,
    status = status,
    totalFiles = totalFiles,
    completedFiles = completedFiles,
    failedFiles = failedFiles,
    totalInputBytes = totalInputBytes,
    totalOutputBytes = totalOutputBytes,
    savedBytes = savedBytesOverride ?: 0,
    progressPercent = if (totalFiles == 0) 100 else (((completedFiles + failedFiles).toDouble() / totalFiles) * 100).toInt().coerceIn(0, 100),
    startedAt = startedAt,
    completedAt = completedAt,
    reportUrl = reportPath?.let { reportService.artifactUrl("run", id) },
    logUrl = logPath?.let { reportService.artifactUrl("run-log", id) },
)

fun OptimizationRunItem.toResponse() = RunItemResponse(
    id = id,
    mediaFileId = mediaFileId,
    storageRootId = storageRootId,
    storageRootLabel = storageRootLabel,
    storageRootPath = storageRootPath,
    optimizedRoot = optimizedRoot,
    archiveRoot = archiveRoot,
    status = status,
    originalPath = originalPath,
    candidatePath = candidatePath,
    optimizedPath = optimizedPath,
    archivedPath = archivedPath,
    inputBytes = inputBytes,
    outputBytes = outputBytes,
    savedBytes = savedBytes,
    errorMessage = errorMessage,
    validationJson = validationJson,
)

fun MediaFile.toResponse() = MediaFileResponse(
    id,
    originalPath,
    optimizedPath,
    archivedPath,
    status,
    sizeBytes,
    retentionUntil,
    validationJson,
)

fun PrecheckRun.toResponse(reportService: ReportService) = PrecheckRunResponse(
    id = id,
    status = status,
    startedAt = startedAt,
    completedAt = completedAt,
    totalFiles = totalFiles,
    scannedFiles = scannedFiles,
    progressPercent = when {
        status == com.archivesentinel.domain.PrecheckStatus.COMPLETED -> 100
        totalFiles <= 0 -> 0
        else -> ((scannedFiles.toDouble() / totalFiles) * 100).toInt().coerceIn(0, 100)
    },
    progressMessage = progressMessage,
    totalBytes = totalBytes,
    estimatedSavingsBytes = estimatedSavingsBytes,
    reportUrl = reportPath?.let { reportService.artifactUrl("precheck", id) },
    logUrl = logPath?.let { reportService.artifactUrl("precheck-log", id) },
    errorMessage = errorMessage,
)

fun AutomationRule.toResponse() = AutomationRuleResponse(
    id = id,
    storageRootId = storageRootId,
    enabled = enabled,
    intervalMinutes = intervalMinutes,
    lastRunAt = lastRunAt,
)

fun <T, R> Page<T>.toPageResponse(mapper: (T) -> R) = PageResponse(
    content = content.map(mapper),
    page = number,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
)
