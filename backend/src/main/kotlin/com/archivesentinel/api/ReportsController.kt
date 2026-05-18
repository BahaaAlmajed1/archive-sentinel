package com.archivesentinel.api

import com.archivesentinel.domain.PrecheckRunRepository
import com.archivesentinel.domain.PrecheckStatus
import com.archivesentinel.domain.RunStatus
import com.archivesentinel.service.MonitoringService
import com.archivesentinel.service.OptimizationService
import com.archivesentinel.service.ReportService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/reports")
class ReportsController(
    private val monitoringService: MonitoringService,
    private val reportService: ReportService,
    private val optimizationService: OptimizationService,
    private val precheckRunRepository: PrecheckRunRepository,
) {
    @GetMapping("/summary")
    fun monitoringSummary() = monitoringService.summary()

    @GetMapping("/preview")
    fun reportPreview() = reportService.html()

    @GetMapping("/runs")
    fun reportRuns(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) status: RunStatus?,
        @RequestParam(defaultValue = "") search: String,
    ): PageResponse<RunResponse> =
        optimizationService.runs(page, size, status, search).toPageResponse { it.toResponse(reportService) }

    @GetMapping("/prechecks")
    fun prechecks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) status: PrecheckStatus?,
        @RequestParam(defaultValue = "") search: String,
    ): PageResponse<PrecheckRunResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        val result = if (search.isNotBlank()) precheckRunRepository.search(status, search, pageable)
        else status?.let { precheckRunRepository.findByStatus(it, pageable) } ?: precheckRunRepository.findAll(pageable)
        return result.toPageResponse { it.toResponse(reportService) }
    }

    @GetMapping("/artifacts/{kind}/{id}")
    fun artifact(@PathVariable kind: String, @PathVariable id: UUID): ResponseEntity<String> {
        val artifact = reportService.artifact(kind, id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${artifact.filename}\"")
            .contentType(MediaType.parseMediaType(artifact.contentType))
            .body(artifact.body)
    }
}

@RestController
@RequestMapping("/api/monitoring")
class MonitoringCompatController(private val monitoringService: MonitoringService) {
    @GetMapping("/summary")
    fun monitoringSummary() = monitoringService.summary()
}
