package com.archivesentinel.service

import com.archivesentinel.domain.DeletionMode
import com.archivesentinel.domain.PolicyTarget
import com.archivesentinel.domain.PolicyTargetRepository
import com.archivesentinel.domain.StorageRoot
import com.archivesentinel.domain.StorageRootRepository
import com.archivesentinel.domain.TargetType
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.exists

@Component
class ExampleDataService(
    private val storageRootRepository: StorageRootRepository,
    private val policyTargetRepository: PolicyTargetRepository,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val root = resolveExampleRoot() ?: return
        val rootPath = "runtime/examples/originals"
        val existingRoot = storageRootRepository.findByLabel("Examples")
        if (existingRoot == null) {
            storageRootRepository.save(StorageRoot(label = "Examples", path = rootPath))
        } else if (existingRoot.path != rootPath) {
            existingRoot.path = rootPath
            existingRoot.updatedAt = Instant.now()
            storageRootRepository.save(existingRoot)
        }

        ensurePolicy("runtime/examples/originals/camera-masters", root.resolve("camera-masters"), excluded = true)
        ensurePolicy("runtime/examples/originals/review-proxies", root.resolve("review-proxies"), retentionDays = 14, deletionMode = DeletionMode.AUTOMATIC)
        ensurePolicy("runtime/examples/originals/client-deliverables", root.resolve("client-deliverables"), excludedExtensions = "mov,mxf")
        ensureSystemPolicy(systemExclusionPath(), excluded = true)
    }

    private fun ensurePolicy(
        storedPath: String,
        resolvedPath: Path,
        excluded: Boolean = false,
        excludedExtensions: String? = null,
        retentionDays: Int? = null,
        deletionMode: DeletionMode? = null,
    ) {
        val normalized = resolvedPath.toAbsolutePath().normalize().toString()
        val existing = policyTargetRepository.findAll().firstOrNull {
            it.path == storedPath || it.path == normalized || it.path.replace("\\", "/").endsWith(storedPath)
        }
        if (existing != null) {
            if (existing.path != storedPath) {
                existing.path = storedPath
                existing.updatedAt = Instant.now()
                policyTargetRepository.save(existing)
            }
            return
        }
        policyTargetRepository.save(
            PolicyTarget(
                path = storedPath,
                targetType = TargetType.FOLDER,
                excluded = excluded,
                recursive = true,
                excludedExtensions = excludedExtensions,
                retentionDays = retentionDays,
                deletionMode = deletionMode,
            ),
        )
    }

    private fun ensureSystemPolicy(path: Path, excluded: Boolean) {
        val normalized = path.toString()
        val existing = policyTargetRepository.findByPath(normalized)
        if (existing != null) return
        policyTargetRepository.save(
            PolicyTarget(
                path = normalized,
                targetType = TargetType.FOLDER,
                excluded = excluded,
                recursive = true,
            ),
        )
    }

    private fun resolveExampleRoot(): Path? {
        var current: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (current != null) {
            val candidate = current.resolve("runtime").resolve("examples").resolve("originals")
            if (candidate.exists() && Files.isDirectory(candidate)) return candidate
            current = current.parent
        }
        return null
    }

    private fun systemExclusionPath(): Path =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            Paths.get("C:\\System Volume Information")
        } else {
            Paths.get("/proc")
        }
}
