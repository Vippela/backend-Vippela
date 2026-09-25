package com.vippela.backend.linking

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceLinkRepository : JpaRepository<DeviceLink, UUID> {
    fun findByTokenAndStatus(token: String, status: String): DeviceLink?
}