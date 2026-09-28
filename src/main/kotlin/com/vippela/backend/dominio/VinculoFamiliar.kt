package com.vippela.backend.dominio

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class StatusVinculo { ATIVO, ENCERRADO }

@Entity
@Table(name = "vinculo_familiar")
data class VinculoFamiliar(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_vinculo")
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    var status: StatusVinculo = StatusVinculo.ATIVO,

    @Column(name = "criado_em")
    val criadoEm: Instant = Instant.now(),

    @Column(name = "encerrado_em")
    var encerradoEm: Instant? = null,

    @ManyToOne @JoinColumn(name = "id_responsavel")
    val responsavel: Usuario,

    @ManyToOne @JoinColumn(name = "id_familiar")
    val familiar: Usuario
)