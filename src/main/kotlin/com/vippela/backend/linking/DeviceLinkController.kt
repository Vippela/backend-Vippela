package com.vippela.backend.linking

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class LinkCodeResponse(val token: String, val expiresAt: String)
data class ConfirmLinkRequest(val token: String, val deviceId: String)

@RestController
@RequestMapping("/links")
class DeviceLinkController(private val service: DeviceLinkService) {

    // TODO: trocar por um UUID vindo da autenticação real, quando existir
    private val testOwnerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @PostMapping("/generate")
    fun generate(): ResponseEntity<LinkCodeResponse> {
        val link = service.generateLinkCode(testOwnerId)
        return ResponseEntity.ok(LinkCodeResponse(link.token, link.expiresAt.toString()))
    }

    @PostMapping("/confirm")
    fun confirm(@RequestBody request: ConfirmLinkRequest): ResponseEntity<Any> {
        return try {
            val link = service.confirmLink(request.token, request.deviceId)
            ResponseEntity.ok(link)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.GONE).body(mapOf("error" to e.message))
        }
    }
}