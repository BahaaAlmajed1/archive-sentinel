package com.archivesentinel.api

import com.archivesentinel.service.SettingsService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class SettingsController(private val settingsService: SettingsService) {
    @GetMapping("/setup/status")
    fun setupStatus() = mapOf("setupCompleted" to settingsService.current().setupCompleted)

    @GetMapping("/settings")
    fun settings(): SettingsResponse = settingsService.current().toResponse()

    @PutMapping("/settings")
    fun updateSettings(@Valid @RequestBody request: SetupRequest): SettingsResponse = settingsService.update(request).toResponse()
}
