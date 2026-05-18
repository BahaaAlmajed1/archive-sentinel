package com.archivesentinel.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MediaFileRepository : JpaRepository<MediaFile, UUID> {
    fun findByOriginalPath(originalPath: String): MediaFile?
    fun findAllByOriginalPathIn(originalPaths: Collection<String>): List<MediaFile>
    fun findAllByStatus(status: MediaStatus): List<MediaFile>
    fun findAllByStatusIn(statuses: Collection<MediaStatus>): List<MediaFile>
    fun findAllByStatusIn(statuses: Collection<MediaStatus>, pageable: Pageable): Page<MediaFile>
    fun findByStatus(status: MediaStatus, pageable: Pageable): Page<MediaFile>
    fun findByOriginalPathContainingIgnoreCase(query: String, pageable: Pageable): Page<MediaFile>
    fun findByStatusAndOriginalPathContainingIgnoreCase(status: MediaStatus, query: String, pageable: Pageable): Page<MediaFile>
    fun findAllByStorageRootId(storageRootId: UUID): List<MediaFile>
    fun countByStorageRootId(storageRootId: UUID): Long
    fun findByStatusInAndOriginalPathContainingIgnoreCase(statuses: Collection<MediaStatus>, query: String, pageable: Pageable): Page<MediaFile>
    fun countByStatus(status: MediaStatus): Long
}
