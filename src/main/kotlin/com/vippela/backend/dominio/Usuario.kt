package com.vippela.backend.dominio

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TipoConta { RESPONSAVEL, FAMILIAR }

@Entity
@Table(name = "usuario")
data class Usuario(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_usuario")
    val id: UUID? = null,

    val nome: String,

    @Column(name = "data_nascimento")
    val dataNascimento: LocalDate,

    val email: String,

    @Column(name = "senha_hash")
    val senhaHash: String? = null,

    @Column(name = "google_uid")
    val googleUid: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_conta")
    val tipoConta: TipoConta,

    @Column(name = "criado_em")
    val criadoEm: Instant = Instant.now()
)