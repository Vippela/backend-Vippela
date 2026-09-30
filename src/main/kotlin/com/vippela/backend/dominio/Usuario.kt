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

    @Column(name = "nome", nullable = false, length = 50)
    val nome: String,

    @Column(name = "data_nascimento")
    val dataNascimento: LocalDate? = null,

    // O Postgres usa CITEXT (único sem diferenciar caixa). As anotações
    // reproduzem isso no schema que o Hibernate cria nos testes em H2.
    @Column(name = "email", nullable = false, unique = true)
    val email: String,

    @Column(name = "senha_hash")
    val senhaHash: String? = null,

    @Column(name = "google_uid", length = 128, unique = true)
    val googleUid: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_conta")
    val tipoConta: TipoConta,

    @Column(name = "criado_em")
    val criadoEm: Instant = Instant.now()
)