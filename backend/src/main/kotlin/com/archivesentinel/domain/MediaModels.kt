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
@Table(name = "media_files")
class MediaFile(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "storage_root_id") var storageRootId: UUID = UUID.randomUUID(),
    @Column(name = "original_path") var originalPath: String = "",
    @Column(name = "relative_path") var relativePath: String = "",
    @Column(name = "size_bytes") var sizeBytes: Long = 0,
    @Column(name = "last_accessed_at") var lastAccessedAt: Instant? = null,
    @Column(name = "last_modified_at") var lastModifiedAt: Instant? = null,
    @Enumerated(EnumType.STRING) var status: MediaStatus = MediaStatus.DISCOVERED,
    @Column(name = "metadata_json", columnDefinition = "text") var metadataJson: String? = null,
    @Column(name = "validation_json", columnDefinition = "text") var validationJson: String? = null,
    @Column(name = "candidate_path") var candidatePath: String? = null,
    @Column(name = "optimized_path") var optimizedPath: String? = null,
    @Column(name = "archived_path") var archivedPath: String? = null,
    @Column(name = "archived_at") var archivedAt: Instant? = null,
    @Column(name = "retention_until") var retentionUntil: Instant? = null,
    @Enumerated(EnumType.STRING) @Column(name = "retention_deletion_mode") var retentionDeletionMode: DeletionMode? = null,
    @Column(name = "ever_optimized") var everOptimized: Boolean = false,
    @Column(name = "last_optimized_at") var lastOptimizedAt: Instant? = null,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)
