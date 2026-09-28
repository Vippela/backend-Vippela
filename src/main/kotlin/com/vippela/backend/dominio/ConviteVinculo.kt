package com.vippela.backend.dominio

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "convite_vinculo")
data class ConviteVinculo(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_convite")
    val id: UUID? = null,

    @Column(name = "codigo_hash")
    val codigoHash: String,

    @Column(name = "expira_em")
    val expiraEm: Instant,

    @Column(name = "consumido_em")
    var consumidoEm: Instant? = null,

    @OneToOne @JoinColumn(name = "id_vinculo")
    val vinculo: VinculoFamiliar,

    @ManyToOne @JoinColumn(name = "id_responsavel")
    val responsavel: Usuario
)