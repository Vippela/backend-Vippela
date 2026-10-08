package com.vippela.backend.linking

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class DeviceLinkService(private val repo: DeviceLinkRepository) {
    private val random = SecureRandom()
    private fun fail(status: HttpStatus, message: String): Nothing = throw ResponseStatusException(status, message)
    private fun digest(key: String): String {
        if (!key.matches(Regex("[A-Za-z0-9_-]{43,128}"))) fail(HttpStatus.UNAUTHORIZED, "Credencial inválida")
        return MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun authorize(hash: String?, key: String) {
        if (hash == null || !MessageDigest.isEqual(hash.toByteArray(), digest(key).toByteArray()))
            fail(HttpStatus.FORBIDDEN, "Sem acesso a este vínculo")
    }
    private fun link(id: UUID) = repo.locked(id) ?: fail(HttpStatus.NOT_FOUND, "Vínculo não encontrado")
    private fun active(link: DeviceLink) {
        if (link.status != "active") fail(HttpStatus.CONFLICT, "Vínculo não ativo")
    }
    fun generate(key: String, request: GenerateLinkRequest): LinkCodeResponse {
        val hash = digest(key)
        if (request.memberKey.isBlank() || request.memberKey.length > 100 || request.memberName.isBlank() || request.memberName.length > 100 || request.ownerName.length > 100)
            fail(HttpStatus.BAD_REQUEST, "Identificação inválida")
        // Um novo código substitui apenas os códigos pendentes desse familiar.
        repo.findAllByOwnerKeyHashAndStatus(hash, "pending").filter { it.memberKey == request.memberKey }.forEach { it.status = "revoked" }
        var code: String
        do { code = (100000 + random.nextInt(900000)).toString() } while (repo.existsByToken(code))
        val saved = repo.save(DeviceLink(ownerUserId = UUID.nameUUIDFromBytes(hash.toByteArray()), token = code,
            expiresAt = Instant.now().plusSeconds(300), ownerKeyHash = hash,
            memberKey = request.memberKey, memberName = request.memberName, ownerName = request.ownerName))
        return LinkCodeResponse(saved.id.toString(), code, saved.expiresAt.toString(), request.memberKey)
    }
    fun confirm(key: String, request: ConfirmLinkRequest): LinkView {
        val hash = digest(key)
        if (!request.token.matches(Regex("[0-9]{6}")) || request.deviceId != hash)
            fail(HttpStatus.BAD_REQUEST, "Código ou aparelho inválido")
        val target = repo.findByTokenAndStatus(request.token, "pending")
        if (target == null) {
            val previous = repo.findByTokenAndStatus(request.token, "active")
            if (previous != null && previous.deviceKeyHash == hash && previous.linkedDeviceId == request.deviceId) return view(previous)
            fail(HttpStatus.BAD_REQUEST, "Código inválido ou já utilizado")
        }
        if (!target.expiresAt.isAfter(Instant.now())) fail(HttpStatus.GONE, "Código expirado")
        // Uma conta/aparelho recebe regras de um único vínculo ativo.
        if (repo.findAllByLinkedDeviceIdAndStatus(request.deviceId, "active").isNotEmpty())
            fail(HttpStatus.CONFLICT, "Este aparelho já tem um vínculo ativo")
        if (repo.findAllByOwnerKeyHashAndStatus(target.ownerKeyHash!!, "active").any { it.memberKey == target.memberKey })
            fail(HttpStatus.CONFLICT, "Este familiar já tem um aparelho vinculado")
        target.deviceBinding = request.deviceId
        target.memberBinding = target.ownerKeyHash + ":" + target.memberKey
        target.deviceKeyHash = hash
        target.linkedDeviceId = request.deviceId
        target.status = "active"
        return view(repo.saveAndFlush(target))
    }
    fun list(key: String) = repo.findAllByOwnerKeyHashAndStatus(digest(key), "active").map(::view)
    fun ownerView(id: UUID, key: String): LinkView = link(id).let { authorize(it.ownerKeyHash, key); view(it) }
    fun update(id: UUID, key: String, request: RuleRequest): LinkView {
        val target = link(id)
        authorize(target.ownerKeyHash, key)
        active(target)
        if (!target.apps.containsKey(request.packageName)) fail(HttpStatus.BAD_REQUEST, "Aplicativo não informado pelo familiar")
        val changed = if (request.blocked) target.blockedPackages.add(request.packageName) else target.blockedPackages.remove(request.packageName)
        if (changed) target.revision++
        return view(target)
    }
    fun sync(id: UUID, key: String, request: SyncRequest): LinkView {
        val target = link(id)
        authorize(target.deviceKeyHash, key)
        active(target)
        if (request.apps.size > 500 || request.apps.any { !it.packageName.matches(Regex("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")) || it.label.length !in 1..100 })
            fail(HttpStatus.BAD_REQUEST, "Lista de apps inválida")
        target.apps.clear()
        target.apps.putAll(request.apps.associate { it.packageName to it.label })
        target.appliedRevision = maxOf(target.appliedRevision, request.appliedRevision.coerceIn(-1, target.revision))
        target.protectionEnabled = request.protectionEnabled
        target.lastSeenAt = Instant.now()
        return view(target)
    }
    fun report(id: UUID, key: String): UsageReport {
        val target = link(id)
        authorize(target.ownerKeyHash, key)
        return reportView(target)
    }
    fun saveReport(id: UUID, key: String, request: UsageReport): UsageReport {
        val target = link(id)
        authorize(target.deviceKeyHash, key)
        active(target)
        val now = Instant.now().toEpochMilli()
        val oldest = now - 32L * 86400000
        val validZone = runCatching { java.time.ZoneId.of(request.zone) }.getOrNull()
        if (validZone == null || request.collectedAt !in oldest..(now + 300000) ||
            request.since !in oldest..request.collectedAt || request.buckets.size > 24000 ||
            request.icons.size > 500 || request.icons.values.sumOf { it.length } > 1500000)
            fail(HttpStatus.BAD_REQUEST, "Relatório inválido")
        request.buckets.forEach { (bucket, millis) ->
            val parts = bucket.split("|")
            val date = parts.getOrNull(0)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            val hour = parts.getOrNull(1)?.toIntOrNull()
            val start = date?.atStartOfDay(validZone)?.toInstant()?.toEpochMilli()
            if (parts.size != 3 || start == null || start !in (oldest - 86400000)..now ||
                hour == null || hour !in 0..23 || millis !in 0..7200000 ||
                !parts[2].matches(Regex("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")))
                fail(HttpStatus.BAD_REQUEST, "Estatística inválida")
        }
        request.icons.forEach { (pkg, icon) ->
            val bytes = runCatching { java.util.Base64.getDecoder().decode(icon) }.getOrNull()
            if (!target.apps.containsKey(pkg) || icon.length > 12000 || bytes == null || bytes.size < 24 ||
                !bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)) ||
                java.nio.ByteBuffer.wrap(bytes,16,8).let { it.int !in 1..64 || it.int !in 1..64 })
                fail(HttpStatus.BAD_REQUEST, "Ícone inválido")
        }
        if ((target.usageUpdatedAt?.toEpochMilli() ?: 0) > request.collectedAt) return reportView(target)
        target.usagePermission = request.permission
        target.usageUpdatedAt = Instant.ofEpochMilli(request.collectedAt)
        target.usageSince = Instant.ofEpochMilli(request.since)
        target.usageZone = request.zone
        target.usage.clear()
        if (request.permission) target.usage.putAll(request.buckets)
        target.icons.clear()
        target.icons.putAll(request.icons)
        return reportView(target)
    }
    private fun reportView(link: DeviceLink) = UsageReport(link.usagePermission,
        link.usageUpdatedAt?.toEpochMilli() ?: 0, link.usageSince?.toEpochMilli() ?: 0,
        link.usageZone, link.usage.toMap(), link.icons.toMap())
    private fun view(link: DeviceLink) = LinkView(link.id.toString(), link.memberKey.orEmpty(), link.memberName.orEmpty(),
        link.ownerName.orEmpty(), link.linkedDeviceId, link.status, link.revision, link.appliedRevision,
        link.protectionEnabled, link.lastSeenAt?.toString(), link.apps.map { AppInfo(it.key, it.value) }.sortedBy { it.label }, link.blockedPackages.toSet())
}
