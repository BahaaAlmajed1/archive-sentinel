package com.archivesentinel.service

import com.archivesentinel.api.PrecheckFile
import com.archivesentinel.api.PrecheckFolderSummary
import com.archivesentinel.api.PrecheckRequest
import com.archivesentinel.api.PrecheckRootSummary
import com.archivesentinel.api.PrecheckResponse
import com.archivesentinel.api.RootRunOverrideRequest
import com.archivesentinel.api.StorageRootScanProgressResponse
import com.archivesentinel.domain.MediaFile
import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.MediaStatus
import com.archivesentinel.domain.PrecheckRun
import com.archivesentinel.domain.PrecheckRunItem
import com.archivesentinel.domain.PrecheckRunItemRepository
import com.archivesentinel.domain.PrecheckRunRepository
import com.archivesentinel.domain.PrecheckStatus
import com.archivesentinel.domain.StorageRoot
import com.archivesentinel.domain.StorageRootRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.extension

@Service
class ScanService(
    private val storageRootRepository: StorageRootRepository,
    private val mediaFileRepository: MediaFileRepository,
    private val precheckRunRepository: PrecheckRunRepository,
    private val precheckRunItemRepository: PrecheckRunItemRepository,
    private val probeService: ProbeService,
    private val auditService: AuditService,
    private val settingsService: SettingsService,
    private val policyResolutionService: PolicyResolutionService,
    private val reportService: ReportService,
    private val appPathService: AppPathService,
    private val rootChangeMonitorService: RootChangeMonitorService,
) {
    private val previewLimit = 50
    private val backgroundExecutor = Executors.newCachedThreadPool()
    private val rootScanProgress = ConcurrentHashMap<java.util.UUID, RootScanProgress>()

    fun precheck(request: PrecheckRequest): PrecheckResponse {
        val roots = selectedRoots(request)
        val precheck = precheckRunRepository.save(
            PrecheckRun(
                rootIds = roots.joinToString(",") { it.id.toString() },
                status = PrecheckStatus.RUNNING,
                progressMessage = "Counting candidate files",
            ),
        )
        runPrecheck(precheck, roots, request)
        return toPrecheckResponse(precheckRunRepository.findById(precheck.id).orElseThrow())
    }

    fun startPrecheck(request: PrecheckRequest): PrecheckResponse {
        val roots = selectedRoots(request)
        if (roots.isNotEmpty() && roots.all { it.lastScannedAt != null && !rootChangeMonitorService.isDirty(it.id) }) {
            if (roots.size == 1) {
                roots.single().latestPrecheckRunId?.let { latestId ->
                    return precheckRunRepository.findById(latestId).orElse(null)?.let(::toPrecheckResponse)
                        ?: buildCachedPrecheck(roots, request)
                }
            }
            return buildCachedPrecheck(roots, request)
        }
        val precheck = precheckRunRepository.save(
            PrecheckRun(
                rootIds = roots.joinToString(",") { it.id.toString() },
                status = PrecheckStatus.RUNNING,
                progressMessage = "Queued",
            ),
        )
        backgroundExecutor.submit {
            runPrecheck(precheck, roots, request)
        }
        return toPrecheckResponse(precheck)
    }

    private fun runPrecheck(precheck: PrecheckRun, roots: List<StorageRoot>, request: PrecheckRequest) {
        try {
            val overrides = request.overrides.associateBy { it.rootId }
            precheck.progressMessage = "Counting candidate files"
            precheckRunRepository.save(precheck)
            val scannedFiles = roots.flatMap { root ->
                scanRoot(root) { event ->
                    when (event) {
                        is ScanProgress.Counted -> {
                            synchronized(precheck) {
                                precheck.totalFiles += event.totalFiles
                                precheck.progressMessage = "Scanning ${root.label}"
                            }
                        }
                        ScanProgress.Scanned -> {
                            synchronized(precheck) {
                                precheck.scannedFiles += 1
                                if (precheck.scannedFiles % 10 == 0 || precheck.scannedFiles == precheck.totalFiles) {
                                    precheck.progressMessage = "Scanned ${precheck.scannedFiles} of ${precheck.totalFiles} files"
                                    precheckRunRepository.save(precheck)
                                }
                            }
                        }
                    }
                }
            }
            val files = scannedFiles.map { it.media }
            val totalBytes = files.sumOf { it.sizeBytes }
            val settings = settingsService.current()
            val estimatedOutputBytes = (totalBytes * (settings.estimatedOutputRatioPercent / 100.0)).toLong()
            precheck.status = PrecheckStatus.COMPLETED
            precheck.completedAt = Instant.now()
            precheck.totalFiles = files.size
            precheck.scannedFiles = files.size
            precheck.totalBytes = totalBytes
            precheck.estimatedOutputBytes = estimatedOutputBytes
            precheck.estimatedSavingsBytes = totalBytes - estimatedOutputBytes
            precheck.progressMessage = "Precheck complete"
            savePrecheckItems(precheck, scannedFiles, overrides)
            refreshPrecheckArtifacts(precheck)
            precheckRunRepository.save(precheck)
            markRootsScanned(roots.map { it.id }, precheck.id)
        } catch (ex: Exception) {
            precheck.status = PrecheckStatus.FAILED
            precheck.completedAt = Instant.now()
            precheck.errorMessage = ex.message
            precheck.progressMessage = "Precheck failed"
            precheck.reportPath = reportService.generatePrecheckFailureReport(precheck).toString()
            precheck.logPath = reportService.generatePrecheckFailureLog(precheck).toString()
            precheckRunRepository.save(precheck)
            throw ex
        }
    }

    private fun buildCachedPrecheck(roots: List<StorageRoot>, request: PrecheckRequest): PrecheckResponse {
        val precheck = precheckRunRepository.save(
            PrecheckRun(
                rootIds = roots.joinToString(",") { it.id.toString() },
                status = PrecheckStatus.COMPLETED,
                progressMessage = "Showing cached scan results",
                completedAt = Instant.now(),
            ),
        )
        val cachedFiles = roots.flatMap { root ->
            mediaFileRepository.findAllByStorageRootId(root.id)
                .filter { Files.exists(Paths.get(it.originalPath)) }
                .map { ScannedMedia(it, newlyDiscovered = false) }
        }
        val files = cachedFiles.map { it.media }
        val totalBytes = files.sumOf { it.sizeBytes }
        val settings = settingsService.current()
        precheck.totalFiles = files.size
        precheck.scannedFiles = files.size
        precheck.totalBytes = totalBytes
        precheck.estimatedOutputBytes = (totalBytes * (settings.estimatedOutputRatioPercent / 100.0)).toLong()
        precheck.estimatedSavingsBytes = totalBytes - precheck.estimatedOutputBytes
        savePrecheckItems(precheck, cachedFiles, request.overrides.associateBy { it.rootId })
        refreshPrecheckArtifacts(precheck)
        val now = Instant.now()
        storageRootRepository.saveAll(
            storageRootRepository.findAllById(roots.map { it.id }).onEach {
                it.latestPrecheckRunId = precheck.id
                it.updatedAt = now
            },
        )
        return toPrecheckResponse(precheckRunRepository.save(precheck))
    }

    private fun selectedRoots(request: PrecheckRequest): List<StorageRoot> {
        val requested = if (request.rootIds.isEmpty()) {
            storageRootRepository.findAll().filter { it.enabled }
        } else {
            storageRootRepository.findAllById(request.rootIds).filter { it.enabled }
        }
        return requested
            .sortedBy { appPathService.resolve(it.path).nameCount }
            .fold(emptyList()) { accepted, root ->
                val path = appPathService.resolve(root.path)
                if (accepted.any { path.startsWith(appPathService.resolve(it.path)) }) accepted else accepted + root
            }
    }

    @Transactional
    fun scanRoot(root: StorageRoot, onProgress: (ScanProgress) -> Unit = {}): List<ScannedMedia> {
        val rootPath = appPathService.resolve(root.path)
        if (!rootPath.exists()) return emptyList()
        val settings = settingsService.current()
        val enabledExtensions = parseExtensions(settings.enabledExtensions)
        val policies = policyResolutionService.policies()
        val candidates = mutableListOf<ScanCandidate>()
        val directories = mutableListOf<Path>()
        Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                directories.add(dir)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (file.extension.lowercase() !in enabledExtensions) return FileVisitResult.CONTINUE
                if (policyResolutionService.resolve(file, settings, policies).excluded) return FileVisitResult.CONTINUE
                val relative = rootPath.relativize(file).toString().replace("\\", "/")
                candidates += ScanCandidate(file, relative, attrs.size(), attrs.lastAccessTime().toInstant(), attrs.lastModifiedTime().toInstant())
                return FileVisitResult.CONTINUE
            }
        })
        onProgress(ScanProgress.Counted(candidates.size))
        val existingByPath = if (candidates.isEmpty()) emptyMap()
        else mediaFileRepository.findAllByOriginalPathIn(candidates.map { it.path.toString() }).associateBy { it.originalPath }
        val analyzed = analyzeCandidates(candidates, settings.scanThreads, existingByPath, onProgress)
        val result = mutableListOf<ScannedMedia>()
        val savedMedia = mutableListOf<MediaFile>()
        val newlyDiscovered = mutableListOf<MediaFile>()
        analyzed.forEach { candidate ->
            val existing = existingByPath[candidate.path.toString()]
            val media = existing ?: MediaFile(
                storageRootId = root.id,
                originalPath = candidate.path.toString(),
                relativePath = candidate.relativePath,
            )
            media.storageRootId = root.id
            media.relativePath = candidate.relativePath
            media.sizeBytes = candidate.sizeBytes
            media.lastAccessedAt = candidate.lastAccessedAt
            media.status = if (existing == null) MediaStatus.DISCOVERED else media.status
            media.updatedAt = Instant.now()
            if (candidate.errorMessage == null) {
                media.metadataJson = candidate.metadataJson
                if (media.status !in activeStatuses) media.status = MediaStatus.ANALYZED
            } else {
                media.status = MediaStatus.FAILED
                media.validationJson = """{"error":"${candidate.errorMessage.replace("\"", "'")}"}"""
            }
            media.lastModifiedAt = candidate.lastModifiedAt
            savedMedia += media
            if (existing == null) newlyDiscovered += media
        }
        mediaFileRepository.saveAll(savedMedia).forEach { saved ->
            if (newlyDiscovered.any { it.id == saved.id }) auditService.record(saved.id, "DISCOVERED", "Discovered ${saved.originalPath}")
            result += ScannedMedia(saved, newlyDiscovered = newlyDiscovered.any { it.id == saved.id })
        }
        val seen = result.map { it.media.id }.toSet()
        result += trackedCompressedMedia(root).filter { it.media.id !in seen }
        rootChangeMonitorService.register(root, directories)
        return result
    }

    private fun trackedCompressedMedia(root: StorageRoot): List<ScannedMedia> =
        mediaFileRepository.findAllByStorageRootId(root.id)
            .filter { it.everOptimized }
            .filter { Files.exists(Paths.get(it.originalPath)) }
            .map { media ->
                val optimized = media.optimizedPath?.let { Paths.get(it) }
                if (optimized != null && Files.exists(optimized)) {
                    media.sizeBytes = runCatching { Files.size(optimized) }.getOrDefault(media.sizeBytes)
                }
                media.updatedAt = Instant.now()
                ScannedMedia(mediaFileRepository.save(media), newlyDiscovered = false)
            }

    fun precheckResponse(id: java.util.UUID, rootId: java.util.UUID?, search: String, page: Int, size: Int): PrecheckResponse {
        val precheck = precheckRunRepository.findById(id).orElseThrow()
        return toPrecheckResponse(precheck, rootId, search, page, size)
    }

    fun precheckFolderFiles(
        id: java.util.UUID,
        rootId: java.util.UUID?,
        folderPath: String,
        search: String,
        page: Int,
        size: Int,
    ): com.archivesentinel.api.PageResponse<PrecheckFile> {
        precheckRunRepository.findById(id).orElseThrow()
        val items = precheckRunItemRepository.searchFolder(
            id,
            rootId,
            folderPath,
            search,
            PageRequest.of(page, size, Sort.by("createdAt")),
        )
        val mediaById = mediaFileRepository.findAllById(items.content.map { it.mediaFileId }).associateBy { it.id }
        return com.archivesentinel.api.PageResponse(
            content = items.content.mapNotNull { item -> mediaById[item.mediaFileId]?.let { item.toResponse(it) } },
            page = items.number,
            size = items.size,
            totalElements = items.totalElements,
            totalPages = items.totalPages,
        )
    }

    fun startRootInventoryScan(rootId: java.util.UUID): StorageRootScanProgressResponse {
        val root = storageRootRepository.findById(rootId).orElseThrow()
        val progress = RootScanProgress(root.id)
        rootScanProgress[root.id] = progress
        backgroundExecutor.submit {
            try {
                progress.status = "RUNNING"
                progress.message = "Counting candidate files"
                scanRoot(root) { event ->
                    when (event) {
                        is ScanProgress.Counted -> {
                            synchronized(progress) {
                                progress.totalFiles = event.totalFiles
                                progress.message = "Scanning ${root.label}"
                            }
                        }
                        ScanProgress.Scanned -> {
                            synchronized(progress) {
                                progress.scannedFiles += 1
                                progress.message = "Scanned ${progress.scannedFiles} of ${progress.totalFiles} files"
                            }
                        }
                    }
                }
                progress.status = "COMPLETED"
                progress.message = "Scan complete"
                markRootsScanned(listOf(root.id), root.latestPrecheckRunId)
            } catch (ex: Exception) {
                progress.status = "FAILED"
                progress.message = ex.message ?: "Scan failed"
            }
        }
        return progress.toResponse()
    }

    fun rootInventoryScanProgress(): List<StorageRootScanProgressResponse> =
        rootScanProgress.values.sortedBy { it.status }.map { it.toResponse() }

    fun dismissRootInventoryScan(rootId: java.util.UUID) {
        rootScanProgress.remove(rootId)
    }

    fun scansForRoot(rootId: java.util.UUID): List<PrecheckRun> =
        precheckRunRepository.findAll()
            .filter { run -> run.rootIds.split(",").contains(rootId.toString()) }
            .sortedByDescending { it.startedAt }

    fun hasRunningScanForRoot(rootId: java.util.UUID): Boolean =
        precheckRunRepository.findByStatus(PrecheckStatus.RUNNING, PageRequest.of(0, 200))
            .content
            .any { run -> run.rootIds.split(",").contains(rootId.toString()) }

    private fun markRootsScanned(rootIds: List<java.util.UUID>, latestPrecheckRunId: java.util.UUID?) {
        val now = Instant.now()
        storageRootRepository.saveAll(
            storageRootRepository.findAllById(rootIds).onEach {
                it.lastScannedAt = now
                it.latestPrecheckRunId = latestPrecheckRunId ?: it.latestPrecheckRunId
                it.updatedAt = now
                rootChangeMonitorService.markClean(it.id)
            },
        )
    }

    @Transactional
    fun updateSelection(itemId: java.util.UUID, selected: Boolean): PrecheckFile {
        val item = precheckRunItemRepository.findById(itemId).orElseThrow()
        item.selected = selected
        item.updatedAt = Instant.now()
        precheckRunItemRepository.save(item)
        precheckRunRepository.findById(item.precheckRunId).orElse(null)?.let(::refreshPrecheckArtifacts)
        return item.toResponse(mediaFileRepository.findById(item.mediaFileId).orElseThrow())
    }

    @Transactional
    fun updateAllSelections(precheckId: java.util.UUID, selected: Boolean): PrecheckResponse {
        precheckRunItemRepository.updateSelections(precheckId, selected, Instant.now())
        val precheck = precheckRunRepository.findById(precheckId).orElseThrow()
        refreshPrecheckArtifacts(precheck)
        return toPrecheckResponse(precheck)
    }

    @Transactional
    fun updateFolderSelections(precheckId: java.util.UUID, rootId: java.util.UUID?, folderPath: String, selected: Boolean): PrecheckResponse {
        val now = Instant.now()
        precheckRunItemRepository.saveAll(
            precheckRunItemRepository.findFolderItems(precheckId, rootId, folderPath).onEach {
                it.selected = selected
                it.updatedAt = now
            },
        )
        val precheck = precheckRunRepository.findById(precheckId).orElseThrow()
        refreshPrecheckArtifacts(precheck)
        return toPrecheckResponse(precheck, rootId)
    }

    private fun savePrecheckItems(precheck: PrecheckRun, files: List<ScannedMedia>, overrides: Map<java.util.UUID, RootRunOverrideRequest>) {
        val roots = storageRootRepository.findAllById(files.map { it.media.storageRootId }.distinct()).associateBy { it.id }
        val settings = settingsService.current()
        val policies = policyResolutionService.policies()
        files.forEach { scanned ->
            val media = scanned.media
            val resolvedPolicy = policyResolutionService.resolve(Paths.get(media.originalPath), settings, policies)
            val override = overrides[media.storageRootId]
            val root = roots[media.storageRootId]
            val defaultSelected = !media.everOptimized && media.status !in setOf(
                MediaStatus.ARCHIVED,
                MediaStatus.AWAITING_DELETION_APPROVAL,
                MediaStatus.DELETED,
            )
            precheckRunItemRepository.save(
                PrecheckRunItem(
                    precheckRunId = precheck.id,
                    mediaFileId = media.id,
                    folderPath = parentFolder(media.relativePath),
                    selected = defaultSelected,
                    defaultSelected = defaultSelected,
                    retentionDays = override?.retentionDays ?: resolvedPolicy.retentionDays,
                    deletionMode = override?.deletionMode ?: resolvedPolicy.deletionMode,
                    outputMode = override?.outputMode ?: resolvedPolicy.outputMode,
                    optimizedRootOverride = override?.optimizedRootOverride?.takeIf { it.isNotBlank() } ?: root?.optimizedRootOverride,
                    archiveRootOverride = override?.archiveRootOverride?.takeIf { it.isNotBlank() } ?: root?.archiveRootOverride,
                ),
            )
        }
    }

    private fun toPrecheckResponse(
        precheck: PrecheckRun,
        rootId: java.util.UUID? = null,
        search: String = "",
        page: Int = 0,
        size: Int = previewLimit,
    ): PrecheckResponse {
        val items = precheckRunItemRepository.search(
            precheck.id,
            rootId,
            search,
            PageRequest.of(page, size, Sort.by("folderPath", "createdAt")),
        )
        val mediaById = mediaFileRepository.findAllById(items.content.map { it.mediaFileId }).associateBy { it.id }
        val storageRoots = storageRootRepository.findAllById(precheckRunItemRepository.summarizeByRoot(precheck.id).map { it.rootId })
            .associateBy { it.id }
        val roots = precheckRunItemRepository.summarizeByRoot(precheck.id).mapNotNull { summary ->
            storageRoots[summary.rootId]?.let { root ->
                PrecheckRootSummary(
                    id = root.id,
                    label = root.label,
                    path = root.path,
                    totalFiles = summary.totalFiles,
                    selectedFiles = summary.selectedFiles,
                    unselectedFiles = summary.totalFiles - summary.selectedFiles,
                )
            }
        }.sortedBy { it.label.lowercase() }
        val folders = precheckRunItemRepository.summarizeByFolder(precheck.id, rootId, search)
            .map { summary ->
                PrecheckFolderSummary(
                    path = summary.path,
                    totalFiles = summary.totalFiles,
                    selectedFiles = summary.selectedFiles,
                    unselectedFiles = summary.totalFiles - summary.selectedFiles,
                )
            }
        return PrecheckResponse(
            id = precheck.id,
            status = precheck.status,
            scannedFiles = precheck.scannedFiles,
            progressMessage = precheck.progressMessage,
            progressPercent = when {
                precheck.status == PrecheckStatus.COMPLETED -> 100
                precheck.totalFiles <= 0 -> 0
                else -> ((precheck.scannedFiles.toDouble() / precheck.totalFiles) * 100).toInt().coerceIn(0, 100)
            },
            totalFiles = precheck.totalFiles,
            totalBytes = precheck.totalBytes,
            estimatedOutputBytes = precheck.estimatedOutputBytes,
            estimatedSavingsBytes = precheck.estimatedSavingsBytes,
            reportUrl = precheck.reportPath?.let { reportService.artifactUrl("precheck", precheck.id) },
            logUrl = precheck.logPath?.let { reportService.artifactUrl("precheck-log", precheck.id) },
            selectedFiles = precheckRunItemRepository.countByPrecheckRunIdAndSelected(precheck.id, true),
            roots = roots,
            folders = folders,
            files = com.archivesentinel.api.PageResponse(
                content = items.content.mapNotNull { item -> mediaById[item.mediaFileId]?.let { item.toResponse(it) } },
                page = items.number,
                size = items.size,
                totalElements = items.totalElements,
                totalPages = items.totalPages,
            ),
        )
    }

    private fun PrecheckRunItem.toResponse(media: MediaFile): PrecheckFile =
        PrecheckFile(
            id = id,
            mediaFileId = media.id,
            storageRootId = media.storageRootId,
            path = media.originalPath,
            sizeBytes = media.sizeBytes,
            relativePath = media.relativePath,
            folderPath = folderPath,
            lastAccessedAt = media.lastAccessedAt,
            status = media.status,
            selected = selected,
            defaultSelected = defaultSelected,
            everOptimized = media.everOptimized,
        )

    private fun parentFolder(path: String): String =
        path.replace("\\", "/").substringBeforeLast("/", "Root")

    private val activeStatuses = setOf(
        MediaStatus.QUEUED,
        MediaStatus.TRANSCODING,
        MediaStatus.VALIDATING,
        MediaStatus.STAGED_CANDIDATE,
    )

    private fun analyzeCandidates(
        candidates: List<ScanCandidate>,
        configuredThreads: Int,
        existingByPath: Map<String, MediaFile>,
        onProgress: (ScanProgress) -> Unit,
    ): List<AnalyzedCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val threads = configuredThreads.coerceIn(1, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        if (threads == 1 || candidates.size == 1) return candidates.map { it.analyze(existingByPath[it.path.toString()], onProgress) }
        val executor = Executors.newFixedThreadPool(threads)
        return try {
            executor.invokeAll(candidates.map { candidate -> Callable { candidate.analyze(existingByPath[candidate.path.toString()], onProgress) } }).map { it.get() }
        } finally {
            executor.shutdown()
        }
    }

    private fun ScanCandidate.analyze(existing: MediaFile?, onProgress: (ScanProgress) -> Unit): AnalyzedCandidate =
        try {
            val cachedMetadata = existing
                ?.takeIf { cached ->
                    cached.metadataJson != null &&
                        cached.sizeBytes == sizeBytes &&
                        cached.lastModifiedAt?.toEpochMilli() == lastModifiedAt.toEpochMilli()
                }
                ?.metadataJson
            AnalyzedCandidate(path, relativePath, sizeBytes, lastAccessedAt, lastModifiedAt, cachedMetadata ?: probeService.probe(path).toString(), null)
        } catch (ex: Exception) {
            AnalyzedCandidate(path, relativePath, sizeBytes, lastAccessedAt, lastModifiedAt, null, ex.message ?: "Metadata probe failed")
        } finally {
            onProgress(ScanProgress.Scanned)
        }

    private fun refreshPrecheckArtifacts(precheck: PrecheckRun) {
        val rootIds = precheck.rootIds.split(",").mapNotNull { value -> runCatching { java.util.UUID.fromString(value) }.getOrNull() }
        val roots = storageRootRepository.findAllById(rootIds)
        val allFiles = mediaFileRepository.findAllById(
            precheckRunItemRepository.findByPrecheckRunId(precheck.id).map { it.mediaFileId },
        )
        precheck.reportPath = reportService.generatePrecheckReport(precheck, roots, allFiles).toString()
        precheck.logPath = reportService.generatePrecheckLog(precheck, roots, allFiles).toString()
        precheckRunRepository.save(precheck)
    }
}

