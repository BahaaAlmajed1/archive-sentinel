package com.archivesentinel.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface OptimizationRunRepository : JpaRepository<OptimizationRun, UUID> {
    fun countByStatus(status: RunStatus): Long
    fun findByStatus(status: RunStatus, pageable: Pageable): Page<OptimizationRun>
    fun findAllByStatusIn(statuses: Collection<RunStatus>): List<OptimizationRun>

    @Query("select coalesce(sum(r.totalInputBytes - r.totalOutputBytes), 0) from OptimizationRun r where r.status in :statuses")
    fun totalSavingsForStatuses(statuses: Collection<RunStatus>): Long

    @Query(
        """
        select distinct r from OptimizationRun r
        left join OptimizationRunItem i on i.runId = r.id
        where (:status is null or r.status = :status)
          and (
            :search = '' or
            lower(cast(r.id as string)) like lower(concat('%', :search, '%')) or
            lower(i.originalPath) like lower(concat('%', :search, '%'))
          )
        """,
    )
    fun search(status: RunStatus?, search: String, pageable: Pageable): Page<OptimizationRun>
}

interface OptimizationRunItemRepository : JpaRepository<OptimizationRunItem, UUID> {
    fun findByRunId(runId: UUID): List<OptimizationRunItem>
    fun findByRunId(runId: UUID, pageable: Pageable): Page<OptimizationRunItem>
    fun findByMediaFileId(mediaFileId: UUID): List<OptimizationRunItem>
    fun deleteByRunId(runId: UUID): Long

    @Modifying
    @Query("update OptimizationRunItem i set i.mediaFileId = null where i.mediaFileId in :mediaFileIds")
    fun clearMediaReferences(mediaFileIds: Collection<UUID>): Int

    @Query("select coalesce(sum(i.savedBytes), 0) from OptimizationRunItem i where i.runId = :runId")
    fun totalSavedBytesByRunId(runId: UUID): Long

    @Query("select coalesce(sum(i.savedBytes), 0) from OptimizationRunItem i where i.runId in (select r.id from OptimizationRun r where r.status in :statuses)")
    fun totalSavedBytesForRunStatuses(statuses: Collection<RunStatus>): Long

    @Query(
        """
        select i from OptimizationRunItem i
        where i.runId = :runId
          and (
            lower(i.originalPath) like lower(concat('%', :search, '%')) or
            lower(coalesce(i.optimizedPath, '')) like lower(concat('%', :search, '%')) or
            lower(coalesce(i.archivedPath, '')) like lower(concat('%', :search, '%')) or
            lower(coalesce(i.storageRootLabel, '')) like lower(concat('%', :search, '%'))
          )
        """,
    )
    fun searchItems(runId: UUID, search: String, pageable: Pageable): Page<OptimizationRunItem>
}

interface PrecheckRunRepository : JpaRepository<PrecheckRun, UUID> {
    fun findByStatus(status: PrecheckStatus, pageable: Pageable): Page<PrecheckRun>

    @Query(
        """
        select distinct p from PrecheckRun p
        left join PrecheckRunItem i on i.precheckRunId = p.id
        left join MediaFile m on m.id = i.mediaFileId
        where (:status is null or p.status = :status)
          and (
            :search = '' or
            lower(cast(p.id as string)) like lower(concat('%', :search, '%')) or
            lower(m.originalPath) like lower(concat('%', :search, '%'))
          )
        """,
    )
    fun search(status: PrecheckStatus?, search: String, pageable: Pageable): Page<PrecheckRun>
}

interface PrecheckRunItemRepository : JpaRepository<PrecheckRunItem, UUID> {
    fun findByPrecheckRunId(precheckRunId: UUID): List<PrecheckRunItem>
    fun findByPrecheckRunIdAndFolderPath(precheckRunId: UUID, folderPath: String, pageable: Pageable): Page<PrecheckRunItem>
    fun findByPrecheckRunIdAndSelected(precheckRunId: UUID, selected: Boolean): List<PrecheckRunItem>
    fun findByMediaFileId(mediaFileId: UUID): List<PrecheckRunItem>
    fun deleteByPrecheckRunId(precheckRunId: UUID): Long

    @Modifying
    @Query("delete from PrecheckRunItem i where i.mediaFileId in :mediaFileIds")
    fun deleteByMediaFileIds(mediaFileIds: Collection<UUID>): Int
    fun countByPrecheckRunIdAndSelected(precheckRunId: UUID, selected: Boolean): Long

    @Modifying
    @Query("update PrecheckRunItem i set i.selected = :selected, i.updatedAt = :updatedAt where i.precheckRunId = :precheckRunId")
    fun updateSelections(precheckRunId: UUID, selected: Boolean, updatedAt: Instant): Int

    @Query(
        """
        select i from PrecheckRunItem i
        join MediaFile m on m.id = i.mediaFileId
        where i.precheckRunId = :precheckRunId
          and i.folderPath = :folderPath
          and (:rootId is null or m.storageRootId = :rootId)
        """,
    )
    fun findFolderItems(precheckRunId: UUID, rootId: UUID?, folderPath: String): List<PrecheckRunItem>

    interface RootSummaryProjection {
        val rootId: UUID
        val totalFiles: Long
        val selectedFiles: Long
    }

    interface FolderSummaryProjection {
        val path: String
        val totalFiles: Long
        val selectedFiles: Long
    }

    @Query(
        """
        select m.storageRootId as rootId,
               count(i) as totalFiles,
               coalesce(sum(case when i.selected = true then 1 else 0 end), 0) as selectedFiles
        from PrecheckRunItem i
        join MediaFile m on m.id = i.mediaFileId
        where i.precheckRunId = :precheckRunId
        group by m.storageRootId
        """,
    )
    fun summarizeByRoot(precheckRunId: UUID): List<RootSummaryProjection>

    @Query(
        """
        select i.folderPath as path,
               count(i) as totalFiles,
               coalesce(sum(case when i.selected = true then 1 else 0 end), 0) as selectedFiles
        from PrecheckRunItem i
        join MediaFile m on m.id = i.mediaFileId
        where i.precheckRunId = :precheckRunId
          and (:rootId is null or m.storageRootId = :rootId)
          and (:search = '' or lower(m.originalPath) like lower(concat('%', :search, '%')))
        group by i.folderPath
        order by i.folderPath
        """,
    )
    fun summarizeByFolder(precheckRunId: UUID, rootId: UUID?, search: String): List<FolderSummaryProjection>

    @Query(
        """
        select i from PrecheckRunItem i
        join MediaFile m on m.id = i.mediaFileId
        where i.precheckRunId = :precheckRunId
          and (:rootId is null or m.storageRootId = :rootId)
          and (:search = '' or lower(m.originalPath) like lower(concat('%', :search, '%')))
        """,
    )
    fun search(precheckRunId: UUID, rootId: UUID?, search: String, pageable: Pageable): Page<PrecheckRunItem>

    @Query(
        """
        select i from PrecheckRunItem i
        join MediaFile m on m.id = i.mediaFileId
        where i.precheckRunId = :precheckRunId
          and i.folderPath = :folderPath
          and (:rootId is null or m.storageRootId = :rootId)
          and (:search = '' or lower(m.originalPath) like lower(concat('%', :search, '%')))
        """,
    )
    fun searchFolder(precheckRunId: UUID, rootId: UUID?, folderPath: String, search: String, pageable: Pageable): Page<PrecheckRunItem>
}
