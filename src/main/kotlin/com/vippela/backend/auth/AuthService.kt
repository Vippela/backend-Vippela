package com.vippela.backend.auth

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale

@Service
@Transactional
class AuthService(private val users: AuthUsers, private val sessions: AuthSessions, private val google: GoogleTokenVerifier) {
    private val passwords = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()
    private val dummyHash = passwords.encode("invalid-login-placeholder")
    private val random = SecureRandom()
    private fun fail(status: HttpStatus, message: String): Nothing = throw ResponseStatusException(status, message)
    private fun email(value: String) = value.trim().lowercase(Locale.ROOT)
    private fun role(value: String): String {
        if (value !in setOf("RESPONSAVEL", "FAMILIAR")) fail(HttpStatus.BAD_REQUEST, "Tipo de conta inválido")
        return value
    }
    private fun digest(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun response(user: AuthUser, session: AuthSession, token: String? = null) = SessionResponse(user.id.toString(), user.nome, user.email, user.tipoConta, token, session.expiraEm.toString())
    private fun issue(user: AuthUser): SessionResponse {
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val session = sessions.save(AuthSession(digest(token), user.id, Instant.now().plusSeconds(7 * 24 * 3600)))
        return response(user, session, token)
    }
    fun register(request: RegisterRequest): SessionResponse {
        val address = email(request.email)
        if (users.existsByEmail(address)) fail(HttpStatus.CONFLICT, "E-mail já cadastrado")
        val user = users.saveAndFlush(AuthUser(nome = request.nome.trim(), email = address, senhaHash = passwords.encode(request.senha), tipoConta = role(request.tipoConta)))
        return issue(user)
    }
    fun login(request: LoginRequest): SessionResponse {
        val user = users.findByEmail(email(request.email))
        val matches = passwords.matches(request.senha, user?.senhaHash ?: dummyHash)
        if (user?.senhaHash == null || !matches) fail(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos")
        return issue(user)
    }
    fun google(request: GoogleRequest): SessionResponse {
        val identity = google.verify(request.idToken)
        val existing = users.findByGoogleUid(identity.uid)
        if (existing != null) return issue(existing)
        val address = email(identity.email)
        if (users.existsByEmail(address)) fail(HttpStatus.CONFLICT, "Entre pelo método já cadastrado para este e-mail")
        val name = (identity.nome ?: request.nome ?: "Usuário").trim().take(120).ifBlank { "Usuário" }
        return issue(users.saveAndFlush(AuthUser(nome = name, email = address, googleUid = identity.uid, tipoConta = role(request.tipoConta))))
    }
    private fun session(header: String?): AuthSession {
        val token = header?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        if (token == null || !token.matches(Regex("[A-Za-z0-9_-]{43}"))) fail(HttpStatus.UNAUTHORIZED, "Sessão inválida")
        val session = sessions.findById(digest(token)).orElse(null) ?: fail(HttpStatus.UNAUTHORIZED, "Sessão inválida")
        if (!session.expiraEm.isAfter(Instant.now())) fail(HttpStatus.UNAUTHORIZED, "Sessão expirada")
        return session
    }
    fun me(header: String?): SessionResponse {
        val session = session(header)
        val user = users.findById(session.usuarioId).orElse(null) ?: fail(HttpStatus.UNAUTHORIZED, "Sessão inválida")
        return response(user, session)
    }
    fun logout(header: String?) { sessions.delete(session(header)) }
    fun exists(address: String) = users.existsByEmail(email(address))
}
