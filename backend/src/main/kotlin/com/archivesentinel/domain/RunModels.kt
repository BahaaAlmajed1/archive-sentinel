package com.archivesentinel.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "optimization_runs")
class OptimizationRun(
    @Id var id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) var status: RunStatus = RunStatus.QUEUED,
    @Column(name = "started_at") var startedAt: Instant = Instant.now(),
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Column(name = "total_files") var totalFiles: Int = 0,
    @Column(name = "completed_files") var completedFiles: Int = 0,
    @Column(name = "failed_files") var failedFiles: Int = 0,
    @Column(name = "total_input_bytes") var totalInputBytes: Long = 0,
    @Column(name = "total_output_bytes") var totalOutputBytes: Long = 0,
    @Column(name = "report_path") var reportPath: String? = null,
    @Column(name = "log_path") var logPath: String? = null,
)

@Entity
@Table(name = "optimization_run_items")
class OptimizationRunItem(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "run_id") var runId: UUID = UUID.randomUUID(),
    @Column(name = "media_file_id") var mediaFileId: UUID? = null,
    @Column(name = "storage_root_id") var storageRootId: UUID? = null,
    @Column(name = "storage_root_label") var storageRootLabel: String? = null,
    @Column(name = "storage_root_path") var storageRootPath: String? = null,
    @Column(name = "optimized_root") var optimizedRoot: String? = null,
    @Column(name = "archive_root") var archiveRoot: String? = null,
    @Enumerated(EnumType.STRING) var status: MediaStatus = MediaStatus.QUEUED,
    @Column(name = "original_path") var originalPath: String = "",
    @Column(name = "candidate_path") var candidatePath: String? = null,
    @Column(name = "optimized_path") var optimizedPath: String? = null,
    @Column(name = "archived_path") var archivedPath: String? = null,
    @Column(name = "input_bytes") var inputBytes: Long = 0,
    @Column(name = "output_bytes") var outputBytes: Long = 0,
    @Column(name = "saved_bytes") var savedBytes: Long = 0,
    @Column(name = "error_message", columnDefinition = "text") var errorMessage: String? = null,
    @Column(name = "validation_json", columnDefinition = "text") var validationJson: String? = null,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "precheck_runs")
class PrecheckRun(
    @Id var id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) var status: PrecheckStatus = PrecheckStatus.RUNNING,
    @Column(name = "started_at") var startedAt: Instant = Instant.now(),
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Column(name = "root_ids", columnDefinition = "text") var rootIds: String = "",
    @Column(name = "total_files") var totalFiles: Int = 0,
    @Column(name = "scanned_files") var scannedFiles: Int = 0,
    @Column(name = "total_bytes") var totalBytes: Long = 0,
    @Column(name = "estimated_output_bytes") var estimatedOutputBytes: Long = 0,
    @Column(name = "estimated_savings_bytes") var estimatedSavingsBytes: Long = 0,
    @Column(name = "report_path") var reportPath: String? = null,
    @Column(name = "log_path") var logPath: String? = null,
    @Column(name = "error_message", columnDefinition = "text") var errorMessage: String? = null,
    @Column(name = "progress_message") var progressMessage: String = "Waiting to start",
)

@Entity
@Table(name = "precheck_run_items")
class PrecheckRunItem(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "precheck_run_id") var precheckRunId: UUID = UUID.randomUUID(),
    @Column(name = "media_file_id") var mediaFileId: UUID = UUID.randomUUID(),
    @Column(name = "folder_path") var folderPath: String = "",
    var selected: Boolean = true,
    @Column(name = "default_selected") var defaultSelected: Boolean = true,
    @Column(name = "retention_days") var retentionDays: Int = 0,
    @Enumerated(EnumType.STRING) @Column(name = "deletion_mode") var deletionMode: DeletionMode = DeletionMode.MANUAL,
    @Enumerated(EnumType.STRING) @Column(name = "output_mode") var outputMode: OutputMode = OutputMode.PARALLEL,
    @Column(name = "optimized_root_override") var optimizedRootOverride: String? = null,
    @Column(name = "archive_root_override") var archiveRootOverride: String? = null,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)
