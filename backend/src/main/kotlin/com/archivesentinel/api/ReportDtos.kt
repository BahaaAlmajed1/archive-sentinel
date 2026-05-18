package com.archivesentinel.api

import com.archivesentinel.domain.PrecheckStatus
import java.time.Instant
import java.util.UUID

data class MonitoringSummary(
    val totalFiles: Long,
    val archivedFiles: Long,
    val failedFiles: Long,
    val completedRuns: Long,
    val pendingDeletionApprovals: Long,
    val historicalSavingsBytes: Long,
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class PrecheckRunResponse(
    val id: UUID,
    val status: PrecheckStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    val totalFiles: Int,
    val scannedFiles: Int,
    val progressPercent: Int,
    val progressMessage: String,
    val totalBytes: Long,
    val estimatedSavingsBytes: Long,
    val reportUrl: String?,
    val logUrl: String?,
    val errorMessage: String?,
)
