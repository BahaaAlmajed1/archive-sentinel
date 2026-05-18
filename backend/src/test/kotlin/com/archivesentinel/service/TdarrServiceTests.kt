package com.archivesentinel.service

import com.archivesentinel.domain.AppSettings
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TdarrServiceTests {
    @Test
    fun `status reports unreachable without throwing`() {
        val settingsService = Mockito.mock(SettingsService::class.java)
        Mockito.`when`(settingsService.current()).thenReturn(AppSettings(tdarrBaseUrl = "http://localhost:1"))

        val status = TdarrService(settingsService, ObjectMapper(), AppPathService()).status()

        assertFalse(status.reachable)
    }
}
