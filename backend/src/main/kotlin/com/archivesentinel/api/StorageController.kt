package com.archivesentinel.api

import com.archivesentinel.service.StorageService
import com.archivesentinel.service.ScanService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class StorageController(
    private val storageService: StorageService,
    private val scanService: ScanService,
    private val rootChangeMonitorService: com.archivesentinel.service.RootChangeMonitorService,
    private val reportService: com.archivesentinel.service.ReportService,
) {
    @GetMapping("/storage-roots")
    fun roots(): List<StorageRootResponse> = storageService.roots().map {
        val effective = storageService.effectivePolicy(it)
        it.toResponse(
            effective.retentionDays,
            effective.deletionMode,
            effective.outputMode,
            storageService.activePolicies(it),
            rootChangeMonitorService.isDirty(it.id),
        )
    }

    @PostMapping("/storage-roots")
    fun addRoot(@Valid @RequestBody request: StorageRootRequest): StorageRootResponse {
        val root = storageService.addRoot(request)
        if (root.enabled) scanService.startPrecheck(PrecheckRequest(rootIds = listOf(root.id)))
        val effective = storageService.effectivePolicy(root)
        return root.toResponse(
            effective.retentionDays,
            effective.deletionMode,
            effective.outputMode,
            storageService.activePolicies(root),
            rootChangeMonitorService.isDirty(root.id),
        )
    }

    @PutMapping("/storage-roots/{id}")
    fun updateRoot(@PathVariable id: UUID, @Valid @RequestBody request: StorageRootRequest): StorageRootResponse {
        val root = storageService.updateRoot(id, request)
        val effective = storageService.effectivePolicy(root)
        return root.toResponse(
            effective.retentionDays,
            effective.deletionMode,
            effective.outputMode,
            storageService.activePolicies(root),
            rootChangeMonitorService.isDirty(root.id),
        )
    }

    @DeleteMapping("/storage-roots/{id}")
    fun deleteRoot(@PathVariable id: UUID) = storageService.deleteRoot(id)

    @PostMapping("/storage-roots/{id}/untrack-delete")
    fun untrackAndDeleteRoot(@PathVariable id: UUID): UntrackStorageRootResponse = storageService.untrackAndDeleteRoot(id)

    @PostMapping("/storage-roots/{id}/scan")
    fun scanRoot(@PathVariable id: UUID): StorageRootScanProgressResponse = scanService.startRootInventoryScan(id)

    @GetMapping("/storage-roots/scans")
    fun rootScans(): List<StorageRootScanProgressResponse> = scanService.rootInventoryScanProgress()

    @DeleteMapping("/storage-roots/scans/{id}")
    fun dismissRootScan(@PathVariable id: UUID) = scanService.dismissRootInventoryScan(id)

    @GetMapping("/storage-roots/{id}/scan-history")
    fun scanHistory(@PathVariable id: UUID): List<PrecheckRunResponse> =
        scanService.scansForRoot(id).map { it.toResponse(reportService) }

    @GetMapping("/policies")
    fun policies(): List<PolicyTargetResponse> = storageService.policies().map { it.toResponse() }

    @PostMapping("/policies")
    fun addPolicy(@Valid @RequestBody request: PolicyTargetRequest): PolicyTargetResponse = storageService.addPolicy(request).toResponse()

    @PutMapping("/policies/{id}")
    fun updatePolicy(@PathVariable id: UUID, @Valid @RequestBody request: PolicyTargetRequest): PolicyTargetResponse =
        storageService.updatePolicy(id, request).toResponse()

    @DeleteMapping("/policies/{id}")
    fun deletePolicy(@PathVariable id: UUID) = storageService.deletePolicy(id)

    @GetMapping("/automations")
    fun automations(): List<AutomationRuleResponse> = storageService.automations().map { it.toResponse() }

    @PostMapping("/automations")
    fun upsertAutomation(@RequestBody request: AutomationRuleRequest): AutomationRuleResponse =
        storageService.upsertAutomation(request.storageRootId, request.enabled, request.intervalMinutes).toResponse()

    @DeleteMapping("/automations/{id}")
    fun deleteAutomation(@PathVariable id: UUID) = storageService.deleteAutomation(id)
}
