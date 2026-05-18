package com.archivesentinel.service

import com.archivesentinel.domain.MediaFile
import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.OptimizationRun
import com.archivesentinel.domain.OptimizationRunItem
import com.archivesentinel.domain.OptimizationRunItemRepository
import com.archivesentinel.domain.OptimizationRunRepository
import com.archivesentinel.domain.PrecheckRun
import com.archivesentinel.domain.PrecheckRunItem
import com.archivesentinel.domain.PrecheckRunItemRepository
import com.archivesentinel.domain.PrecheckRunRepository
import com.archivesentinel.domain.PrecheckStatus
import com.archivesentinel.domain.RunStatus
import com.archivesentinel.domain.StorageRoot
import com.archivesentinel.domain.StorageRootRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@SpringBootTest
class DataMaintenanceServiceTests {
    @Autowired lateinit var dataMaintenanceService: DataMaintenanceService
    @Autowired lateinit var storageRootRepository: StorageRootRepository
    @Autowired lateinit var mediaFileRepository: MediaFileRepository
    @Autowired lateinit var optimizationRunRepository: OptimizationRunRepository
    @Autowired lateinit var optimizationRunItemRepository: OptimizationRunItemRepository
    @Autowired lateinit var precheckRunRepository: PrecheckRunRepository
    @Autowired lateinit var precheckRunItemRepository: PrecheckRunItemRepository

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `delete run recalculates compression history from remaining successful runs`() {
        val original = tempDir.resolve("clip.mp4")
        Files.writeString(original, "original")
        val root = storageRootRepository.save(StorageRoot(label = "Test", path = tempDir.toString()))
        val media = mediaFileRepository.save(
            MediaFile(
                storageRootId = root.id,
                originalPath = original.toString(),
                relativePath = "clip.mp4",
                sizeBytes = 100,
                status = MediaStatus.ARCHIVED,
                optimizedPath = "optimized-2.mp4",
                archivedPath = "archive-2.mp4",
                everOptimized = true,
                lastOptimizedAt = Instant.now(),
            ),
        )
        val firstRun = optimizationRunRepository.save(OptimizationRun(status = RunStatus.COMPLETED, completedAt = Instant.now().minusSeconds(60), totalFiles = 1, completedFiles = 1))
        optimizationRunItemRepository.save(
            OptimizationRunItem(
                runId = firstRun.id,
                mediaFileId = media.id,
                status = MediaStatus.ARCHIVED,
                originalPath = media.originalPath,
                optimizedPath = "optimized-1.mp4",
                archivedPath = "archive-1.mp4",
            ),
        )
        val secondRun = optimizationRunRepository.save(OptimizationRun(status = RunStatus.COMPLETED, completedAt = Instant.now(), totalFiles = 1, completedFiles = 1))
        optimizationRunItemRepository.save(
            OptimizationRunItem(
                runId = secondRun.id,
                mediaFileId = media.id,
                status = MediaStatus.ARCHIVED,
                originalPath = media.originalPath,
                optimizedPath = "optimized-2.mp4",
                archivedPath = "archive-2.mp4",
            ),
        )
        val precheck = precheckRunRepository.save(PrecheckRun(status = PrecheckStatus.COMPLETED, rootIds = root.id.toString()))
        val precheckItem = precheckRunItemRepository.save(
            PrecheckRunItem(
                precheckRunId = precheck.id,
                mediaFileId = media.id,
                folderPath = "Root",
                selected = false,
                defaultSelected = false,
            ),
        )

        dataMaintenanceService.deleteRun(secondRun.id)

        val afterSecondDelete = mediaFileRepository.findById(media.id).orElseThrow()
        assertTrue(afterSecondDelete.everOptimized)
        assertEquals("optimized-1.mp4", afterSecondDelete.optimizedPath)
        assertEquals("archive-1.mp4", afterSecondDelete.archivedPath)

        dataMaintenanceService.deleteRun(firstRun.id)

        val afterLastDelete = mediaFileRepository.findById(media.id).orElseThrow()
        val refreshedPrecheckItem = precheckRunItemRepository.findById(precheckItem.id).orElseThrow()
        assertFalse(afterLastDelete.everOptimized)
        assertNull(afterLastDelete.optimizedPath)
        assertNull(afterLastDelete.archivedPath)
        assertEquals(MediaStatus.ANALYZED, afterLastDelete.status)
        assertTrue(refreshedPrecheckItem.defaultSelected)
        assertTrue(refreshedPrecheckItem.selected)
    }
}
