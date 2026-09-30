package com.vippela.backend.repositorio

import com.vippela.backend.dominio.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface UsuarioRepository : JpaRepository<Usuario, UUID> {
    fun findByEmailIgnoreCase(email: String): Usuario?
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun findByGoogleUid(googleUid: String): Usuario?
}

interface SessaoContaRepository : JpaRepository<SessaoConta, UUID> {
    fun findByTokenHash(tokenHash: String): SessaoConta?

    /** Traz o usuário junto: /auth/me responde fora da transação. */
    @Query("select s from SessaoConta s join fetch s.usuario where s.tokenHash = :hash")
    fun comUsuarioPorTokenHash(hash: String): SessaoConta?

    fun deleteByExpiraEmBefore(limite: Instant): Long
}

interface VinculoFamiliarRepository : JpaRepository<VinculoFamiliar, UUID>

interface ConviteVinculoRepository : JpaRepository<ConviteVinculo, UUID> {
    fun findByCodigoHashAndConsumidoEmIsNull(codigoHash: String): ConviteVinculo?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConviteVinculo c where c.codigoHash = :hash and c.consumidoEm is null")
    fun buscarParaConsumir(hash: String): ConviteVinculo?
}

interface DispositivoRepository : JpaRepository<Dispositivo, UUID> {
    fun findByIdentificadorInstalacao(identificador: String): Dispositivo?
    fun findByFamiliar_IdAndAtivo(familiarId: UUID, ativo: Boolean): Dispositivo?
}
