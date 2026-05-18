package com.archivesentinel.service

import com.archivesentinel.api.PrecheckRequest
import com.archivesentinel.domain.AutomationRuleRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class AutomationService(
    private val automationRuleRepository: AutomationRuleRepository,
    private val scanService: ScanService,
    private val optimizationService: OptimizationService,
) {
    @Scheduled(fixedDelay = 60_000)
    fun processDueRules() {
        val now = Instant.now()
        automationRuleRepository.findAllByEnabled(true)
            .filter { rule ->
                rule.lastRunAt == null || !rule.lastRunAt!!.plus(rule.intervalMinutes.toLong(), ChronoUnit.MINUTES).isAfter(now)
            }
            .forEach { rule ->
                val precheck = scanService.precheck(PrecheckRequest(rootIds = listOf(rule.storageRootId)))
                if (precheck.selectedFiles > 0) optimizationService.startFromPrecheck(precheck.id)
                rule.lastRunAt = now
                rule.updatedAt = now
                automationRuleRepository.save(rule)
            }
    }
}
