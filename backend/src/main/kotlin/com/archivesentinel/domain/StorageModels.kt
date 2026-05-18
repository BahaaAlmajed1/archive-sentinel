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
@Table(name = "storage_roots")
class StorageRoot(
    @Id var id: UUID = UUID.randomUUID(),
    var label: String = "",
    var path: String = "",
    var enabled: Boolean = true,
    @Column(name = "optimized_root_override") var optimizedRootOverride: String? = null,
    @Column(name = "archive_root_override") var archiveRootOverride: String? = null,
    @Column(name = "last_scanned_at") var lastScannedAt: Instant? = null,
    @Column(name = "latest_precheck_run_id") var latestPrecheckRunId: UUID? = null,
    @Column(name = "auto_rescan_enabled") var autoRescanEnabled: Boolean = false,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "automation_rules")
class AutomationRule(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "storage_root_id") var storageRootId: UUID = UUID.randomUUID(),
    var enabled: Boolean = true,
    @Column(name = "interval_minutes") var intervalMinutes: Int = 60,
    @Column(name = "last_run_at") var lastRunAt: Instant? = null,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "policy_targets")
class PolicyTarget(
    @Id var id: UUID = UUID.randomUUID(),
    var path: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "target_type") var targetType: TargetType = TargetType.FOLDER,
    var excluded: Boolean = false,
    var recursive: Boolean = true,
    @Column(name = "excluded_extensions", columnDefinition = "text") var excludedExtensions: String? = null,
    @Column(name = "retention_days") var retentionDays: Int? = null,
    @Enumerated(EnumType.STRING) @Column(name = "deletion_mode") var deletionMode: DeletionMode? = null,
    @Enumerated(EnumType.STRING) @Column(name = "output_mode") var outputMode: OutputMode? = null,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)
