package com.vippela.backend

import com.vippela.backend.linking.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceLinkIntegrationTests {
    @Autowired lateinit var service: DeviceLinkService
    @Autowired lateinit var repo: DeviceLinkRepository
    @LocalServerPort var port = 0
    private val owner = "A".repeat(43)
    private val other = "B".repeat(43)
    private val child = "C".repeat(43)
    private fun device(key: String) = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    private val device = device(child)
    private val app = AppInfo("com.google.android.youtube", "YouTube")
    @BeforeEach fun clear() { repo.deleteAll() }
    private fun pair(member: String = "1", ownerKey: String = owner, childKey: String = child, deviceId: String = device(childKey)): LinkView {
        val code = service.generate(ownerKey, GenerateLinkRequest(member, "Ana", "Cleber"))
        return service.confirm(childKey, ConfirmLinkRequest(code.token, deviceId))
    }
    @Test fun rulesTravelOnlyToTheLinkedChildAndAreAcknowledgedAfterReceipt() {
        val first = pair()
        val second = pair("2", owner, "D".repeat(43), device("D".repeat(43)))
        val id = UUID.fromString(first.id)
        service.sync(id, child, SyncRequest(listOf(app), -1, true))
        val changed = service.update(id, owner, RuleRequest(app.packageName, true))
        assertEquals(1L, changed.revision)
        assertEquals(-1L, changed.appliedRevision)
        val received = service.sync(id, child, SyncRequest(listOf(app), 0, true))
        assertTrue(received.blockedPackages.contains(app.packageName))
        assertEquals(0L, received.appliedRevision)
        service.sync(id, child, SyncRequest(listOf(app), received.revision, true))
        assertEquals(1L, service.ownerView(id, owner).appliedRevision)
        assertTrue(service.ownerView(UUID.fromString(second.id), owner).blockedPackages.isEmpty())
        service.update(id, owner, RuleRequest(app.packageName, false))
        assertTrue(service.sync(id, child, SyncRequest(listOf(app), 1, true)).blockedPackages.isEmpty())
    }
    @Test fun anotherOwnerOrChildCannotReadOrChangeTheLink() {
        val id = UUID.fromString(pair().id)
        assertTrue(service.list(other).isEmpty())
        assertEquals(403, assertThrows(ResponseStatusException::class.java) { service.ownerView(id, other) }.statusCode.value())
        assertEquals(403, assertThrows(ResponseStatusException::class.java) { service.update(id, other, RuleRequest(app.packageName, true)) }.statusCode.value())
        assertEquals(403, assertThrows(ResponseStatusException::class.java) { service.sync(id, other, SyncRequest(listOf(app), -1, true)) }.statusCode.value())
        assertEquals(403, assertThrows(ResponseStatusException::class.java) { service.update(id, child, RuleRequest(app.packageName, true)) }.statusCode.value())
    }
    @Test fun aCodeCannotBeClaimedByTwoDevices() {
        val code = service.generate(owner, GenerateLinkRequest("1", "Ana", "Cleber"))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(listOf(child, other).mapIndexed { i, key -> Callable {
                runCatching { service.confirm(key, ConfirmLinkRequest(code.token, device(key))) }.isSuccess
            }}).map { it.get() }
            assertEquals(1, results.count { it })
            assertEquals(1, service.list(owner).size)
        } finally { executor.shutdownNow() }
    }
    @Test fun confirmationRetryIsIdempotentAndCodesExpire() {
        val code = service.generate(owner, GenerateLinkRequest("1", "Ana", "Cleber"))
        val request = ConfirmLinkRequest(code.token, device)
        assertEquals(service.confirm(child, request).id, service.confirm(child, request).id)
        repo.save(DeviceLink(ownerUserId = UUID.randomUUID(), token = "123456", expiresAt = Instant.now().minusSeconds(1)))
        assertEquals(410, assertThrows(ResponseStatusException::class.java) { service.confirm(child, ConfirmLinkRequest("123456", device)) }.statusCode.value())
    }
    @Test fun unknownAppsAreRejectedAndDisabledProtectionIsReported() {
        val id = UUID.fromString(pair().id)
        assertEquals(400, assertThrows(ResponseStatusException::class.java) { service.update(id, owner, RuleRequest("com.android.settings", true)) }.statusCode.value())
        service.sync(id, child, SyncRequest(listOf(app), 0, false))
        assertFalse(service.ownerView(id, owner).protectionEnabled)
    }
    @Test fun httpEndpointRequiresCredentialHeaderAndDoesNotExposeSecrets() {
        pair()
        val client = HttpClient.newHttpClient()
        val uri = URI("http://localhost:$port/links")
        val missing = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(400, missing.statusCode())
        val response = client.send(HttpRequest.newBuilder(uri).header("X-Owner-Key", owner).GET().build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        assertFalse(response.body().contains("KeyHash"))
        assertFalse(response.body().contains("token"))
        assertFalse(response.body().contains(owner))
    }
}
