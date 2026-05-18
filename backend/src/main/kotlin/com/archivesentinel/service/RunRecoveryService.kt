package com.archivesentinel.service

import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.OptimizationRunItemRepository
import com.archivesentinel.domain.OptimizationRunRepository
import com.archivesentinel.domain.RunStatus
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class RunRecoveryService(
    private val optimizationRunRepository: OptimizationRunRepository,
    private val optimizationRunItemRepository: OptimizationRunItemRepository,
    private val mediaFileRepository: MediaFileRepository,
    private val reportService: ReportService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        optimizationRunRepository.findAllByStatusIn(listOf(RunStatus.QUEUED, RunStatus.RUNNING))
            .forEach { run ->
                val items = optimizationRunItemRepository.findByRunId(run.id)
                val unfinished = items.filter { it.status !in terminalStatuses }
                unfinished.forEach { item ->
                    item.status = MediaStatus.FAILED
                    item.errorMessage = "Backend restarted before the run completed"
                    item.updatedAt = Instant.now()
                    optimizationRunItemRepository.save(item)
                    item.mediaFileId
                        ?.let(mediaFileRepository::findById)
                        ?.orElse(null)
                        ?.let { media ->
                            media.status = MediaStatus.FAILED
                            media.validationJson = """{"error":"Backend restarted before the run completed"}"""
                            media.updatedAt = Instant.now()
                            mediaFileRepository.save(media)
                        }
                }
                run.completedFiles = items.count { it.status == MediaStatus.ARCHIVED }
                run.failedFiles = items.count { it.status == MediaStatus.FAILED }
                run.status = RunStatus.FAILED
                run.completedAt = Instant.now()
                run.reportPath = reportService.generateRunReport(run).toString()
                run.logPath = reportService.generateRunLog(run).toString()
                optimizationRunRepository.save(run)
            }
    }

    private companion object {
        val terminalStatuses = setOf(
            MediaStatus.ARCHIVED,
            MediaStatus.FAILED,
            MediaStatus.DELETED,
            MediaStatus.RESTORED,
            MediaStatus.CANCELLED,
        )
    }
}
