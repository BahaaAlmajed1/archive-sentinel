package com.archivesentinel.service

import com.archivesentinel.domain.AppSettings
import com.archivesentinel.domain.DeletionMode
import com.archivesentinel.domain.OutputMode
import com.archivesentinel.domain.PolicyTarget
import com.archivesentinel.domain.PolicyTargetRepository
import com.archivesentinel.domain.TargetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.file.Paths

class PolicyResolutionServiceTests {
    private val defaults = AppSettings(defaultRetentionDays = 90, defaultDeletionMode = DeletionMode.MANUAL, defaultOutputMode = OutputMode.PARALLEL)

    @Test
    fun `most specific matching policy wins`() {
        val repo = Mockito.mock(PolicyTargetRepository::class.java)
        Mockito.`when`(repo.findAll()).thenReturn(
            listOf(
                PolicyTarget(path = "D:/media", targetType = TargetType.FOLDER, recursive = true, retentionDays = 90),
                PolicyTarget(path = "D:/media/client-a", targetType = TargetType.FOLDER, recursive = true, retentionDays = 14, outputMode = OutputMode.SAME_LIBRARY),
            ),
        )

        val resolved = PolicyResolutionService(repo, AppPathService()).resolve(Paths.get("D:/media/client-a/episode.mkv"), defaults)

        assertEquals(14, resolved.retentionDays)
        assertEquals(OutputMode.SAME_LIBRARY, resolved.outputMode)
    }

    @Test
    fun `extension exclusions make matching policy excluded`() {
        val repo = Mockito.mock(PolicyTargetRepository::class.java)
        Mockito.`when`(repo.findAll()).thenReturn(
            listOf(
                PolicyTarget(path = "D:/media", targetType = TargetType.FOLDER, recursive = true, excludedExtensions = "mov,mxf"),
            ),
        )

        val service = PolicyResolutionService(repo, AppPathService())

        assertTrue(service.resolve(Paths.get("D:/media/source.mov"), defaults).excluded)
        assertFalse(service.resolve(Paths.get("D:/media/review.mp4"), defaults).excluded)
    }
}
