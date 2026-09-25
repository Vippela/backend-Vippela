package com.vippela.backend.linking

import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class DeviceLinkService(private val repo: DeviceLinkRepository) {

    fun generateLinkCode(ownerUserId: UUID): DeviceLink {
        val link = DeviceLink(
            ownerUserId = ownerUserId,
            token = generateSecureCode(),
            expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES)
        )
        return repo.save(link)
    }

    private fun generateSecureCode(): String {
        val random = SecureRandom()
        return (100000 + random.nextInt(900000)).toString()
    }

    fun confirmLink(token: String, linkedDeviceId: String): DeviceLink {
        val link = repo.findByTokenAndStatus(token, "pending")
            ?: throw IllegalArgumentException("Código inválido")

        if (link.expiresAt.isBefore(Instant.now())) {
            throw IllegalStateException("Código expirado")
        }

        link.linkedDeviceId = linkedDeviceId
        link.status = "active"
        return repo.save(link)
    }
}