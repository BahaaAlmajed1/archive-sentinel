package com.archivesentinel.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "app_settings")
class AppSettings(
    @Id var id: Long = 1L,
    @Column(name = "setup_completed") var setupCompleted: Boolean = false,
    @Enumerated(EnumType.STRING) @Column(name = "execution_mode") var executionMode: ExecutionMode = ExecutionMode.HOST_NATIVE,
    @Enumerated(EnumType.STRING) @Column(name = "tdarr_mode") var tdarrMode: TdarrMode = TdarrMode.EXISTING,
    @Column(name = "tdarr_base_url") var tdarrBaseUrl: String = "http://localhost:8266",
    @Column(name = "managed_tdarr_enabled") var managedTdarrEnabled: Boolean = false,
    @Column(name = "archive_root") var archiveRoot: String = "runtime/archive",
    @Column(name = "optimized_root") var optimizedRoot: String = "runtime/optimized",
    @Column(name = "staging_enabled") var stagingEnabled: Boolean = true,
    @Column(name = "staging_root") var stagingRoot: String? = "runtime/staging",
    @Column(name = "default_retention_days") var defaultRetentionDays: Int = 90,
    @Enumerated(EnumType.STRING) @Column(name = "default_deletion_mode") var defaultDeletionMode: DeletionMode = DeletionMode.MANUAL,
    @Enumerated(EnumType.STRING) @Column(name = "default_output_mode") var defaultOutputMode: OutputMode = OutputMode.PARALLEL,
    @Column(name = "precheck_extension_options", columnDefinition = "text") var precheckExtensionOptions: String = "mkv,mp4,mov,avi,m4v,webm,mpg,mpeg,wmv,m2ts,ts",
    @Column(name = "enabled_extensions", columnDefinition = "text") var enabledExtensions: String = "mkv,mp4,mov,avi,m4v,webm,mpg,mpeg,wmv,m2ts,ts",
    @Column(name = "estimated_output_ratio_percent") var estimatedOutputRatioPercent: Int = 65,
    @Column(name = "output_container_extension") var outputContainerExtension: String = "mkv",
    @Column(name = "duration_tolerance_seconds") var durationToleranceSeconds: Double = 0.5,
    @Column(name = "tdarr_codecs_to_exclude", columnDefinition = "text") var tdarrCodecsToExclude: String = "hevc",
    @Column(name = "tdarr_transcode_arguments", columnDefinition = "text") var tdarrTranscodeArguments: String = ",-map 0 -map_metadata 0 -map_chapters 0 -c:v hevc_nvenc -preset p5 -cq 28 -c:a copy -c:s copy",
    @Column(name = "media_ffmpeg_path") var mediaFfmpegPath: String = "",
    @Column(name = "media_ffprobe_path") var mediaFfprobePath: String = "",
    @Column(name = "report_interval_hours") var reportIntervalHours: Int = 24,
    @Column(name = "report_recipients") var reportRecipients: String = "",
    @Column(name = "smtp_host") var smtpHost: String = "",
    @Column(name = "smtp_port") var smtpPort: Int = 25,
    @Column(name = "smtp_username") var smtpUsername: String = "",
    @Column(name = "smtp_password") var smtpPassword: String = "",
    @Column(name = "smtp_auth") var smtpAuth: Boolean = false,
    @Column(name = "smtp_start_tls") var smtpStartTls: Boolean = false,
    @Column(name = "smtp_from") var smtpFrom: String = "archive-sentinel@localhost",
    @Column(name = "scan_threads") var scanThreads: Int = 4,
    @Column(name = "analysis_threads") var analysisThreads: Int = 2,
    @Column(name = "validation_threads") var validationThreads: Int = 2,
    @Column(name = "file_ops_threads") var fileOpsThreads: Int = 2,
    @Column(name = "tdarr_submission_concurrency") var tdarrSubmissionConcurrency: Int = 10,
    @Column(name = "background_scan_refresh_minutes") var backgroundScanRefreshMinutes: Int = 15,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)