data class ScannedMedia(val media: MediaFile, val newlyDiscovered: Boolean)
sealed interface ScanProgress {
    data class Counted(val totalFiles: Int) : ScanProgress
    data object Scanned : ScanProgress
}
private data class ScanCandidate(val path: Path, val relativePath: String, val sizeBytes: Long, val lastAccessedAt: Instant, val lastModifiedAt: Instant)
private data class AnalyzedCandidate(
    val path: Path,
    val relativePath: String,
    val sizeBytes: Long,
    val lastAccessedAt: Instant,
    val lastModifiedAt: Instant,
    val metadataJson: String?,
    val errorMessage: String?,
)

private data class RootScanProgress(
    val rootId: java.util.UUID,
    var status: String = "QUEUED",
    var totalFiles: Int = 0,
    var scannedFiles: Int = 0,
    var message: String = "Queued",
)

private fun RootScanProgress.toResponse(): StorageRootScanProgressResponse {
    val remaining = (totalFiles - scannedFiles).coerceAtLeast(0)
    val progressPercent = when {
        status == "COMPLETED" -> 100
        totalFiles <= 0 -> 0
        else -> ((scannedFiles.toDouble() / totalFiles) * 100).toInt().coerceIn(0, 100)
    }
    return StorageRootScanProgressResponse(
        rootId = rootId,
        status = status,
        totalFiles = totalFiles,
        scannedFiles = scannedFiles,
        remainingFiles = remaining,
        progressPercent = progressPercent,
        message = message,
    )
}
