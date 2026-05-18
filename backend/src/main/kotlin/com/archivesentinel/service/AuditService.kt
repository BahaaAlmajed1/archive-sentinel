package com.archivesentinel.service

import com.archivesentinel.domain.AuditEvent
import com.archivesentinel.domain.AuditEventRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuditService(private val auditEventRepository: AuditEventRepository) {
    fun record(fileId: UUID?, eventType: String, message: String) =
        auditEventRepository.save(AuditEvent(mediaFileId = fileId, eventType = eventType, message = message))
}
