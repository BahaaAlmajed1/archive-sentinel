package com.archivesentinel.api

import com.archivesentinel.service.NativePickerService
import com.archivesentinel.service.OptimizationService
import com.archivesentinel.service.PathService
import com.archivesentinel.service.TdarrService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class SystemController(
    private val pathService: PathService,
    private val nativePickerService: NativePickerService,
    private val tdarrService: TdarrService,
    private val optimizationService: OptimizationService,
) {
    @GetMapping("/health")
    fun health() = mapOf("status" to "ok")

    @PostMapping("/paths/validate")
    fun validatePath(@RequestBody body: Map<String, String>) = pathService.validate(body["path"] ?: "")

    @PostMapping("/pickers/folders")
    fun pickFolders(@RequestBody request: NativePickerRequest) = nativePickerService.pickFolders(request)

    @PostMapping("/pickers/files")
    fun pickFiles(@RequestBody request: NativePickerRequest) = nativePickerService.pickFiles(request)

    @GetMapping("/tdarr/status")
    fun tdarrStatus() = tdarrService.status()

    @PostMapping("/tdarr/clear-queue")
    fun clearTdarrQueue() = mapOf("clearedPaths" to optimizationService.forceClearTdarrQueue())

    @GetMapping("/paths/reveal")
    fun revealPath(@RequestParam path: String): ResponseEntity<Void> {
        pathService.reveal(path)
        return ResponseEntity.noContent().build()
    }
}
