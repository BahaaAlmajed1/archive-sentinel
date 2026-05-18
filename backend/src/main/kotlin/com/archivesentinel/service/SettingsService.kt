package com.archivesentinel.service

import com.archivesentinel.api.SetupRequest
import com.archivesentinel.domain.AppSettings
import com.archivesentinel.domain.AppSettingsRepository
import com.archivesentinel.domain.ExecutionMode
import com.archivesentinel.domain.TdarrMode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SettingsService(
    private val appSettingsRepository: AppSettingsRepository,
    @Value("\${app.defaults.execution-mode}") private val defaultExecutionMode: String,
    @Value("\${app.defaults.tdarr-mode}") private val defaultTdarrMode: String,
    @Value("\${app.defaults.tdarr-base-url}") private val defaultTdarrBaseUrl: String,
    @Value("\${app.defaults.managed-tdarr-enabled}") private val defaultManagedTdarrEnabled: Boolean,
    @Value("\${app.defaults.archive-root}") private val defaultArchiveRoot: String,
    @Value("\${app.defaults.optimized-root}") private val defaultOptimizedRoot: String,
    @Value("\${app.defaults.staging-root}") private val defaultStagingRoot: String,
    @Value("\${app.defaults.precheck-extension-options}") private val defaultPrecheckExtensionOptions: String,
    @Value("\${app.defaults.enabled-extensions}") private val defaultEnabledExtensions: String,
    @Value("\${app.defaults.estimated-output-ratio-percent}") private val defaultEstimatedOutputRatioPercent: Int,
    @Value("\${app.defaults.output-container-extension}") private val defaultOutputContainerExtension: String,
    @Value("\${app.defaults.duration-tolerance-seconds}") private val defaultDurationToleranceSeconds: Double,
    @Value("\${app.defaults.tdarr-codecs-to-exclude}") private val defaultTdarrCodecsToExclude: String,
    @Value("\${app.defaults.tdarr-transcode-arguments}") private val defaultTdarrTranscodeArguments: String,
    @Value("\${app.defaults.media-ffmpeg-path}") private val defaultMediaFfmpegPath: String,
    @Value("\${app.defaults.media-ffprobe-path}") private val defaultMediaFfprobePath: String,
) {
    fun current(): AppSettings = appSettingsRepository.findById(1L).orElseGet { appSettingsRepository.save(defaultSettings()) }

    private fun defaultSettings() = AppSettings(
        executionMode = runCatching { ExecutionMode.valueOf(defaultExecutionMode) }.getOrDefault(ExecutionMode.HOST_NATIVE),
        tdarrMode = runCatching { TdarrMode.valueOf(defaultTdarrMode) }.getOrDefault(TdarrMode.EXISTING),
        tdarrBaseUrl = defaultTdarrBaseUrl,
        managedTdarrEnabled = defaultManagedTdarrEnabled,
        archiveRoot = defaultArchiveRoot,
        optimizedRoot = defaultOptimizedRoot,
        stagingRoot = defaultStagingRoot,
        precheckExtensionOptions = normalizeExtensions(defaultPrecheckExtensionOptions) ?: "",
        enabledExtensions = normalizeExtensions(defaultEnabledExtensions) ?: "",
        estimatedOutputRatioPercent = defaultEstimatedOutputRatioPercent,
        outputContainerExtension = normalizeExtension(defaultOutputContainerExtension),
        durationToleranceSeconds = defaultDurationToleranceSeconds,
        tdarrCodecsToExclude = normalizeExtensions(defaultTdarrCodecsToExclude) ?: "",
        tdarrTranscodeArguments = defaultTdarrTranscodeArguments,
        mediaFfmpegPath = defaultMediaFfmpegPath,
        mediaFfprobePath = defaultMediaFfprobePath,
    )

    @Transactional
    fun update(request: SetupRequest): AppSettings {
        val settings = current()
        settings.setupCompleted = true
        settings.executionMode = request.executionMode
        settings.tdarrMode = request.tdarrMode
        settings.tdarrBaseUrl = request.tdarrBaseUrl.trimEnd('/')
        settings.managedTdarrEnabled = request.managedTdarrEnabled
        settings.archiveRoot = request.archiveRoot
        settings.optimizedRoot = request.optimizedRoot
        settings.stagingEnabled = request.stagingEnabled
        settings.stagingRoot = request.stagingRoot
        settings.defaultRetentionDays = request.defaultRetentionDays
        settings.defaultDeletionMode = request.defaultDeletionMode
        settings.defaultOutputMode = request.defaultOutputMode
        settings.precheckExtensionOptions = normalizeExtensions(request.precheckExtensionOptions.joinToString(",")) ?: ""
        settings.enabledExtensions = normalizeExtensions(request.enabledExtensions.joinToString(",")) ?: ""
        settings.estimatedOutputRatioPercent = request.estimatedOutputRatioPercent
        settings.outputContainerExtension = normalizeExtension(request.outputContainerExtension)
        settings.durationToleranceSeconds = request.durationToleranceSeconds
        settings.tdarrCodecsToExclude = normalizeExtensions(request.tdarrCodecsToExclude) ?: ""
        settings.tdarrTranscodeArguments = request.tdarrTranscodeArguments
        settings.mediaFfmpegPath = request.mediaFfmpegPath
        settings.mediaFfprobePath = request.mediaFfprobePath
        settings.reportIntervalHours = request.reportIntervalHours
        settings.reportRecipients = request.reportRecipients
        settings.smtpHost = request.smtpHost
        settings.smtpPort = request.smtpPort
        settings.smtpUsername = request.smtpUsername
        settings.smtpPassword = request.smtpPassword
        settings.smtpAuth = request.smtpAuth
        settings.smtpStartTls = request.smtpStartTls
        settings.smtpFrom = request.smtpFrom.ifBlank { "archive-sentinel@localhost" }
        val maxThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        settings.scanThreads = request.scanThreads.coerceIn(1, maxThreads)
        settings.analysisThreads = request.analysisThreads.coerceIn(1, maxThreads)
        settings.validationThreads = request.validationThreads.coerceIn(1, maxThreads)
        settings.fileOpsThreads = request.fileOpsThreads.coerceIn(1, maxThreads)
        settings.tdarrSubmissionConcurrency = request.tdarrSubmissionConcurrency
        settings.backgroundScanRefreshMinutes = request.backgroundScanRefreshMinutes
        settings.updatedAt = Instant.now()
        return appSettingsRepository.save(settings)
    }
}

fun normalizeExtension(value: String): String = value.trim().removePrefix(".").lowercase()
