package com.archivesentinel.api

import com.archivesentinel.service.ArchiveService
import com.archivesentinel.service.ReportService
import com.archivesentinel.domain.OptimizationRunItemRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/archive")
class ArchiveController(
    private val archiveService: ArchiveService,
    private val reportService: ReportService,
    private val optimizationRunItemRepository: OptimizationRunItemRepository,
) {
    @GetMapping
    fun archive(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<MediaFileResponse> =
        archiveService.archived(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "archivedAt"))).toPageResponse { it.toResponse() }

    @GetMapping("/pending-deletion")
    fun pendingDeletion(): List<MediaFileResponse> = archiveService.pendingDeletionApprovals().map { it.toResponse() }

    @GetMapping("/runs")
    fun runs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<ArchiveRunResponse> =
        archiveService.archiveRuns(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt")))
            .toPageResponse {
                ArchiveRunResponse(
                    run = it.toResponse(reportService, 0),
                    recoverableFiles = archiveService.recoverableByRun(it.id, "", org.springframework.data.domain.Pageable.unpaged()).totalElements,
                    restoredFiles = archiveService.restoredCount(it.id),
                )
            }

    @GetMapping("/runs/{id}")
    fun runFiles(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(defaultValue = "") folder: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ArchiveRunTreeResponse {
        val itemsByMediaId = optimizationRunItemRepository.findByRunId(id).mapNotNull { item ->
            item.mediaFileId?.let { it to item }
        }.toMap()
        val all = archiveService.recoverableByRun(id, search, org.springframework.data.domain.Pageable.unpaged()).content
        val folders = all.groupBy { media ->
            val item = itemsByMediaId[media.id]
            val root = item?.storageRootLabel ?: "Unknown root"
            "$root / ${parentFolder(media.relativePath)}"
        }.map { (path, rows) ->
            PrecheckFolderSummary(path, rows.size.toLong(), rows.size.toLong(), 0)
        }.sortedBy { it.path }
        val filtered = if (folder.isBlank()) all else all.filter { media ->
            val item = itemsByMediaId[media.id]
            val root = item?.storageRootLabel ?: "Unknown root"
            "$root / ${parentFolder(media.relativePath)}" == folder
        }
        val sorted = filtered.sortedBy { it.originalPath.lowercase() }
        val from = (page * size).coerceAtMost(sorted.size)
        val to = (from + size).coerceAtMost(sorted.size)
        val files = org.springframework.data.domain.PageImpl(sorted.subList(from, to), PageRequest.of(page, size, Sort.by("originalPath")), sorted.size.toLong())
        return ArchiveRunTreeResponse(folders, files.toPageResponse { it.toResponse() })
    }

    @PostMapping("/{id}/restore")
    fun restore(@PathVariable id: UUID): MediaFileResponse = archiveService.restore(id).toResponse()

    @PostMapping("/runs/{id}/restore")
    fun restoreRun(@PathVariable id: UUID): List<MediaFileResponse> = archiveService.restoreRun(id).map { it.toResponse() }

    @PostMapping("/{id}/approve-deletion")
    fun approveDeletion(@PathVariable id: UUID): MediaFileResponse = archiveService.approveDeletion(id).toResponse()

    private fun parentFolder(path: String): String =
        path.replace("\\", "/").substringBeforeLast("/", "Root")
}
