package com.vippela.backend.linking

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

 data class GenerateLinkRequest(val memberKey: String, val memberName: String, val ownerName: String)
 data class LinkCodeResponse(val id: String, val token: String, val expiresAt: String, val memberKey: String)
 data class ConfirmLinkRequest(val token: String, val deviceId: String)
 data class AppInfo(val packageName: String, val label: String)
 data class RuleRequest(val packageName: String, val blocked: Boolean)
 data class SyncRequest(val apps: List<AppInfo>, val appliedRevision: Long, val protectionEnabled: Boolean)
 data class LinkView(val id: String, val memberKey: String, val memberName: String, val ownerName: String,
    val linkedDeviceId: String?, val status: String, val revision: Long, val appliedRevision: Long,
    val protectionEnabled: Boolean, val lastSeenAt: String?, val apps: List<AppInfo>, val blockedPackages: Set<String>)

@RestController
@RequestMapping("/links")
class DeviceLinkController(private val service: DeviceLinkService) {
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun duplicateBinding() = mapOf("error" to "O aparelho ou familiar já possui um vínculo")
    private val attempts = mutableMapOf<String, Pair<Long, Int>>()
    @Synchronized private fun limit(ip: String) {
        val now = System.currentTimeMillis()
        attempts.entries.removeIf { now - it.value.first > 60_000 }
        val previous = attempts[ip] ?: (now to 0)
        if (previous.second >= 10 || attempts.size >= 10000) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde um minuto")
        attempts[ip] = previous.first to previous.second + 1
    }
    @PostMapping("/generate")
    fun generate(@RequestHeader("X-Owner-Key") key: String, @RequestBody request: GenerateLinkRequest, http: HttpServletRequest): LinkCodeResponse {
        limit(http.remoteAddr)
        return service.generate(key, request)
    }
    @PostMapping("/confirm")
    fun confirm(@RequestHeader("X-Device-Key") key: String, @RequestBody request: ConfirmLinkRequest, http: HttpServletRequest): LinkView {
        limit(http.remoteAddr)
        return service.confirm(key, request)
    }
    @GetMapping("/{id}/report")
    fun report(@PathVariable id: UUID, @RequestHeader("X-Owner-Key") key: String) = service.report(id, key)
    @PutMapping("/{id}/report")
    fun report(@PathVariable id: UUID, @RequestHeader("X-Device-Key") key: String,
        @RequestBody request: UsageReport) = service.saveReport(id, key, request)
    @GetMapping fun list(@RequestHeader("X-Owner-Key") key: String) = service.list(key)
    @GetMapping("/{id}") fun status(@PathVariable id: UUID, @RequestHeader("X-Owner-Key") key: String) = service.ownerView(id, key)
    @PutMapping("/{id}/rule") fun update(@PathVariable id: UUID, @RequestHeader("X-Owner-Key") key: String, @RequestBody request: RuleRequest) = service.update(id, key, request)
    @PostMapping("/{id}/sync") fun sync(@PathVariable id: UUID, @RequestHeader("X-Device-Key") key: String, @RequestBody request: SyncRequest) = service.sync(id, key, request)
}

 data class UsageReport(
    val permission: Boolean = false,
    val collectedAt: Long = 0,
    val since: Long = 0,
    val zone: String = "UTC",
    val buckets: Map<String, Long> = emptyMap(),
    val icons: Map<String, String> = emptyMap(),
 )
