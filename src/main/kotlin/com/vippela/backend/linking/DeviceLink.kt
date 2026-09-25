package com.vippela.backend.linking

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "device_links")
data class DeviceLink(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    val ownerUserId: UUID,
    val token: String,
    var linkedDeviceId: String? = null,
    var status: String = "pending",
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now()
)