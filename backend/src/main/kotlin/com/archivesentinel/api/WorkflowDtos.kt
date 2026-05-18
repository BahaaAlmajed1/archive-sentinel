package com.archivesentinel.api

import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.DeletionMode
import com.archivesentinel.domain.OutputMode
import com.archivesentinel.domain.RunStatus
import java.time.Instant
import java.util.UUID

data class RootRunOverrideRequest(
    val rootId: UUID,
    val retentionDays: Int? = null,
    val deletionMode: DeletionMode? = null,
    val outputMode: OutputMode? = null,
    val optimizedRootOverride: String? = null,
    val archiveRootOverride: String? = null,
)
data class PrecheckRequest(val rootIds: List<UUID> = emptyList(), val overrides: List<RootRunOverrideRequest> = emptyList())
data class PrecheckFolderSelectionRequest(val rootId: UUID? = null, val folderPath: String = "", val selected: Boolean = false)
data class PrecheckFile(
    val id: UUID,
    val mediaFileId: UUID,
    val storageRootId: UUID,
    val path: String,
    val sizeBytes: Long,
    val relativePath: String,
    val folderPath: String,
    val lastAccessedAt: Instant?,
    val status: MediaStatus,
    val selected: Boolean,
    val defaultSelected: Boolean,
    val everOptimized: Boolean,
)
data class PrecheckFolderSummary(val path: String, val totalFiles: Long, val selectedFiles: Long, val unselectedFiles: Long)
data class PrecheckRootSummary(
    val id: UUID,
    val label: String,
    val path: String,
    val totalFiles: Long,
    val selectedFiles: Long,
    val unselectedFiles: Long,
)
data class PrecheckResponse(
    val id: UUID,
    val status: com.archivesentinel.domain.PrecheckStatus,
    val scannedFiles: Int,
    val progressMessage: String,
    val progressPercent: Int,
    val totalFiles: Int,
    val totalBytes: Long,
    val estimatedOutputBytes: Long,
    val estimatedSavingsBytes: Long,
    val reportUrl: String?,
    val logUrl: String?,
    val selectedFiles: Long,
    val roots: List<PrecheckRootSummary>,
    val folders: List<PrecheckFolderSummary>,
    val files: PageResponse<PrecheckFile>,
)
data class StartRunRequest(val mediaFileIds: List<UUID> = emptyList(), val precheckId: UUID? = null)
data class RunResponse(
    val id: UUID,
    val status: RunStatus,
    val totalFiles: Int,
    val completedFiles: Int,
    val failedFiles: Int,
    val totalInputBytes: Long,
    val totalOutputBytes: Long,
    val savedBytes: Long,
    val progressPercent: Int,
    val startedAt: Instant,
    val completedAt: Instant?,
    val reportUrl: String?,
    val logUrl: String?,
)
data class RunItemResponse(
    val id: UUID,
    val mediaFileId: UUID?,
    val storageRootId: UUID?,
    val storageRootLabel: String?,
    val storageRootPath: String?,
    val optimizedRoot: String?,
    val archiveRoot: String?,
    val status: MediaStatus,
    val originalPath: String,
    val candidatePath: String?,
    val optimizedPath: String?,
    val archivedPath: String?,
    val inputBytes: Long,
    val outputBytes: Long,
    val savedBytes: Long,
    val errorMessage: String?,
    val validationJson: String?,
)
data class RunDetailsResponse(val run: RunResponse, val items: PageResponse<RunItemResponse>)
data class MediaFileResponse(
    val id: UUID,
    val originalPath: String,
    val optimizedPath: String?,
    val archivedPath: String?,
    val status: MediaStatus,
    val sizeBytes: Long,
    val retentionUntil: Instant?,
    val validationJson: String?,
)

data class ArchiveRunResponse(
    val run: RunResponse,
    val recoverableFiles: Long,
    val restoredFiles: Long,
)

data class ArchiveRunTreeResponse(
    val folders: List<PrecheckFolderSummary>,
    val files: PageResponse<MediaFileResponse>,
)
