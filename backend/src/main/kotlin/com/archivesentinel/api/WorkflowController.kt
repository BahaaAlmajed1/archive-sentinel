package com.archivesentinel.api

import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.RunStatus
import com.archivesentinel.service.DataMaintenanceService
import com.archivesentinel.service.OptimizationService
import com.archivesentinel.service.ReportService
import com.archivesentinel.service.ScanService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class WorkflowController(
    private val scanService: ScanService,
    private val optimizationService: OptimizationService,
    private val dataMaintenanceService: DataMaintenanceService,
    private val mediaFileRepository: MediaFileRepository,
    private val reportService: ReportService,
) {
    @PostMapping("/precheck")
    fun precheck(@RequestBody(required = false) request: PrecheckRequest?): PrecheckResponse =
        scanService.precheck(request ?: PrecheckRequest())

    @PostMapping("/precheck/async")
    fun startPrecheck(@RequestBody(required = false) request: PrecheckRequest?): PrecheckResponse =
        scanService.startPrecheck(request ?: PrecheckRequest())

    @PostMapping("/runs")
    fun startRun(@RequestBody request: StartRunRequest): RunResponse =
        (request.precheckId?.let { optimizationService.startFromPrecheck(it) } ?: optimizationService.start(request.mediaFileIds))
            .toResponse(reportService, 0)

    @GetMapping("/runs")
    fun runs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) status: RunStatus?,
        @RequestParam(defaultValue = "") search: String,
    ): PageResponse<RunResponse> =
        optimizationService.runs(page, size, status, search).toPageResponse { it.toResponse(reportService, optimizationService.savedBytes(it.id)) }

    @PostMapping("/runs/{id}/cancel")
    fun cancelRun(@PathVariable id: UUID): RunResponse =
        optimizationService.cancelRun(id).toResponse(reportService, optimizationService.savedBytes(id))

    @DeleteMapping("/runs/{id}")
    fun deleteRun(@PathVariable id: UUID): DeleteRunResponse = dataMaintenanceService.deleteRun(id)

    @GetMapping("/precheck/{id}")
    fun precheckDetails(
        @PathVariable id: UUID,
        @RequestParam(required = false) rootId: UUID?,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PrecheckResponse = scanService.precheckResponse(id, rootId, search, page, size)

    @GetMapping("/precheck/{id}/folders/files")
    fun precheckFolderFiles(
        @PathVariable id: UUID,
        @RequestParam(required = false) rootId: UUID?,
        @RequestParam folderPath: String,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): PageResponse<PrecheckFile> = scanService.precheckFolderFiles(id, rootId, folderPath, search, page, size)

    @PostMapping("/precheck/items/{id}/selection")
    fun updatePrecheckSelection(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Boolean>,
    ): PrecheckFile = scanService.updateSelection(id, body["selected"] ?: false)

    @PostMapping("/precheck/{id}/selection")
    fun updateAllPrecheckSelections(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Boolean>,
    ): PrecheckResponse = scanService.updateAllSelections(id, body["selected"] ?: false)

    @PostMapping("/precheck/{id}/folders/selection")
    fun updatePrecheckFolderSelection(
        @PathVariable id: UUID,
        @RequestBody body: PrecheckFolderSelectionRequest,
    ): PrecheckResponse = scanService.updateFolderSelections(id, body.rootId, body.folderPath, body.selected)

    @GetMapping("/runs/{id}")
    fun runDetails(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        @RequestParam(defaultValue = "") search: String,
    ): RunDetailsResponse {
        val run = optimizationService.run(id).toResponse(reportService, optimizationService.savedBytes(id))
        val pageable = PageRequest.of(page, size, Sort.by("storageRootLabel", "originalPath"))
        val rawItems = if (search.isBlank()) optimizationService.runItems(id, pageable)
        else optimizationService.searchRunItems(id, search, pageable)
        val items = rawItems.toPageResponse { it.toResponse() }
        return RunDetailsResponse(run, items)
    }

    @GetMapping("/media-files")
    fun mediaFiles(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) status: MediaStatus?,
        @RequestParam(defaultValue = "") search: String,
    ): PageResponse<MediaFileResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        val result: Page<com.archivesentinel.domain.MediaFile> = when {
            status != null && search.isNotBlank() -> mediaFileRepository.findByStatusAndOriginalPathContainingIgnoreCase(status, search, pageable)
            status != null -> mediaFileRepository.findByStatus(status, pageable)
            search.isNotBlank() -> mediaFileRepository.findByOriginalPathContainingIgnoreCase(search, pageable)
            else -> mediaFileRepository.findAll(pageable)
        }
        return result.toPageResponse { it.toResponse() }
    }
}
