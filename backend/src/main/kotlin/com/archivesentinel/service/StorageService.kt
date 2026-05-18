package com.archivesentinel.service

import com.archivesentinel.api.PolicyTargetRequest
import com.archivesentinel.api.StorageRootRequest
import com.archivesentinel.api.UntrackStorageRootResponse
import com.archivesentinel.domain.AppSettings
import com.archivesentinel.domain.DeletionMode
import com.archivesentinel.domain.OutputMode
import com.archivesentinel.domain.PolicyTarget
import com.archivesentinel.domain.PolicyTargetRepository
import com.archivesentinel.domain.StorageRoot
import com.archivesentinel.domain.StorageRootRepository
import com.archivesentinel.domain.TargetType
import com.archivesentinel.domain.AutomationRule
import com.archivesentinel.domain.AutomationRuleRepository
import com.archivesentinel.domain.MediaFileRepository
import com.archivesentinel.domain.OptimizationRunItemRepository
import com.archivesentinel.domain.PrecheckRunItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.io.path.extension

@Service
class StorageService(
    private val storageRootRepository: StorageRootRepository,
    private val policyTargetRepository: PolicyTargetRepository,
    private val automationRuleRepository: AutomationRuleRepository,
    private val mediaFileRepository: MediaFileRepository,
    private val optimizationRunItemRepository: OptimizationRunItemRepository,
    private val precheckRunItemRepository: PrecheckRunItemRepository,
    private val appPathService: AppPathService,
    private val settingsService: SettingsService,
    private val policyResolutionService: PolicyResolutionService,
    private val rootChangeMonitorService: RootChangeMonitorService,
) {
    fun roots(): List<StorageRoot> = storageRootRepository.findAll()

    fun activePolicies(root: StorageRoot): List<PolicyTarget> {
        val rootPath = appPathService.resolve(root.path)
        return policyTargetRepository.findAll().filter { policy ->
            val policyPath = appPathService.resolve(policy.path)
            policyPath.startsWith(rootPath) || rootPath.startsWith(policyPath)
        }
    }

    fun effectivePolicy(root: StorageRoot): ResolvedPolicy =
        policyResolution(root.path)

    fun addRoot(request: StorageRootRequest): StorageRoot {
        val requestedPath = appPathService.resolve(request.path)
        val existing = storageRootRepository.findAll().firstOrNull { appPathService.resolve(it.path) == requestedPath }
        if (existing != null) {
            existing.label = request.label
            existing.enabled = request.enabled
            existing.optimizedRootOverride = request.optimizedRootOverride?.takeIf { it.isNotBlank() }
            existing.archiveRootOverride = request.archiveRootOverride?.takeIf { it.isNotBlank() }
            existing.autoRescanEnabled = request.autoRescanEnabled
            existing.updatedAt = Instant.now()
            return storageRootRepository.save(existing)
        }
        return storageRootRepository.save(
            StorageRoot(
                label = request.label,
                path = request.path,
                enabled = request.enabled,
                optimizedRootOverride = request.optimizedRootOverride?.takeIf { it.isNotBlank() },
                archiveRootOverride = request.archiveRootOverride?.takeIf { it.isNotBlank() },
                autoRescanEnabled = request.autoRescanEnabled,
            ),
        )
    }

    fun updateRoot(id: UUID, request: StorageRootRequest): StorageRoot {
        val root = storageRootRepository.findById(id).orElseThrow()
        val pathChanged = appPathService.resolve(root.path) != appPathService.resolve(request.path)
        root.label = request.label
        root.path = request.path
        root.enabled = request.enabled
        root.optimizedRootOverride = request.optimizedRootOverride?.takeIf { it.isNotBlank() }
        root.archiveRootOverride = request.archiveRootOverride?.takeIf { it.isNotBlank() }
        root.autoRescanEnabled = request.autoRescanEnabled
        if (pathChanged) {
            root.lastScannedAt = null
            root.latestPrecheckRunId = null
            rootChangeMonitorService.markDirty(root.id)
        }
        root.updatedAt = Instant.now()
        return storageRootRepository.save(root)
    }

    fun deleteRoot(id: UUID) {
        require(mediaFileRepository.countByStorageRootId(id) == 0L) {
            "Storage roots with tracked files cannot be deleted. Disable the root instead."
        }
        automationRuleRepository.deleteByStorageRootId(id)
        storageRootRepository.deleteById(id)
    }

    @Transactional
    fun untrackAndDeleteRoot(id: UUID): UntrackStorageRootResponse {
        val root = storageRootRepository.findById(id).orElseThrow()
        val media = mediaFileRepository.findAllByStorageRootId(id)
        val mediaIds = media.map { it.id }
        val deletedPrecheckItems = if (mediaIds.isEmpty()) 0 else precheckRunItemRepository.deleteByMediaFileIds(mediaIds)
        val clearedRunItemLinks = if (mediaIds.isEmpty()) 0 else optimizationRunItemRepository.clearMediaReferences(mediaIds)
        mediaFileRepository.deleteAllInBatch(media)
        automationRuleRepository.deleteByStorageRootId(id)
        storageRootRepository.delete(root)
        return UntrackStorageRootResponse(
            deletedRootId = id,
            untrackedMediaFiles = media.size,
            deletedPrecheckItems = deletedPrecheckItems,
            clearedRunItemLinks = clearedRunItemLinks,
        )
    }

    fun policies(): List<PolicyTarget> = policyTargetRepository.findAll()

    fun addPolicy(request: PolicyTargetRequest): PolicyTarget =
        policyTargetRepository.save(
            PolicyTarget(
                path = request.path,
                targetType = request.targetType,
                excluded = request.excluded,
                recursive = request.recursive,
                excludedExtensions = normalizeExtensions(request.excludedExtensions),
                retentionDays = request.retentionDays,
                deletionMode = request.deletionMode,
                outputMode = request.outputMode,
            ),
        )

    fun updatePolicy(id: UUID, request: PolicyTargetRequest): PolicyTarget {
        val policy = policyTargetRepository.findById(id).orElseThrow()
        policy.path = request.path
        policy.targetType = request.targetType
        policy.excluded = request.excluded
        policy.recursive = request.recursive
        policy.excludedExtensions = normalizeExtensions(request.excludedExtensions)
        policy.retentionDays = request.retentionDays
        policy.deletionMode = request.deletionMode
        policy.outputMode = request.outputMode
        policy.updatedAt = Instant.now()
        return policyTargetRepository.save(policy)
    }

    fun deletePolicy(id: UUID) = policyTargetRepository.deleteById(id)

    fun automations(): List<AutomationRule> = automationRuleRepository.findAll()

    fun upsertAutomation(storageRootId: UUID, enabled: Boolean, intervalMinutes: Int): AutomationRule {
        require(intervalMinutes > 0) { "Interval must be greater than zero" }
        storageRootRepository.findById(storageRootId).orElseThrow()
        val rule = automationRuleRepository.findByStorageRootId(storageRootId) ?: AutomationRule(storageRootId = storageRootId)
        rule.enabled = enabled
        rule.intervalMinutes = intervalMinutes
        rule.updatedAt = Instant.now()
        return automationRuleRepository.save(rule)
    }

    fun deleteAutomation(id: UUID) = automationRuleRepository.deleteById(id)

    private fun policyResolution(rawPath: String): ResolvedPolicy =
        policyResolutionService.resolve(appPathService.resolve(rawPath), settingsService.current())
}

