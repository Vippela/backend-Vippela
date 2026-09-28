package com.vippela.backend.dominio

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "dispositivo")
data class Dispositivo(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_dispositivo")
    val id: UUID? = null,

    @Column(name = "identificador_instalacao")
    val identificadorInstalacao: String,

    @Column(name = "nome_modelo")
    val nomeModelo: String,

    @Column(name = "sistema_operacional")
    val sistemaOperacional: String,

    val ativo: Boolean = true,

    @ManyToOne @JoinColumn(name = "id_familiar")
    val familiar: Usuario
)