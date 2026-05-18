package com.archivesentinel.domain

import org.springframework.data.jpa.repository.JpaRepository

interface AppSettingsRepository : JpaRepository<AppSettings, Long>
