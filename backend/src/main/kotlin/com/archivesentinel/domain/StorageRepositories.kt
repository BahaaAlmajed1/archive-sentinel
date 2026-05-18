package com.archivesentinel.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StorageRootRepository : JpaRepository<StorageRoot, UUID> {
    fun findByPath(path: String): StorageRoot?
    fun findByLabel(label: String): StorageRoot?
}

interface PolicyTargetRepository : JpaRepository<PolicyTarget, UUID> {
    fun findByPath(path: String): PolicyTarget?
}

interface AutomationRuleRepository : JpaRepository<AutomationRule, UUID> {
    fun findByStorageRootId(storageRootId: UUID): AutomationRule?
    fun findAllByEnabled(enabled: Boolean): List<AutomationRule>
    fun deleteByStorageRootId(storageRootId: UUID)
}
