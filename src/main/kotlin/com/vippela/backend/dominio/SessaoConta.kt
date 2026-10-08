package com.vippela.backend.dominio

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Sessão de login. O token vai inteiro e sem hash para o aparelho; aqui
 * fica só o SHA-256 dele, como já acontece com convite_vinculo.codigo_hash.
 */
@Entity
@Table(name = "sessao_conta")
data class SessaoConta(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_sessao")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario")
    val usuario: Usuario,

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    val tokenHash: String,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: Instant = Instant.now(),

    @Column(name = "expira_em", nullable = false)
    val expiraEm: Instant,

    @Column(name = "ultimo_uso_em")
    var ultimoUsoEm: Instant? = null,

    @Column(name = "revogado_em")
    var revogadoEm: Instant? = null
) {
    fun ativaEm(momento: Instant): Boolean = revogadoEm == null && expiraEm.isAfter(momento)
}
