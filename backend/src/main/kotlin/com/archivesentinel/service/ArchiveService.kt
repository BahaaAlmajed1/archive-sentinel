package com.archivesentinel.service

import com.archivesentinel.domain.DeletionMode
import com.archivesentinel.domain.MediaFile
import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.OptimizationRun
import com.archivesentinel.domain.OptimizationRunRepository
import com.archivesentinel.domain.OptimizationRunItemRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageImpl
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@Service
class ArchiveService(
    private val mediaFileRepository: MediaFileRepository,
    private val auditService: AuditService,
    private val settingsService: SettingsService,
    private val policyResolutionService: PolicyResolutionService,
    private val optimizationRunRepository: OptimizationRunRepository,
    private val optimizationRunItemRepository: OptimizationRunItemRepository,
) {
    fun archived(pageable: Pageable): Page<MediaFile> =
        mediaFileRepository.findAllByStatusIn(listOf(MediaStatus.ARCHIVED, MediaStatus.AWAITING_DELETION_APPROVAL), pageable)

    fun archived(): List<MediaFile> =
        mediaFileRepository.findAllByStatusIn(listOf(MediaStatus.ARCHIVED, MediaStatus.AWAITING_DELETION_APPROVAL))

    fun pendingDeletionApprovals(): List<MediaFile> =
        mediaFileRepository.findAllByStatusIn(listOf(MediaStatus.ARCHIVED, MediaStatus.AWAITING_DELETION_APPROVAL))
            .filter { isDue(it) && deletionModeFor(it) == DeletionMode.MANUAL }

    fun archiveRuns(pageable: Pageable): Page<OptimizationRun> {
        val recoverableIds = archived().map { it.id }.toSet()
        val allRuns = optimizationRunRepository.findAll().filter { run ->
            optimizationRunItemRepository.findByRunId(run.id).any { it.mediaFileId in recoverableIds }
        }
        val from = pageable.offset.toInt().coerceAtMost(allRuns.size)
        val to = (from + pageable.pageSize).coerceAtMost(allRuns.size)
        return PageImpl(allRuns.subList(from, to), pageable, allRuns.size.toLong())
    }

    fun recoverableByRun(runId: UUID, search: String, pageable: Pageable): Page<MediaFile> {
        val mediaIds = optimizationRunItemRepository.findByRunId(runId).mapNotNull { it.mediaFileId }.toSet()
        val recoverable = archived()
            .filter { it.id in mediaIds }
            .filter { search.isBlank() || it.originalPath.contains(search, ignoreCase = true) || it.archivedPath.orEmpty().contains(search, ignoreCase = true) }
        if (pageable.isUnpaged) return PageImpl(recoverable)
        val from = pageable.offset.toInt().coerceAtMost(recoverable.size)
        val to = (from + pageable.pageSize).coerceAtMost(recoverable.size)
        return PageImpl(recoverable.subList(from, to), pageable, recoverable.size.toLong())
    }

    fun restoredCount(runId: UUID): Long {
        val mediaIds = optimizationRunItemRepository.findByRunId(runId).mapNotNull { it.mediaFileId }.toSet()
        return mediaFileRepository.findAllById(mediaIds).count { it.status == MediaStatus.RESTORED }.toLong()
    }

    @Transactional
    fun restoreRun(runId: UUID): List<MediaFile> =
        recoverableByRun(runId, "", org.springframework.data.domain.Pageable.unpaged()).content.map { restore(it.id) }

    @Transactional
    fun restore(id: UUID): MediaFile {
        val media = mediaFileRepository.findById(id).orElseThrow()
        val archived = Paths.get(media.archivedPath ?: error("No archived path"))
        val original = Paths.get(media.originalPath)
        original.parent?.createDirectories()
        Files.move(archived, original, StandardCopyOption.REPLACE_EXISTING)
        media.status = MediaStatus.RESTORED
        media.archivedPath = null
        media.archivedAt = null
        media.retentionUntil = null
        media.retentionDeletionMode = null
        media.updatedAt = Instant.now()
        auditService.record(media.id, "RESTORED", "Restored original to ${media.originalPath}")
        return mediaFileRepository.save(media)
    }

    @Transactional
    fun approveDeletion(id: UUID): MediaFile {
        val media = mediaFileRepository.findById(id).orElseThrow()
        require(isDue(media)) { "Retention period has not elapsed yet" }
        require(deletionModeFor(media) == DeletionMode.MANUAL) { "Deletion approval is only required for manual policies" }
        val archived = Paths.get(media.archivedPath ?: error("No archived path"))
        if (archived.exists()) Files.delete(archived)
        media.status = MediaStatus.DELETED
        media.updatedAt = Instant.now()
        auditService.record(media.id, "DELETED", "Deleted archived original after approval")
        return mediaFileRepository.save(media)
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    fun processRetention() {
        mediaFileRepository.findAllByStatusIn(listOf(MediaStatus.ARCHIVED, MediaStatus.AWAITING_DELETION_APPROVAL))
            .filter { isDue(it) }
            .forEach { media ->
                when (deletionModeFor(media)) {
                    DeletionMode.MANUAL -> {
                        if (media.status != MediaStatus.AWAITING_DELETION_APPROVAL) {
                            media.status = MediaStatus.AWAITING_DELETION_APPROVAL
                            media.updatedAt = Instant.now()
                            mediaFileRepository.save(media)
                            auditService.record(media.id, "AWAITING_DELETION_APPROVAL", "Retention elapsed; awaiting manual approval")
                        }
                    }
                    DeletionMode.AUTOMATIC -> {
                        val archived = Paths.get(media.archivedPath ?: return@forEach)
                        if (archived.exists()) Files.delete(archived)
                        media.status = MediaStatus.DELETED
                        media.updatedAt = Instant.now()
                        mediaFileRepository.save(media)
                        auditService.record(media.id, "DELETED", "Deleted archived original automatically after retention")
                    }
                }
            }
    }

    private fun isDue(media: MediaFile): Boolean =
        media.retentionUntil != null && !media.retentionUntil!!.isAfter(Instant.now())

    private fun deletionModeFor(media: MediaFile): DeletionMode =
        media.retentionDeletionMode ?: policyResolutionService.resolve(Paths.get(media.originalPath), settingsService.current()).deletionMode
}
