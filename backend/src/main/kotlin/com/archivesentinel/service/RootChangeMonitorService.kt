package com.archivesentinel.service

import com.archivesentinel.domain.StorageRoot
import com.archivesentinel.domain.StorageRootRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.io.path.isDirectory

@Service
class RootChangeMonitorService(private val storageRootRepository: StorageRootRepository) {
    private val watcher: WatchService = FileSystems.getDefault().newWatchService()
    private val executor = Executors.newSingleThreadExecutor()
    private val keyRoots = ConcurrentHashMap<WatchKey, UUID>()
    private val watchedDirs = ConcurrentHashMap<UUID, MutableSet<Path>>()
    private val dirtyRoots = ConcurrentHashMap.newKeySet<UUID>()

    @PostConstruct
    fun start() {
        storageRootRepository.findAll()
            .filter { it.lastScannedAt != null }
            .forEach { dirtyRoots += it.id }
        executor.submit {
            while (!Thread.currentThread().isInterrupted) {
                val key = runCatching { watcher.take() }.getOrNull() ?: break
                val rootId = keyRoots[key]
                if (rootId != null && key.pollEvents().isNotEmpty()) dirtyRoots += rootId
                key.reset()
            }
        }
    }

    fun register(root: StorageRoot, directories: Collection<Path>) {
        val watched = watchedDirs.computeIfAbsent(root.id) { ConcurrentHashMap.newKeySet() }
        directories.filter { Files.exists(it) && it.isDirectory() }.forEach { directory ->
            if (watched.add(directory)) {
                val key = directory.register(
                    watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                )
                keyRoots[key] = root.id
            }
        }
        dirtyRoots.remove(root.id)
    }

    fun markDirty(rootId: UUID) {
        dirtyRoots += rootId
    }

    fun markClean(rootId: UUID) {
        dirtyRoots.remove(rootId)
    }

    fun isDirty(rootId: UUID): Boolean = rootId in dirtyRoots

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
        watcher.close()
    }
}