data class ResolvedPolicy(
    val excluded: Boolean,
    val retentionDays: Int,
    val deletionMode: DeletionMode,
    val outputMode: OutputMode,
    val matchedPolicy: PolicyTarget?,
)

@Service
class PolicyResolutionService(
    private val policyTargetRepository: PolicyTargetRepository,
    private val appPathService: AppPathService,
) {
    fun policies(): List<PolicyTarget> = policyTargetRepository.findAll()

    fun resolve(path: Path, defaults: AppSettings): ResolvedPolicy =
        resolve(path, defaults, policies())

    fun resolve(path: Path, defaults: AppSettings, policies: List<PolicyTarget>): ResolvedPolicy {
        val normalizedPath = path.normalize()
        val matching = policies
            .filter { policy -> matches(policy, normalizedPath) }
            .maxByOrNull { appPathService.resolve(it.path).nameCount }
        val extensionExcluded = matching?.excludedExtensions
            ?.let { parseExtensions(it).contains(path.extension.lowercase()) }
            ?: false

        return ResolvedPolicy(
            excluded = (matching?.excluded ?: false) || extensionExcluded,
            retentionDays = matching?.retentionDays ?: defaults.defaultRetentionDays,
            deletionMode = matching?.deletionMode ?: defaults.defaultDeletionMode,
            outputMode = matching?.outputMode ?: defaults.defaultOutputMode,
            matchedPolicy = matching,
        )
    }

    private fun matches(policy: PolicyTarget, path: Path): Boolean {
        val policyPath = appPathService.resolve(policy.path)
        return when (policy.targetType) {
            TargetType.FILE -> path == policyPath
            TargetType.FOLDER -> if (policy.recursive) path.startsWith(policyPath) else path.parent == policyPath
        }
    }
}

fun normalizeExtensions(value: String?): String? =
    parseExtensions(value).takeIf { it.isNotEmpty() }?.joinToString(",")

fun parseExtensions(value: String?): Set<String> =
    value.orEmpty()
        .split(",", "\n", ";", " ")
        .map { it.trim().removePrefix(".").lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
