package com.archivesentinel.service

import com.archivesentinel.api.PrecheckRequest
import com.archivesentinel.domain.StorageRootRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RootAutoRescanService(
    private val storageRootRepository: StorageRootRepository,
    private val settingsService: SettingsService,
    private val rootChangeMonitorService: RootChangeMonitorService,
    private val scanService: ScanService,
) {
    @Scheduled(fixedDelay = 60_000)
    fun scanDirtyRootsWhenDue() {
        val now = Instant.now()
        val interval = settingsService.current().backgroundScanRefreshMinutes.toLong()
        storageRootRepository.findAll()
            .filter { it.enabled && it.autoRescanEnabled && it.lastScannedAt != null }
            .filter { rootChangeMonitorService.isDirty(it.id) }
            .filter { it.lastScannedAt!!.plus(interval, ChronoUnit.MINUTES) <= now }
            .filterNot { scanService.hasRunningScanForRoot(it.id) }
            .forEach { scanService.startPrecheck(PrecheckRequest(rootIds = listOf(it.id))) }
    }
}
