package com.archivesentinel.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.archivesentinel.api.TdarrStatusResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

@Service
class TdarrService(
    private val settingsService: SettingsService,
    private val objectMapper: ObjectMapper,
    private val appPathService: AppPathService,
) {
    private val restTemplate = RestTemplate()
    private val scanBatchExecutor = Executors.newSingleThreadScheduledExecutor()
    private val scanBatchLock = Any()
    private val pendingScans = mutableMapOf<String, MutableList<PendingScan>>()
    private val scheduledFlushes = mutableMapOf<String, ScheduledFuture<*>>()

    fun status(): TdarrStatusResponse {
        val settings = settingsService.current()
        return try {
            val body = restTemplate.getForObject("${settings.tdarrBaseUrl}/api/v2/status", Map::class.java)
            TdarrStatusResponse(
                reachable = true,
                status = body?.get("status")?.toString(),
                version = body?.get("version")?.toString(),
            )
        } catch (_: RestClientException) {
            TdarrStatusResponse(false, null, null)
        }
    }

    fun transcodeToCandidate(sourceRoot: Path, original: Path, candidateRoot: Path, shouldCancel: () -> Boolean = { false }): Path {
        val settings = settingsService.current()
        ensureCompatibleWorkers(settings)
        val candidate = candidatePath(sourceRoot, original, candidateRoot, settings.outputContainerExtension)
        ensureTdarrWritableChain(candidateRoot, candidate.parent)
        Files.deleteIfExists(candidate)
        val cacheRoot = if (settings.stagingEnabled && !settings.stagingRoot.isNullOrBlank()) {
            appPathService.resolve(settings.stagingRoot!!).resolve(".tdarr-cache")
        } else {
            appPathService.resolve(settings.optimizedRoot).resolve(".tdarr-cache")
        }
        ensureTdarrWritableDirectory(cacheRoot)

        val libraryId = ensureCandidateLibrary(sourceRoot, candidateRoot, cacheRoot, settings)
        clearTdarrFileState(original)
        queueScan(libraryId, original, settings.tdarrSubmissionConcurrency.coerceAtLeast(1))
        waitForCandidate(original, candidate, shouldCancel)
        return candidate
    }

    private fun ensureTdarrWritableChain(root: Path, leaf: Path) {
        ensureTdarrWritableDirectory(root)
        val normalizedRoot = root.normalize()
        val normalizedLeaf = leaf.normalize()
        try {
            var current = normalizedRoot
            for (part in normalizedRoot.relativize(normalizedLeaf)) {
                current = current.resolve(part)
                ensureTdarrWritableDirectory(current)
            }
        } catch (_: IllegalArgumentException) {
            ensureTdarrWritableDirectory(normalizedLeaf)
        }
    }

    private fun ensureTdarrWritableDirectory(directory: Path) {
        directory.createDirectories()
        try {
            Files.setPosixFilePermissions(directory, EnumSet.allOf(PosixFilePermission::class.java))
        } catch (_: Exception) {
            val file = directory.toFile()
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        }
    }

    private fun candidatePath(sourceRoot: Path, original: Path, candidateRoot: Path, outputExtension: String): Path {
        val normalizedRoot = sourceRoot.toAbsolutePath().normalize()
        val normalizedOriginal = original.toAbsolutePath().normalize()
        val relative = if (normalizedOriginal.startsWith(normalizedRoot)) {
            normalizedRoot.relativize(normalizedOriginal)
        } else {
            normalizedOriginal.fileName
        }
        val fileName = relative.fileName.toString().substringBeforeLast('.') + ".${normalizeExtension(outputExtension)}"
        return candidateRoot.resolve(relative).resolveSibling(fileName)
    }

    @Synchronized
    private fun ensureCandidateLibrary(sourceRoot: Path, candidateRoot: Path, cacheRoot: Path, settings: com.archivesentinel.domain.AppSettings): String {
        val libraryId = "archive-sentinel-${sha256("${sourceRoot.normalize()}|${candidateRoot.normalize()}").take(16)}"
        val existing = crudGetById("LibrarySettingsJSONDB", libraryId)
        val library = candidateLibrary(libraryId, sourceRoot, candidateRoot, cacheRoot, settings)
        if (existing == null || existing.isNull) {
            crudWrite("insert", "LibrarySettingsJSONDB", libraryId, library)
        } else {
            crudWrite("update", "LibrarySettingsJSONDB", libraryId, library)
        }
        return libraryId
    }

    private fun candidateLibrary(
        libraryId: String,
        sourceRoot: Path,
        candidateRoot: Path,
        cacheRoot: Path,
        settings: com.archivesentinel.domain.AppSettings,
    ): Map<String, Any> {
        val outputExtension = ".${normalizeExtension(settings.outputContainerExtension)}"
        val containerFilter = parseExtensions(settings.enabledExtensions).joinToString(",")
        return linkedMapOf(
            "_id" to libraryId,
            "name" to "Archive Sentinel ${sourceRoot.fileName ?: "root"}",
            "folder" to tdarrPath(sourceRoot),
            "foldersToIgnore" to "",
            "foldersToIgnoreCaseInsensitive" to false,
            "folderWatchScanInterval" to 30,
            "scannerThreadCount" to 2,
            "cache" to tdarrPath(cacheRoot),
            "output" to tdarrPath(candidateRoot),
            "folderToFolderConversion" to true,
            "folderToFolderConversionDeleteSource" to false,
            "folderToFolderRecordHistory" to false,
            "copyIfConditionsMet" to false,
            "container" to outputExtension,
            "containerFilter" to containerFilter,
            "createdAt" to Instant.now().toEpochMilli(),
            "folderWatching" to false,
            "useFsEvents" to false,
            "scheduledScanFindNew" to false,
            "processLibrary" to true,
            "processTranscodes" to true,
            "processHealthChecks" to false,
            "scanOnStart" to false,
            "exifToolScan" to true,
            "mediaInfoScan" to true,
            "ffprobeShowData" to false,
            "isDirectoryLibrary" to false,
            "closedCaptionScan" to false,
            "scanButtons" to true,
            "scanFound" to "",
            "navItemSelected" to "navSourceFolder",
            "pluginIDs" to listOf(
                linkedMapOf(
                    "_id" to "archive-sentinel-plugin",
                    "id" to "Tdarr_Plugin_075a_Transcode_Customisable",
                    "checked" to true,
                    "source" to "Community",
                    "priority" to 0,
                    "InputsDB" to linkedMapOf(
                        "codecs_to_exclude" to settings.tdarrCodecsToExclude,
                        "cli" to "ffmpeg",
                        "transcode_arguments" to settings.tdarrTranscodeArguments,
                        "output_container" to outputExtension,
                    ),
                ),
            ),
            "pluginCommunity" to true,
            "handbrake" to false,
            "ffmpeg" to true,
            "handbrakescan" to false,
            "ffmpegscan" to true,
            "preset" to "",
            "decisionMaker" to linkedMapOf(
                "settingsPlugin" to true,
                "settingsFlows" to false,
                "settingsVideo" to false,
                "videoExcludeSwitch" to false,
                "video_codec_names_exclude" to emptyList<Map<String, Any>>(),
                "video_size_range_include" to linkedMapOf("min" to 0, "max" to 100000),
                "video_height_range_include" to linkedMapOf("min" to 0, "max" to 3000),
                "video_width_range_include" to linkedMapOf("min" to 0, "max" to 4000),
                "settingsAudio" to false,
                "audioExcludeSwitch" to false,
                "audio_codec_names_exclude" to emptyList<Map<String, Any>>(),
                "audio_size_range_include" to linkedMapOf("min" to 0, "max" to 10),
            ),
            "schedule" to emptyList<Map<String, Any>>(),
            "totalHealthCheckCount" to 0,
            "totalTranscodeCount" to 0,
            "sizeDiff" to 0,
            "holdNewFiles" to false,
            "holdFor" to 3600,
            "holdForDisplayUnit" to "hours",
            "pluginStackOverview" to true,
            "filterResolutionsSkip" to "",
            "filterCodecsSkip" to "",
            "filterContainersSkip" to "",
            "processPluginsSequentially" to true,
        )
    }

    private fun queueScan(libraryId: String, original: Path, batchSize: Int) {
        val completion = CompletableFuture<Unit>()
        var flushImmediately = false
        synchronized(scanBatchLock) {
            val pending = pendingScans.getOrPut(libraryId) { mutableListOf() }
            pending += PendingScan(original, completion)
            if (pending.size >= batchSize) {
                flushImmediately = true
            } else if (scheduledFlushes[libraryId] == null) {
                scheduledFlushes[libraryId] = scanBatchExecutor.schedule(
                    { flushScanBatch(libraryId) },
                    SCAN_BATCH_WINDOW_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
            }
        }
        if (flushImmediately) flushScanBatch(libraryId)
        completion.get()
    }

    private fun flushScanBatch(libraryId: String) {
        val batch = synchronized(scanBatchLock) {
            scheduledFlushes.remove(libraryId)?.cancel(false)
            pendingScans.remove(libraryId).orEmpty()
        }
        if (batch.isEmpty()) return
        try {
            scanFiles(libraryId, batch.map { it.original })
            batch.forEach { it.completion.complete(Unit) }
        } catch (ex: Exception) {
            batch.forEach { it.completion.completeExceptionally(ex) }
        }
    }

    private fun scanFiles(libraryId: String, originals: List<Path>) {
        post(
            "/api/v2/scan-files",
            mapOf(
                "data" to mapOf(
                    "scanConfig" to mapOf(
                        "dbID" to libraryId,
                        "mode" to "scanFolderWatcher",
                        "arrayOrPath" to originals.map(::tdarrPath),
                    ),
                ),
            ),
        )
    }

    fun clearQueueForPaths(paths: Collection<Path>): Int {
        paths.distinctBy { it.toAbsolutePath().normalize().toString() }.forEach { clearTdarrFileState(it) }
        return paths.size
    }

    fun clearTdarrFileState(original: Path) {
        val ids = listOf(original.toString(), tdarrPath(original)).distinct()
        listOf("StagedJSONDB", "FileJSONDB", "F2FOutputJSONDB").forEach { collection ->
            ids.forEach { docId ->
                try {
                    crudRemoveOne(collection, docId)
                } catch (_: Exception) {
                    // Tdarr treats missing documents as a harmless no-op for this workflow.
                }
            }
        }
    }

    private fun waitForCandidate(original: Path, candidate: Path, shouldCancel: () -> Boolean) {
        val deadline = Instant.now().plus(Duration.ofHours(6))
        val originalIds = listOf(tdarrPath(original), original.toString()).distinct()
        var accepted = false
        var stableSize: Long? = null
        var stablePolls = 0

        while (Instant.now().isBefore(deadline)) {
            if (shouldCancel()) {
                clearTdarrFileState(original)
                throw CancellationException("Run cancelled")
            }
            val staged = originalIds.firstNotNullOfOrNull { crudGetById("StagedJSONDB", it)?.takeUnless(JsonNode::isNull) }
            val stagedStatus = staged?.get("status")?.asText()
            when (stagedStatus) {
                "transcodeSuccess" -> if (!accepted) {
                    post(
                        "/api/v2/transcode-user-verdict",
                        mapOf("data" to mapOf("obj" to objectMapper.convertValue(staged, Map::class.java), "verdict" to "accept")),
                    )
                    accepted = true
                }
                "transcodeError", "transcodeCancelled", "copyError", "copyFailed" ->
                    error(tdarrFailure("Tdarr candidate generation failed with status $stagedStatus", staged, null))
            }

            if (candidate.exists()) {
                val size = candidate.fileSize()
                if (size == stableSize) {
                    stablePolls += 1
                } else {
                    stableSize = size
                    stablePolls = 0
                }
                if (stablePolls >= 2 && staged == null) return
            }

            val fileState = originalIds.firstNotNullOfOrNull { crudGetById("FileJSONDB", it)?.takeUnless(JsonNode::isNull) }
            val transcodeDecision = fileState?.get("TranscodeDecisionMaker")?.asText()
            if (!candidate.exists()) {
                when (transcodeDecision) {
                    "Not required" -> error(tdarrFailure("Tdarr did not generate a candidate because the item was marked Not required", staged, fileState))
                    "Transcode error", "Transcode cancelled", "Copy error", "Copy failed" ->
                        error(tdarrFailure("Tdarr candidate generation failed with decision $transcodeDecision", staged, fileState))
                }
            }

            Thread.sleep(2_000)
        }
        error("Timed out waiting for Tdarr candidate for $original")
    }

    private fun ensureCompatibleWorkers(settings: com.archivesentinel.domain.AppSettings) {
        val nodes = runCatching {
            restTemplate.getForObject("${settings.tdarrBaseUrl}/api/v2/get-nodes", Map::class.java)
        }.getOrNull() ?: return
        val firstNode = nodes.entries.firstOrNull() ?: return
        val nodeId = firstNode.key.toString()
        val node = firstNode.value as? Map<*, *> ?: return
        val workerLimits = node["workerLimits"] as? Map<*, *> ?: return
        val gpuWorkers = (workerLimits["transcodegpu"] as? Number)?.toInt() ?: 0
        val cpuWorkers = (workerLimits["transcodecpu"] as? Number)?.toInt() ?: 0
        if (requiresGpuWorker(settings.tdarrTranscodeArguments)) {
            if (gpuWorkers == 0) alterWorkerLimit(nodeId, "increase", "transcodegpu")
            repeat(cpuWorkers) { alterWorkerLimit(nodeId, "decrease", "transcodecpu") }
        } else if (cpuWorkers == 0) {
            alterWorkerLimit(nodeId, "increase", "transcodecpu")
        }
    }

    private fun alterWorkerLimit(nodeId: String, process: String, workerType: String) {
        post(
            "/api/v2/alter-worker-limit",
            mapOf("data" to mapOf("nodeID" to nodeId, "process" to process, "workerType" to workerType)),
        )
    }

    private fun requiresGpuWorker(arguments: String): Boolean =
        listOf("nvenc", "cuda", "vaapi", "qsv", "amf").any { token -> arguments.contains(token, ignoreCase = true) }

    private fun tdarrFailure(prefix: String, staged: JsonNode?, fileState: JsonNode?): String {
        val detail = listOf(staged, fileState)
            .flatMap { node ->
                listOfNotNull(
                    textAt(node, "error"),
                    textAt(node, "errorMessage"),
                    textAt(node, "reason"),
                    textAt(node, "lastPluginOutput"),
                    textAt(node, "lastPluginDetails"),
                    textAt(node, "TranscodeDecisionMaker"),
                    textAt(node, "ffmpegCommand"),
                )
            }
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ")
            .take(1_500)
        return if (detail.isBlank()) prefix else "$prefix: $detail"
    }

    private fun textAt(node: JsonNode?, field: String): String? =
        node?.get(field)?.takeUnless { it.isNull }?.let {
            if (it.isTextual) it.asText() else it.toString()
        }

    private fun crudGetById(collection: String, docId: String): JsonNode? =
        postJson(
            "/api/v2/cruddb",
            mapOf("data" to mapOf("collection" to collection, "mode" to "getById", "docID" to docId)),
        )

    private fun crudWrite(mode: String, collection: String, docId: String, obj: Map<String, Any>) {
        post("/api/v2/cruddb", mapOf("data" to mapOf("collection" to collection, "mode" to mode, "docID" to docId, "obj" to obj)))
    }

    private fun crudRemoveOne(collection: String, docId: String) {
        post("/api/v2/cruddb", mapOf("data" to mapOf("collection" to collection, "mode" to "removeOne", "docID" to docId)))
    }

    private fun post(path: String, body: Any) {
        val settings = settingsService.current()
        restTemplate.postForObject("${settings.tdarrBaseUrl}$path", body, String::class.java)
    }

    private fun postJson(path: String, body: Any): JsonNode? {
        val settings = settingsService.current()
        val response = restTemplate.postForObject("${settings.tdarrBaseUrl}$path", body, String::class.java)
        return response?.let { objectMapper.readTree(it) }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun tdarrPath(path: Path): String =
        path.toAbsolutePath().normalize().toString().replace('\\', '/')

    private data class PendingScan(val original: Path, val completion: CompletableFuture<Unit>)

    private companion object {
        const val SCAN_BATCH_WINDOW_MILLIS = 250L
    }
}
