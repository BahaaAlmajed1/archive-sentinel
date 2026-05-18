package com.archivesentinel.service

import com.archivesentinel.domain.AppSettings
import com.archivesentinel.domain.MediaFile
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.OptimizationRun
import com.archivesentinel.domain.OptimizationRunItem
import com.archivesentinel.domain.OptimizationRunItemRepository
import com.archivesentinel.domain.OptimizationRunRepository
import com.archivesentinel.domain.PrecheckRun
import com.archivesentinel.domain.PrecheckRunRepository
import com.archivesentinel.domain.RunStatus
import com.archivesentinel.domain.StorageRoot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.mail.javamail.JavaMailSender
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class ReportServiceTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `run report contains collapsed file tree and savings`() {
        val runItemRepository = Mockito.mock(OptimizationRunItemRepository::class.java)
        val run = OptimizationRun(id = UUID.randomUUID(), status = RunStatus.COMPLETED, totalFiles = 1, completedFiles = 1, totalInputBytes = 1_000, totalOutputBytes = 600)
        Mockito.`when`(runItemRepository.findByRunId(run.id)).thenReturn(
            listOf(
                OptimizationRunItem(
                    runId = run.id,
                    status = MediaStatus.ARCHIVED,
                    originalPath = "D:/media/show/episode.mkv",
                    archivedPath = "D:/archive/show/episode.mkv",
                    optimizedPath = "D:/optimized/show/episode.optimized.mkv",
                    inputBytes = 1_000,
                    outputBytes = 600,
                    savedBytes = 400,
                ),
            ),
        )

        val service = ReportService(
            Mockito.mock(SettingsService::class.java),
            Mockito.mock(MonitoringService::class.java),
            Mockito.mock(JavaMailSender::class.java),
            Mockito.mock(OptimizationRunRepository::class.java),
            runItemRepository,
            Mockito.mock(PrecheckRunRepository::class.java),
            AppPathService(),
            tempDir.toString(),
        )

        val report = service.generateRunReport(run)
        val html = Files.readString(report)

        assertTrue(html.contains("<details>"))
        assertTrue(html.contains("Optimization run"))
        assertTrue(html.contains("episode.optimized.mkv"))
    }

    @Test
    fun `precheck log includes failed inventory reasons`() {
        val settingsService = Mockito.mock(SettingsService::class.java)
        Mockito.`when`(settingsService.current()).thenReturn(AppSettings())
        val service = ReportService(
            settingsService,
            Mockito.mock(MonitoringService::class.java),
            Mockito.mock(JavaMailSender::class.java),
            Mockito.mock(OptimizationRunRepository::class.java),
            Mockito.mock(OptimizationRunItemRepository::class.java),
            Mockito.mock(PrecheckRunRepository::class.java),
            AppPathService(),
            tempDir.toString(),
        )
        val root = StorageRoot(label = "clips", path = "E:/CLIPS")
        val failed = MediaFile(
            storageRootId = root.id,
            originalPath = "E:/CLIPS/Base Profile/bad.mp4",
            relativePath = "Base Profile/bad.mp4",
            sizeBytes = 42,
            status = MediaStatus.FAILED,
            validationJson = """{"error":"ffprobe failed"}""",
        )

        val log = service.generatePrecheckLog(PrecheckRun(totalFiles = 1), listOf(root), listOf(failed))
        val text = Files.readString(log)

        assertTrue(text.contains("bad.mp4"))
        assertTrue(text.contains("ffprobe failed"))
    }
}
