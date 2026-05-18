package com.archivesentinel.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "admin_users")
class AdminUser(
    @Id var id: UUID = UUID.randomUUID(),
    var username: String = "admin",
    @Column(name = "password_hash") var passwordHash: String = "",
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "audit_events")
class AuditEvent(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "media_file_id") var mediaFileId: UUID? = null,
    @Column(name = "event_type") var eventType: String = "",
    @Column(columnDefinition = "text") var message: String = "",
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
)
