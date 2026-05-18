package com.archivesentinel.service

import com.archivesentinel.domain.AppSettings
import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.OptimizationRun
import com.archivesentinel.domain.OptimizationRunItem
import com.archivesentinel.domain.OptimizationRunItemRepository
import com.archivesentinel.domain.OptimizationRunRepository
import com.archivesentinel.domain.OutputMode
import com.archivesentinel.domain.PrecheckRunItemRepository
import com.archivesentinel.domain.RunStatus
import com.archivesentinel.domain.StorageRoot
import com.archivesentinel.domain.StorageRootRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream

@Service
class OptimizationService(
    private val settingsService: SettingsService,
    private val mediaFileRepository: MediaFileRepository,
    private val storageRootRepository: StorageRootRepository,
    private val optimizationRunRepository: OptimizationRunRepository,
    private val optimizationRunItemRepository: OptimizationRunItemRepository,
    private val precheckRunItemRepository: PrecheckRunItemRepository,
    private val validationService: ValidationService,
    private val policyResolutionService: PolicyResolutionService,
    private val auditService: AuditService,
    private val tdarrService: TdarrService,
    private val reportService: ReportService,
    private val appPathService: AppPathService,
) {
    private val executor = Executors.newCachedThreadPool()
    private val cancelledRuns = ConcurrentHashMap.newKeySet<UUID>()

    fun runs(page: Int, size: Int, status: RunStatus?, search: String = ""): Page<OptimizationRun> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        return if (search.isNotBlank()) {
            optimizationRunRepository.search(status, search, pageable)
        } else {
            status?.let { optimizationRunRepository.findByStatus(it, pageable) } ?: optimizationRunRepository.findAll(pageable)
        }
    }

    fun run(id: UUID): OptimizationRun = optimizationRunRepository.findById(id).orElseThrow()

    fun runItems(id: UUID, pageable: Pageable): Page<OptimizationRunItem> = optimizationRunItemRepository.findByRunId(id, pageable)
    fun searchRunItems(id: UUID, search: String, pageable: Pageable): Page<OptimizationRunItem> =
        optimizationRunItemRepository.searchItems(id, search, pageable)

    fun savedBytes(id: UUID): Long = optimizationRunItemRepository.totalSavedBytesByRunId(id)

    fun start(ids: List<UUID>): OptimizationRun {
        val files = mediaFileRepository.findAllById(ids)
        val run = optimizationRunRepository.save(
            OptimizationRun(
                status = RunStatus.QUEUED,
                totalFiles = files.size,
                totalInputBytes = files.sumOf { it.sizeBytes },
            ),
        )
        val settings = settingsService.current()
        files.forEach { media ->
            val root = storageRootRepository.findById(media.storageRootId).orElseThrow()
            val targets = targetRoots(settings, root, null)
            media.status = MediaStatus.QUEUED
            media.updatedAt = Instant.now()
            mediaFileRepository.save(media)
            optimizationRunItemRepository.save(
                OptimizationRunItem(
                    runId = run.id,
                    mediaFileId = media.id,
                    storageRootId = root.id,
                    storageRootLabel = root.label,
                    storageRootPath = root.path,
                    optimizedRoot = targets.optimizedRoot.toString(),
                    archiveRoot = targets.archiveRoot.toString(),
                    status = MediaStatus.QUEUED,
                    originalPath = media.originalPath,
                    inputBytes = media.sizeBytes,
                ),
            )
        }
        executor.submit { processRun(run.id, files.map { it.id }) }
        return run
    }

    fun startFromPrecheck(precheckId: UUID): OptimizationRun {
        val selectedItems = precheckRunItemRepository.findByPrecheckRunIdAndSelected(precheckId, true)
        val files = mediaFileRepository.findAllById(selectedItems.map { it.mediaFileId })
        val overrides = selectedItems.associateBy { it.mediaFileId }
        val settings = settingsService.current()
        val run = optimizationRunRepository.save(
            OptimizationRun(
                status = RunStatus.QUEUED,
                totalFiles = files.size,
                totalInputBytes = files.sumOf { it.sizeBytes },
            ),
        )
        files.forEach { media ->
            val root = storageRootRepository.findById(media.storageRootId).orElseThrow()
            val override = overrides[media.id]
            val targets = targetRoots(settings, root, override)
            media.status = MediaStatus.QUEUED
            media.updatedAt = Instant.now()
            mediaFileRepository.save(media)
            optimizationRunItemRepository.save(
                OptimizationRunItem(
                    runId = run.id,
                    mediaFileId = media.id,
                    storageRootId = root.id,
                    storageRootLabel = root.label,
                    storageRootPath = root.path,
                    optimizedRoot = targets.optimizedRoot.toString(),
                    archiveRoot = targets.archiveRoot.toString(),
                    status = MediaStatus.QUEUED,
                    originalPath = media.originalPath,
                    inputBytes = media.sizeBytes,
                ),
            )
        }
        executor.submit { processRun(run.id, files.map { it.id }, overrides) }
        return run
    }

    fun cancelRun(id: UUID): OptimizationRun {
        val run = optimizationRunRepository.findById(id).orElseThrow()
        if (run.status in terminalRunStatuses) return run
        cancelledRuns += id
        run.status = RunStatus.CANCELLED
        run.completedAt = Instant.now()
        val items = optimizationRunItemRepository.findByRunId(id)
        tdarrService.clearQueueForPaths(
            items.flatMap { item ->
                listOfNotNull(item.originalPath, item.candidatePath, item.optimizedPath).map { Paths.get(it) }
            },
        )
        items.filter { it.status in cancellableMediaStatuses }.forEach { item ->
            item.status = MediaStatus.CANCELLED
            item.updatedAt = Instant.now()
            item.candidatePath?.let { runCatching { Files.deleteIfExists(Paths.get(it)) } }
            optimizationRunItemRepository.save(item)
            item.mediaFileId
                ?.let(mediaFileRepository::findById)
                ?.orElse(null)
                ?.let { media ->
                    media.candidatePath?.let { runCatching { Files.deleteIfExists(Paths.get(it)) } }
                    media.status = MediaStatus.ANALYZED
                    media.candidatePath = null
                    media.validationJson = null
                    media.updatedAt = Instant.now()
                    mediaFileRepository.save(media)
                }
        }
        run.completedFiles = items.count { it.status == MediaStatus.ARCHIVED }
        run.failedFiles = items.count { it.status == MediaStatus.FAILED }
        run.reportPath = reportService.generateRunReport(run).toString()
        run.logPath = reportService.generateRunLog(run).toString()
        return optimizationRunRepository.save(run)
    }

    fun forceClearTdarrQueue(): Int {
        val active = mediaFileRepository.findAllByStatusIn(cancellableMediaStatuses)
        val paths = active.flatMap { media ->
            listOfNotNull(media.originalPath, media.candidatePath, media.optimizedPath).map { Paths.get(it) }
        }
        tdarrService.clearQueueForPaths(paths)
        active.forEach { media ->
            media.candidatePath?.let { runCatching { Files.deleteIfExists(Paths.get(it)) } }
            media.candidatePath = null
            media.status = MediaStatus.ANALYZED
            media.updatedAt = Instant.now()
            mediaFileRepository.save(media)
        }
        return paths.distinctBy { it.toString() }.size
    }

    fun processRun(runId: UUID, ids: List<UUID>, overrides: Map<UUID, com.archivesentinel.domain.PrecheckRunItem> = emptyMap()) {
        val settings = settingsService.current()
        val run = optimizationRunRepository.findById(runId).orElseThrow()
        if (run.status == RunStatus.CANCELLED) return
        run.status = RunStatus.RUNNING
        optimizationRunRepository.save(run)
        val completed = AtomicInteger()
        val failed = AtomicInteger()
        val outputBytes = AtomicLong()
        val workerCount = settings.tdarrSubmissionConcurrency.coerceAtLeast(1)
        val pool = Executors.newFixedThreadPool(workerCount)
        try {
            val itemByMediaId = optimizationRunItemRepository.findByRunId(runId).mapNotNull { item ->
                item.mediaFileId?.let { it to item.id }
            }.toMap()
            val futures = ids.map { id ->
                pool.submit {
                    if (isCancelled(runId)) return@submit
                    val result = processOneFile(runId, settings, id, itemByMediaId[id], overrides[id])
                    if (isCancelled(runId)) return@submit
                    val freshRun = optimizationRunRepository.findById(runId).orElseThrow()
                    if (result.success) completed.incrementAndGet() else failed.incrementAndGet()
                    freshRun.completedFiles = completed.get()
                    freshRun.failedFiles = failed.get()
                    freshRun.totalOutputBytes = outputBytes.addAndGet(result.outputBytes)
                    optimizationRunRepository.save(freshRun)
                }
            }
            futures.forEach { it.get() }
        } catch (ex: Exception) {
            val failedRun = optimizationRunRepository.findById(runId).orElseThrow()
            failedRun.status = RunStatus.FAILED
            failedRun.completedAt = Instant.now()
            optimizationRunRepository.save(failedRun)
        } finally {
            pool.shutdown()
        }
        val finalRun = optimizationRunRepository.findById(runId).orElseThrow()
        if (finalRun.status == RunStatus.CANCELLED || isCancelled(runId)) {
            finalRun.completedAt = finalRun.completedAt ?: Instant.now()
            finalRun.status = RunStatus.CANCELLED
        } else if (finalRun.status != RunStatus.FAILED) {
            finalRun.completedAt = Instant.now()
            finalRun.status = if (finalRun.failedFiles > 0) RunStatus.COMPLETED_WITH_FAILURES else RunStatus.COMPLETED
        }
        finalRun.reportPath = reportService.generateRunReport(finalRun).toString()
        finalRun.logPath = reportService.generateRunLog(finalRun).toString()
        optimizationRunRepository.save(finalRun)
    }

    private fun processOneFile(
        runId: UUID,
        settings: AppSettings,
        id: UUID,
        itemId: UUID?,
        override: com.archivesentinel.domain.PrecheckRunItem? = null,
    ): FileResult {
        val media = mediaFileRepository.findById(id).orElseThrow()
        val item = itemId?.let { optimizationRunItemRepository.findById(it).orElse(null) }
        val previousOptimized = media.optimizedPath
        val previousEverOptimized = media.everOptimized
        val previousLastOptimizedAt = media.lastOptimizedAt
        var producedCandidate: Path? = null
        var producedOptimized: Path? = null
        var committed = false
        fun rollbackProducedOutput() {
            if (committed) return
            producedCandidate?.let { runCatching { Files.deleteIfExists(it) } }
            producedOptimized
                ?.takeIf { previousOptimized != it.toString() }
                ?.let { runCatching { Files.deleteIfExists(it) } }
            media.optimizedPath = previousOptimized
            media.everOptimized = previousEverOptimized
            media.lastOptimizedAt = previousLastOptimizedAt
        }
        return try {
            ensureNotCancelled(runId)
            mark(media.id, item, MediaStatus.TRANSCODING)
            media.status = MediaStatus.TRANSCODING
            media.validationJson = null
            media.candidatePath = null
            media.archivedPath = null
            media.archivedAt = null
            media.retentionUntil = null
            mediaFileRepository.save(media)
            val original = Paths.get(media.originalPath)
            val storageRoot = storageRootRepository.findById(media.storageRootId).orElseThrow()
            val source = sourceFor(media)
            val sourceRoot = sourceRootFor(appPathService.resolve(storageRoot.path), source)
            val relative = Paths.get(media.relativePath)
            val resolvedPolicy = policyResolutionService.resolve(original, settings)
            if (resolvedPolicy.excluded) error("File excluded by policy")
            val targets = targetRoots(settings, storageRoot, override)
            val candidateRoot = if (settings.stagingEnabled && !settings.stagingRoot.isNullOrBlank()) {
                appPathService.resolve(settings.stagingRoot!!)
            } else {
                targets.optimizedRoot.resolve(".candidates")
            }
            val optimizedName = relative.fileName.toString().substringBeforeLast('.') + ".optimized.${normalizeExtension(settings.outputContainerExtension)}"
            val candidate = tdarrService.transcodeToCandidate(sourceRoot, source, candidateRoot) { isCancelled(runId) }
            producedCandidate = candidate
            ensureNotCancelled(runId)
            media.candidatePath = candidate.toString()
            updateItem(item, MediaStatus.VALIDATING, candidatePath = candidate.toString())
            media.status = MediaStatus.VALIDATING
            mediaFileRepository.save(media)
            val stagedValidation = validationService.validate(source, candidate)
            media.validationJson = stagedValidation.details
            item?.validationJson = stagedValidation.details
            if (!stagedValidation.valid) error("Candidate validation failed")
            media.status = MediaStatus.STAGED_CANDIDATE
            updateItem(item, MediaStatus.STAGED_CANDIDATE)
            mediaFileRepository.save(media)

            val outputMode = override?.outputMode ?: resolvedPolicy.outputMode
            val retentionDays = override?.retentionDays ?: resolvedPolicy.retentionDays
            val deletionMode = override?.deletionMode ?: resolvedPolicy.deletionMode
            val optimized = when (outputMode) {
                OutputMode.PARALLEL -> targets.optimizedRoot.resolve(relative).resolveSibling(optimizedName)
                OutputMode.SAME_LIBRARY -> source.resolveSibling(optimizedName)
            }
            optimized.parent.createDirectories()
            val stagedHash = sha256(candidate)
            Files.move(candidate, optimized, StandardCopyOption.REPLACE_EXISTING)
            producedCandidate = null
            producedOptimized = optimized
            val finalValidation = validationService.validate(source, optimized)
            ensureNotCancelled(runId)
            media.validationJson = finalValidation.details
            item?.validationJson = finalValidation.details
            if (!finalValidation.valid) error("Final validation failed")
            require(stagedHash == sha256(optimized)) { "Promoted file checksum does not match staged candidate" }
            media.optimizedPath = optimized.toString()
            media.status = MediaStatus.PROMOTED
            updateItem(item, MediaStatus.PROMOTED, optimizedPath = optimized.toString())
            mediaFileRepository.save(media)

            val archived = targets.archiveRoot.resolve(relative)
            if (Files.exists(original)) {
                archived.parent.createDirectories()
                Files.move(original, archived, StandardCopyOption.REPLACE_EXISTING)
                media.archivedPath = archived.toString()
                media.archivedAt = Instant.now()
            }
            val outputBytes = optimized.fileSize()
            val savedBytes = (media.sizeBytes - outputBytes).coerceAtLeast(0)
            media.retentionUntil = Instant.now().plus(retentionDays.toLong(), ChronoUnit.DAYS)
            media.retentionDeletionMode = deletionMode
            media.everOptimized = true
            media.lastOptimizedAt = Instant.now()
            media.status = MediaStatus.ARCHIVED
            media.updatedAt = Instant.now()
            mediaFileRepository.save(media)
            updateItem(
                item,
                MediaStatus.ARCHIVED,
                optimizedPath = optimized.toString(),
                archivedPath = media.archivedPath,
                outputBytes = outputBytes,
                savedBytes = savedBytes,
            )
            committed = true
            auditService.record(media.id, "ARCHIVED", "Optimized to ${media.optimizedPath}; archived original to ${media.archivedPath ?: "already archived"}")
            FileResult(true, outputBytes)
        } catch (_: RunCancelledException) {
            rollbackProducedOutput()
            media.status = MediaStatus.ANALYZED
            media.candidatePath?.let { runCatching { Files.deleteIfExists(Paths.get(it)) } }
            media.candidatePath = null
            media.validationJson = null
            media.updatedAt = Instant.now()
            mediaFileRepository.save(media)
            updateItem(item, MediaStatus.CANCELLED, error = "Run cancelled")
            FileResult(false, 0L)
        } catch (_: CancellationException) {
            rollbackProducedOutput()
            media.status = MediaStatus.ANALYZED
            media.candidatePath?.let { runCatching { Files.deleteIfExists(Paths.get(it)) } }
            media.candidatePath = null
            media.validationJson = null
            media.updatedAt = Instant.now()
            mediaFileRepository.save(media)
            updateItem(item, MediaStatus.CANCELLED, error = "Run cancelled")
            FileResult(false, 0L)
        } catch (ex: Exception) {
            rollbackProducedOutput()
            media.status = MediaStatus.FAILED
            if (media.validationJson.isNullOrBlank()) media.validationJson = """{"error":"${ex.message?.replace("\"", "'")}"}"""
            media.updatedAt = Instant.now()
            mediaFileRepository.save(media)
            updateItem(item, MediaStatus.FAILED, error = ex.message ?: "Unknown error")
            auditService.record(media.id, "FAILED", ex.message ?: "Unknown error")
            FileResult(false, 0L)
        }
    }

    private fun mark(mediaId: UUID, item: OptimizationRunItem?, status: MediaStatus) {
        updateItem(item, status)
        auditService.record(mediaId, status.name, status.name.lowercase().replaceFirstChar { it.uppercase() })
    }

    private fun updateItem(
        item: OptimizationRunItem?,
        status: MediaStatus,
        candidatePath: String? = null,
        optimizedPath: String? = null,
        archivedPath: String? = null,
        outputBytes: Long? = null,
        savedBytes: Long? = null,
        error: String? = null,
    ) {
        if (item == null) return
        item.status = status
        if (candidatePath != null) item.candidatePath = candidatePath
        if (optimizedPath != null) item.optimizedPath = optimizedPath
        if (archivedPath != null) item.archivedPath = archivedPath
        if (outputBytes != null) item.outputBytes = outputBytes
        if (savedBytes != null) item.savedBytes = savedBytes
        if (error != null) item.errorMessage = error
        item.updatedAt = Instant.now()
        optimizationRunItemRepository.save(item)
    }

    private fun targetRoots(
        settings: AppSettings,
        storageRoot: StorageRoot,
        override: com.archivesentinel.domain.PrecheckRunItem?,
    ): TargetRoots {
        val optimizedOverride = override?.optimizedRootOverride?.takeIf { it.isNotBlank() } ?: storageRoot.optimizedRootOverride?.takeIf { it.isNotBlank() }
        val archiveOverride = override?.archiveRootOverride?.takeIf { it.isNotBlank() } ?: storageRoot.archiveRootOverride?.takeIf { it.isNotBlank() }
        return TargetRoots(
            optimizedRoot = optimizedOverride?.let(appPathService::resolve)
                ?: appPathService.resolve(settings.optimizedRoot).resolve(rootNamespace(storageRoot.path)),
            archiveRoot = archiveOverride?.let(appPathService::resolve)
                ?: appPathService.resolve(settings.archiveRoot).resolve(rootNamespace(storageRoot.path)),
        )
    }

    private fun rootNamespace(rawPath: String): Path {
        val path = appPathService.resolve(rawPath).normalize()
        val names = (0 until path.nameCount).map { path.getName(it).toString() }
        val tail = names.takeLast(2).ifEmpty { listOf("root") }
        return tail.fold(Paths.get("")) { current, part -> current.resolve(safePathSegment(part)) }
    }

    private fun safePathSegment(value: String): String =
        value.replace(Regex("""[<>:"/\\|?*]"""), "_").ifBlank { "root" }

    private fun sourceFor(media: com.archivesentinel.domain.MediaFile): Path {
        val original = Paths.get(media.originalPath)
        if (Files.exists(original)) return original
        val optimized = media.optimizedPath?.let { Paths.get(it) }
        if (optimized != null && Files.exists(optimized)) return optimized
        return original
    }

    private fun sourceRootFor(storageRoot: Path, source: Path): Path =
        if (source.toAbsolutePath().normalize().startsWith(storageRoot.toAbsolutePath().normalize())) {
            storageRoot.toAbsolutePath().normalize()
        } else {
            source.toAbsolutePath().normalize().parent ?: storageRoot.toAbsolutePath().normalize()
        }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        path.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isCancelled(runId: UUID): Boolean =
        runId in cancelledRuns || optimizationRunRepository.findById(runId).orElse(null)?.status == RunStatus.CANCELLED

    private fun ensureNotCancelled(runId: UUID) {
        if (isCancelled(runId)) throw RunCancelledException()
    }

    private companion object {
        val terminalRunStatuses = setOf(
            RunStatus.COMPLETED,
            RunStatus.COMPLETED_WITH_FAILURES,
            RunStatus.CANCELLED,
            RunStatus.FAILED,
        )
        val cancellableMediaStatuses = setOf(
            MediaStatus.QUEUED,
            MediaStatus.TRANSCODING,
            MediaStatus.VALIDATING,
            MediaStatus.STAGED_CANDIDATE,
        )
    }
}

private data class FileResult(val success: Boolean, val outputBytes: Long)
private data class TargetRoots(val optimizedRoot: Path, val archiveRoot: Path)
private class RunCancelledException : RuntimeException()
