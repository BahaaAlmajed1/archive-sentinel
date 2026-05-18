package com.archivesentinel.service

import com.archivesentinel.api.MonitoringSummary
import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.OptimizationRunItemRepository
import com.archivesentinel.domain.OptimizationRunRepository
import com.archivesentinel.domain.RunStatus
import org.springframework.stereotype.Service

@Service
class MonitoringService(
    private val mediaFileRepository: MediaFileRepository,
    private val optimizationRunRepository: OptimizationRunRepository,
    private val optimizationRunItemRepository: OptimizationRunItemRepository,
    private val archiveService: ArchiveService,
) {
    fun summary() = MonitoringSummary(
        totalFiles = mediaFileRepository.count(),
        archivedFiles = mediaFileRepository.countByStatus(MediaStatus.ARCHIVED) +
            mediaFileRepository.countByStatus(MediaStatus.AWAITING_DELETION_APPROVAL),
        failedFiles = mediaFileRepository.countByStatus(MediaStatus.FAILED),
        completedRuns = optimizationRunRepository.countByStatus(RunStatus.COMPLETED) +
            optimizationRunRepository.countByStatus(RunStatus.COMPLETED_WITH_FAILURES),
        pendingDeletionApprovals = archiveService.pendingDeletionApprovals().size.toLong(),
        historicalSavingsBytes = optimizationRunItemRepository.totalSavedBytesForRunStatuses(
            listOf(RunStatus.COMPLETED, RunStatus.COMPLETED_WITH_FAILURES),
        ),
    )
}
