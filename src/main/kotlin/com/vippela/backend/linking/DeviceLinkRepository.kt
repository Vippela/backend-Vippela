package com.vippela.backend.linking

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface DeviceLinkRepository : JpaRepository<DeviceLink, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTokenAndStatus(token: String, status: String): DeviceLink?
    fun existsByToken(token: String): Boolean
    fun findAllByOwnerKeyHashAndStatus(ownerKeyHash: String, status: String): List<DeviceLink>
    fun findAllByLinkedDeviceIdAndStatus(linkedDeviceId: String, status: String): List<DeviceLink>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from DeviceLink l where l.id = :id")
    fun locked(id: UUID): DeviceLink?
}
