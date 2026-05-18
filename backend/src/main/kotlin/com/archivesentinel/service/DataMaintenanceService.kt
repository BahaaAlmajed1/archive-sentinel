package com.archivesentinel.service

import com.archivesentinel.api.DataResetResponse
import com.archivesentinel.api.DeleteRunResponse
import com.archivesentinel.domain.AuditEventRepository
import com.archivesentinel.domain.AutomationRuleRepository
import com.archivesentinel.domain.MediaFile
import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.OptimizationRunItem
import com.archivesentinel.domain.OptimizationRunItemRepository
import com.archivesentinel.domain.OptimizationRunRepository
import com.archivesentinel.domain.PolicyTargetRepository
import com.archivesentinel.domain.PrecheckRunItemRepository
import com.archivesentinel.domain.PrecheckRunRepository
import com.archivesentinel.domain.RunStatus
import com.archivesentinel.domain.StorageRootRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Comparator
import java.util.UUID
import kotlin.io.path.exists

@Service
class DataMaintenanceService(
    private val mediaFileRepository: MediaFileRepository,
    private val storageRootRepository: StorageRootRepository,
    private val policyTargetRepository: PolicyTargetRepository,
    private val automationRuleRepository: AutomationRuleRepository,
    private val optimizationRunRepository: OptimizationRunRepository,
    private val optimizationRunItemRepository: OptimizationRunItemRepository,
    private val precheckRunRepository: PrecheckRunRepository,
    private val precheckRunItemRepository: PrecheckRunItemRepository,
    private val auditEventRepository: AuditEventRepository,
    private val exampleDataService: ExampleDataService,
    private val appPathService: AppPathService,
    @Value("\${app.reports.root}") private val reportsRoot: String,
) {
    @Transactional
    fun deleteRun(id: UUID): DeleteRunResponse {
        val run = optimizationRunRepository.findById(id).orElseThrow()
        require(run.status !in activeRunStatuses) { "Cancel or finish the run before deleting it." }
        val items = optimizationRunItemRepository.findByRunId(id)
        val affectedMediaIds = items.mapNotNull { it.mediaFileId }.distinct()
        val artifactPaths = listOfNotNull(run.reportPath, run.logPath)

        optimizationRunItemRepository.deleteByRunId(id)
        optimizationRunRepository.delete(run)
        recalculateCompressionHistory(affectedMediaIds)
        deleteArtifacts(artifactPaths)

        return DeleteRunResponse(
            deletedRunId = id,
            deletedItems = items.size,
            recalculatedMediaFiles = affectedMediaIds.size,
        )
    }

    @Transactional
    fun clearAllData(): DataResetResponse {
        val runCount = optimizationRunRepository.count()
        val precheckCount = precheckRunRepository.count()
        val mediaCount = mediaFileRepository.count()
        val artifactPaths = optimizationRunRepository.findAll().flatMap { listOfNotNull(it.reportPath, it.logPath) } +
            precheckRunRepository.findAll().flatMap { listOfNotNull(it.reportPath, it.logPath) }

        precheckRunItemRepository.deleteAllInBatch()
        precheckRunRepository.deleteAllInBatch()
        optimizationRunItemRepository.deleteAllInBatch()
        optimizationRunRepository.deleteAllInBatch()
        auditEventRepository.deleteAllInBatch()
        automationRuleRepository.deleteAllInBatch()
        mediaFileRepository.deleteAllInBatch()
        storageRootRepository.deleteAllInBatch()
        policyTargetRepository.deleteAllInBatch()

        deleteArtifacts(artifactPaths)
        deleteReportDirectory("run")
        deleteReportDirectory("precheck")
        exampleDataService.seedExamples()

        return DataResetResponse(
            deletedRuns = runCount,
            deletedPrechecks = precheckCount,
            deletedMediaFiles = mediaCount,
            examplesRestored = storageRootRepository.findByLabel("Examples") != null,
        )
    }

    private fun recalculateCompressionHistory(mediaIds: List<UUID>) {
        mediaFileRepository.findAllById(mediaIds).forEach { media ->
            val latestSuccessful = latestSuccessfulRunItem(media.id)
            if (latestSuccessful == null) {
                clearCompressionHistory(media)
            } else {
                applyCompressionHistory(media, latestSuccessful)
            }
            media.updatedAt = Instant.now()
            mediaFileRepository.save(media)
            refreshPrecheckSelection(media)
        }
    }

    private fun latestSuccessfulRunItem(mediaId: UUID): OptimizationRunItem? =
        optimizationRunItemRepository.findByMediaFileId(mediaId)
            .filter { item ->
                item.status == MediaStatus.ARCHIVED &&
                    item.runId.let { optimizationRunRepository.findById(it).orElse(null)?.status in successfulRunStatuses }
            }
            .maxByOrNull { item ->
                optimizationRunRepository.findById(item.runId).orElse(null)?.completedAt ?: item.updatedAt
            }

    private fun clearCompressionHistory(media: MediaFile) {
        media.everOptimized = false
        media.lastOptimizedAt = null
        media.optimizedPath = null
        media.archivedPath = null
        media.archivedAt = null
        media.retentionUntil = null
        media.retentionDeletionMode = null
        if (media.status in historicalStatuses) {
            media.status = if (Paths.get(media.originalPath).exists()) MediaStatus.ANALYZED else MediaStatus.DISCOVERED
        }
    }

    private fun applyCompressionHistory(media: MediaFile, item: OptimizationRunItem) {
        media.everOptimized = true
        media.lastOptimizedAt = optimizationRunRepository.findById(item.runId).orElse(null)?.completedAt ?: item.updatedAt
        media.optimizedPath = item.optimizedPath
        media.archivedPath = item.archivedPath
        if (item.archivedPath != null && media.status !in activeMediaStatuses) media.status = MediaStatus.ARCHIVED
    }

    private fun refreshPrecheckSelection(media: MediaFile) {
        val defaultSelected = !media.everOptimized && media.status == MediaStatus.ANALYZED
        precheckRunItemRepository.findByMediaFileId(media.id).forEach { item ->
            item.defaultSelected = defaultSelected
            item.selected = defaultSelected
            item.updatedAt = Instant.now()
            precheckRunItemRepository.save(item)
        }
    }

    private fun deleteArtifacts(paths: Collection<String>) {
        paths.forEach { value ->
            runCatching {
                val path = Paths.get(value)
                if (path.toAbsolutePath().normalize().startsWith(resolvedReportsRoot())) {
                    Files.deleteIfExists(path)
                }
            }
        }
    }

    private fun deleteReportDirectory(kind: String) {
        val directory = resolvedReportsRoot().resolve(kind).normalize()
        if (!directory.startsWith(resolvedReportsRoot()) || !Files.exists(directory)) return
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { path ->
                runCatching { Files.deleteIfExists(path) }
            }
        }
    }

    private fun resolvedReportsRoot(): Path = appPathService.resolve(reportsRoot).toAbsolutePath().normalize()

    private companion object {
        val activeRunStatuses = setOf(RunStatus.QUEUED, RunStatus.RUNNING)
        val successfulRunStatuses = setOf(RunStatus.COMPLETED, RunStatus.COMPLETED_WITH_FAILURES)
        val historicalStatuses = setOf(
            MediaStatus.ARCHIVED,
            MediaStatus.AWAITING_DELETION_APPROVAL,
            MediaStatus.DELETED,
            MediaStatus.RESTORED,
        )
        val activeMediaStatuses = setOf(
            MediaStatus.QUEUED,
            MediaStatus.TRANSCODING,
            MediaStatus.VALIDATING,
            MediaStatus.STAGED_CANDIDATE,
        )
    }
}
