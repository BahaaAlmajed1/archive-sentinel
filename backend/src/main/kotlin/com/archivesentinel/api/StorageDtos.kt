package com.archivesentinel.api

import com.archivesentinel.domain.DeletionMode
import com.archivesentinel.domain.OutputMode
import com.archivesentinel.domain.TargetType
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class PathValidationResponse(val path: String, val exists: Boolean, val readable: Boolean, val writable: Boolean, val directory: Boolean)
data class TdarrStatusResponse(val reachable: Boolean, val status: String?, val version: String?)
data class NativePickerRequest(val multiple: Boolean = true, val initialPath: String? = null)
data class NativePickerResponse(val paths: List<String>, val cancelled: Boolean, val message: String? = null)
data class ServerBrowserRequest(val path: String? = null, val kind: String = "folder", val showHidden: Boolean = false)
data class ServerBrowserEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val readable: Boolean,
    val writable: Boolean,
    val hidden: Boolean,
    val sizeBytes: Long? = null,
)
data class ServerBrowserResponse(
    val currentPath: String,
    val parentPath: String?,
    val entries: List<ServerBrowserEntry>,
    val roots: List<String>,
    val separator: String,
    val message: String? = null,
)
data class DeleteRunResponse(val deletedRunId: UUID, val deletedItems: Int, val recalculatedMediaFiles: Int)
data class DataResetResponse(val deletedRuns: Long, val deletedPrechecks: Long, val deletedMediaFiles: Long, val examplesRestored: Boolean)
data class UntrackStorageRootResponse(val deletedRootId: UUID, val untrackedMediaFiles: Int, val deletedPrecheckItems: Int, val clearedRunItemLinks: Int)

data class StorageRootRequest(
    @field:NotBlank val label: String,
    @field:NotBlank val path: String,
    val enabled: Boolean = true,
    val optimizedRootOverride: String? = null,
    val archiveRootOverride: String? = null,
    val autoRescanEnabled: Boolean = false,
)
data class StorageRootResponse(
    val id: UUID,
    val label: String,
    val path: String,
    val enabled: Boolean,
    val optimizedRootOverride: String?,
    val archiveRootOverride: String?,
    val lastScannedAt: java.time.Instant?,
    val scanStatus: String,
    val changesDetected: Boolean,
    val autoRescanEnabled: Boolean,
    val effectiveRetentionDays: Int,
    val effectiveDeletionMode: DeletionMode,
    val effectiveOutputMode: OutputMode,
    val activePolicies: List<PolicyTargetResponse>,
)

data class StorageRootScanProgressResponse(
    val rootId: UUID,
    val status: String,
    val totalFiles: Int,
    val scannedFiles: Int,
    val remainingFiles: Int,
    val progressPercent: Int,
    val message: String,
)

data class PolicyTargetRequest(
    @field:NotBlank val path: String,
    val targetType: TargetType,
    val excluded: Boolean,
    val recursive: Boolean,
    val excludedExtensions: String?,
    val retentionDays: Int?,
    val deletionMode: DeletionMode?,
    val outputMode: OutputMode?,
)
data class PolicyTargetResponse(
    val id: UUID,
    val path: String,
    val targetType: TargetType,
    val excluded: Boolean,
    val recursive: Boolean,
    val excludedExtensions: String?,
    val retentionDays: Int?,
    val deletionMode: DeletionMode?,
    val outputMode: OutputMode?,
)

data class AutomationRuleRequest(
    val storageRootId: UUID,
    val enabled: Boolean = true,
    val intervalMinutes: Int = 60,
)

data class AutomationRuleResponse(
    val id: UUID,
    val storageRootId: UUID,
    val enabled: Boolean,
    val intervalMinutes: Int,
    val lastRunAt: java.time.Instant?,
)
