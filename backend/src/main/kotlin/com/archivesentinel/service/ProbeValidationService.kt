package com.archivesentinel.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.fileSize
import kotlin.math.abs

@Service
class ProbeService(
    private val objectMapper: ObjectMapper,
    private val mediaToolPathService: MediaToolPathService,
) {
    fun probe(path: Path): JsonNode {
        val process = ProcessBuilder(
            mediaToolPathService.ffprobePath(),
            "-v", "error",
            "-show_format",
            "-show_streams",
            "-show_chapters",
            "-of", "json",
            path.toString(),
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        require(exit == 0) { "ffprobe failed for $path: $text" }
        return objectMapper.readTree(text)
    }
}

data class ValidationResult(val valid: Boolean, val details: String)

@Service
class ValidationService(
    private val probeService: ProbeService,
    private val settingsService: SettingsService,
    private val objectMapper: ObjectMapper,
    private val mediaToolPathService: MediaToolPathService,
) {
    fun validate(original: Path, candidate: Path): ValidationResult {
        val originalProbe = probeService.probe(original)
        val candidateProbe = probeService.probe(candidate)
        val originalStreams = originalProbe["streams"]
        val candidateStreams = candidateProbe["streams"]
        val originalDuration = originalProbe["format"]["duration"].asDouble()
        val candidateDuration = candidateProbe["format"]["duration"].asDouble()
        val originalVideo = originalStreams.count { it["codec_type"].asText() == "video" }
        val candidateVideo = candidateStreams.count { it["codec_type"].asText() == "video" }
        val originalAudio = originalStreams.count { it["codec_type"].asText() == "audio" }
        val candidateAudio = candidateStreams.count { it["codec_type"].asText() == "audio" }
        val originalSubtitles = originalStreams.count { it["codec_type"].asText() == "subtitle" }
        val candidateSubtitles = candidateStreams.count { it["codec_type"].asText() == "subtitle" }
        val durationDelta = abs(originalDuration - candidateDuration)
        val settings = settingsService.current()
        val checks = linkedMapOf(
            "candidateExists" to java.nio.file.Files.exists(candidate),
            "videoStreamCountMatches" to (candidateVideo == originalVideo && candidateVideo > 0),
            "audioStreamCountMatches" to (candidateAudio == originalAudio),
            "subtitleStreamCountMatches" to (candidateSubtitles == originalSubtitles),
            "streamTopologyMatches" to (streamTopology(originalProbe) == streamTopology(candidateProbe)),
            "chapterCountMatches" to ((originalProbe["chapters"]?.size() ?: 0) == (candidateProbe["chapters"]?.size() ?: 0)),
            "criticalMetadataPreserved" to criticalMetadataPreserved(originalProbe, candidateProbe),
            "durationWithinTolerance" to (durationDelta <= settings.durationToleranceSeconds),
            "candidateSmallerThanOriginal" to (candidate.fileSize() < original.fileSize()),
            "candidateDecodesWithoutErrors" to decodesWithoutErrors(candidate),
        )
        val valid = checks.values.all { it }
        return ValidationResult(valid, objectMapper.writeValueAsString(checks + ("durationDeltaSeconds" to durationDelta)))
    }

    private fun streamTopology(probe: JsonNode): List<Map<String, String>> =
        probe["streams"].map { stream ->
            linkedMapOf(
                "codecType" to stream["codec_type"].asText(""),
                "width" to (stream["width"]?.asText("") ?: ""),
                "height" to (stream["height"]?.asText("") ?: ""),
                "sampleRate" to (stream["sample_rate"]?.asText("") ?: ""),
                "channels" to (stream["channels"]?.asText("") ?: ""),
                "channelLayout" to (stream["channel_layout"]?.asText("") ?: ""),
            )
        }

    private fun criticalMetadataPreserved(originalProbe: JsonNode, candidateProbe: JsonNode): Boolean {
        val ignoredKeys = setOf("encoder", "duration")
        val originalFormatTags = tags(originalProbe["format"]?.get("tags")).filter { (key, value) -> keepMetadataTag(key, value, ignoredKeys) }
        val candidateFormatTags = tags(candidateProbe["format"]?.get("tags"))
        if (!originalFormatTags.all { (key, value) -> candidateFormatTags[key] == value }) return false

        val originalStreams = originalProbe["streams"]
        val candidateStreams = candidateProbe["streams"]
        if (originalStreams.size() != candidateStreams.size()) return false
        return originalStreams.zip(candidateStreams).all { (original, candidate) ->
            val streamIgnoredKeys = ignoredKeys + "creation_time"
            val originalTags = tags(original["tags"]).filter { (key, value) -> keepMetadataTag(key, value, streamIgnoredKeys) }
            val candidateTags = tags(candidate["tags"])
            originalTags.all { (key, value) -> candidateTags[key] == value }
        }
    }

    private fun keepMetadataTag(key: String, value: String, ignoredKeys: Set<String>): Boolean {
        if (key in ignoredKeys) return false
        if (key == "language" && value == "und") return false
        return true
    }

    private fun tags(node: JsonNode?): Map<String, String> =
        node?.fields()?.asSequence()?.associate { it.key.lowercase() to it.value.asText() } ?: emptyMap()

    private fun decodesWithoutErrors(path: Path): Boolean {
        val process = ProcessBuilder(
            mediaToolPathService.ffmpegPath(),
            "-v", "error",
            "-i", path.toString(),
            "-map", "0:v?",
            "-map", "0:a?",
            "-f", "null",
            "-",
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() == 0
    }
}

@Service
class MediaToolPathService(
    private val settingsService: SettingsService,
    private val appPathService: AppPathService,
) {
    fun ffmpegPath(): String = resolve(settingsService.current().mediaFfmpegPath, "ffmpeg")
    fun ffprobePath(): String = resolve(settingsService.current().mediaFfprobePath, "ffprobe")

    private fun resolve(configuredPath: String, executable: String): String {
        val configured = configuredPath.trim()
        if (configured.isNotBlank()) return resolveConfigured(configured)
        return bundledCandidates(executable).firstOrNull { Files.exists(it) }?.toString() ?: executable
    }

    private fun resolveConfigured(value: String): String {
        val path = Paths.get(value)
        return if (path.isAbsolute || value.contains("/") || value.contains("\\")) {
            appPathService.resolve(value).toString()
        } else {
            value
        }
    }

    private fun bundledCandidates(executable: String): List<Path> {
        val os = System.getProperty("os.name").lowercase()
        val binary = if (os.contains("windows")) "$executable.exe" else executable
        val platforms = when {
            os.contains("windows") -> listOf("win32_x64")
            os.contains("mac") -> listOf("darwin_arm64", "darwin_x64")
            else -> listOf("linux_x64")
        }
        return listOf(
            "runtime/tools/tdarr/updater/Tdarr_Node/assets/app/ffmpeg",
            "runtime/tools/tdarr/updater/Tdarr_Server/assets/app/ffmpeg",
        ).flatMap { root ->
            platforms.map { platform -> appPathService.resolve("$root/$platform/$binary") }
        }
    }
}
