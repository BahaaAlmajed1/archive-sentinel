package com.archivesentinel.service

import com.archivesentinel.api.NativePickerRequest
import com.archivesentinel.api.NativePickerResponse
import com.archivesentinel.api.PathValidationResponse
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

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
