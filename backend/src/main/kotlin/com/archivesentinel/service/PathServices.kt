package com.archivesentinel.service

import com.archivesentinel.api.NativePickerRequest
import com.archivesentinel.api.NativePickerResponse
import com.archivesentinel.api.PathValidationResponse
import com.archivesentinel.api.ServerBrowserEntry
import com.archivesentinel.api.ServerBrowserRequest
import com.archivesentinel.api.ServerBrowserResponse
import org.springframework.stereotype.Service
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.streams.toList

@Service
class AppPathService {
    private val basePath = findWorkspaceRoot()

    fun resolve(rawPath: String): Path {
        val path = Paths.get(rawPath)
        return if (path.isAbsolute) path.normalize() else basePath.resolve(path).normalize()
    }

    private fun findWorkspaceRoot(): Path {
        var current: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (current != null) {
            if (current.resolve("backend").exists() && current.resolve("frontend").exists()) return current
            current = current.parent
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
    }
}

@Service
class PathService(private val appPathService: AppPathService) {
    fun validate(rawPath: String): PathValidationResponse {
        val path = appPathService.resolve(rawPath)
        return PathValidationResponse(
            path = path.toString(),
            exists = path.exists(),
            readable = path.exists() && Files.isReadable(path),
            writable = path.exists() && Files.isWritable(path),
            directory = path.exists() && path.isDirectory(),
        )
    }

    fun reveal(rawPath: String): Boolean {
        val path = appPathService.resolve(rawPath)
        return try {
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                ProcessBuilder("explorer.exe", "/select,", path.toString()).start()
            } else {
                val target = if (path.exists()) path.parent ?: path else path.parent ?: path
                ProcessBuilder("xdg-open", target.toString()).start()
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

@Service
class ServerFileBrowserService(private val appPathService: AppPathService) {
    fun browse(request: ServerBrowserRequest): ServerBrowserResponse {
        val kind = request.kind.lowercase()
        val start = startPath(request.path, kind)
        val directory = when {
            Files.isDirectory(start) -> start
            start.parent != null -> start.parent
            else -> homePath()
        }.toAbsolutePath().normalize()
        val roots = FileSystems.getDefault().rootDirectories.map { it.toAbsolutePath().normalize().toString() }.toList()
        val entries = runCatching {
            Files.list(directory).use { stream ->
                stream
                    .filter { request.showHidden || !isHidden(it) }
                    .filter { kind == "file" || Files.isDirectory(it) }
                    .map { entry -> entry.toBrowserEntry() }
                    .toList()
                    .sortedWith(compareBy<ServerBrowserEntry> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
        }.getOrElse { emptyList() }
        val message = if (Files.isReadable(directory)) null else "Directory is not readable."
        return ServerBrowserResponse(
            currentPath = directory.toString(),
            parentPath = directory.parent?.toString(),
            entries = entries,
            roots = roots,
            separator = directory.fileSystem.separator,
            message = message,
        )
    }

    private fun startPath(rawPath: String?, kind: String): Path {
        if (rawPath.isNullOrBlank()) return homePath()
        val resolved = runCatching { appPathService.resolve(rawPath) }.getOrElse { homePath() }
        return if (kind == "file" && !Files.isDirectory(resolved) && resolved.parent != null) resolved.parent else resolved
    }

    private fun homePath(): Path = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize()

    private fun Path.toBrowserEntry(): ServerBrowserEntry =
        ServerBrowserEntry(
            name = fileName?.toString() ?: toString(),
            path = toAbsolutePath().normalize().toString(),
            directory = Files.isDirectory(this),
            readable = Files.isReadable(this),
            writable = Files.isWritable(this),
            hidden = isHidden(this),
            sizeBytes = if (Files.isRegularFile(this)) runCatching { Files.size(this) }.getOrNull() else null,
        )

    private fun isHidden(path: Path): Boolean =
        runCatching { Files.isHidden(path) }.getOrDefault(path.fileName?.toString()?.startsWith(".") == true)
}

@Service
class NativePickerService {
    fun pickFolders(request: NativePickerRequest): NativePickerResponse = runPicker(folderCommand(request))
    fun pickFiles(request: NativePickerRequest): NativePickerResponse = runPicker(fileCommand(request))

    private fun runPicker(command: List<String>): NativePickerResponse =
        try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            if (exit == 0) {
                NativePickerResponse(output.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct(), false)
            } else {
                NativePickerResponse(emptyList(), true, output.ifBlank { "Picker was cancelled or could not be opened." })
            }
        } catch (ex: Exception) {
            NativePickerResponse(emptyList(), true, "Native picker unavailable: ${ex.message}")
        }

    private fun folderCommand(request: NativePickerRequest): List<String> =
        when (System.getProperty("os.name").lowercase()) {
            else -> if (isWindows()) windowsPickerScript(true, request) else linuxPickerCommand(true, request)
        }

    private fun fileCommand(request: NativePickerRequest): List<String> =
        if (isWindows()) windowsPickerScript(false, request) else linuxPickerCommand(false, request)

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("windows")

    private fun windowsPickerScript(folder: Boolean, request: NativePickerRequest): List<String> {
        val escapedInitial = request.initialPath?.replace("'", "''")
        val initial = if (escapedInitial.isNullOrBlank()) "" else "\$dialog.InitialDirectory = '$escapedInitial';"
        val multiselect = if (request.multiple) "\$true" else "\$false"
        val selection = if (folder) "\$dialog.FileNames | ForEach-Object { Split-Path \$_ -Parent }" else "\$dialog.FileNames"
        val folderMode = if (folder) "\$dialog.CheckFileExists = \$false; \$dialog.ValidateNames = \$false; \$dialog.FileName = 'Select folder';" else ""
        val script = """
            Add-Type -AssemblyName System.Windows.Forms;
            ${'$'}dialog = New-Object System.Windows.Forms.OpenFileDialog;
            ${'$'}dialog.Multiselect = $multiselect;
            $folderMode
            $initial
            if (${'$'}dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { $selection } else { exit 2 }
        """.trimIndent()
        return listOf("powershell", "-NoProfile", "-STA", "-Command", script)
    }

    private fun linuxPickerCommand(folder: Boolean, request: NativePickerRequest): List<String> {
        val command = mutableListOf("zenity", "--file-selection", "--multiple", "--separator=\n")
        if (folder) command += "--directory"
        request.initialPath?.takeIf { it.isNotBlank() }?.let { command += listOf("--filename=$it") }
        return command
    }
}
