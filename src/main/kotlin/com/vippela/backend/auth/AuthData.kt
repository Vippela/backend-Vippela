package com.vippela.backend.auth

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "auth_usuario")
class AuthUser(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 120) var nome: String = "",
    @Column(nullable = false, unique = true, length = 254) var email: String = "",
    @Column(length = 512) var senhaHash: String? = null,
    @Column(unique = true, length = 128) var googleUid: String? = null,
    @Column(nullable = false, length = 16) var tipoConta: String = "FAMILIAR",
)

@Entity
@Table(name = "auth_sessao")
class AuthSession(
    @Id @Column(length = 64) var tokenHash: String = "",
    @Column(nullable = false) var usuarioId: UUID = UUID.randomUUID(),
    @Column(nullable = false) var expiraEm: Instant = Instant.now(),
)

interface AuthUsers : JpaRepository<AuthUser, UUID> {
    fun findByEmail(email: String): AuthUser?
    fun findByGoogleUid(googleUid: String): AuthUser?
    fun existsByEmail(email: String): Boolean
}
interface AuthSessions : JpaRepository<AuthSession, String>

data class RegisterRequest(
    @field:NotBlank @field:Size(max = 120) val nome: String,
    @field:NotBlank @field:Email @field:Size(max = 254) val email: String,
    @field:Size(min = 8, max = 256) val senha: String,
    val tipoConta: String,
)
data class SessionResponse(val id: String, val nome: String, val email: String, val tipoConta: String, val token: String?, val expiraEm: String)
data class GoogleIdentity(val uid: String, val email: String, val nome: String?)
