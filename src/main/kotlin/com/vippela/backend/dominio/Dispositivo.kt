package com.vippela.backend.dominio

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "dispositivo")
data class Dispositivo(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_dispositivo")
    val id: UUID? = null,

    // UNIQUE e VARCHAR(60) como no vippela_schema.sql: sem isso, o mesmo
    // aparelho entra duas vezes quando o schema vem do Hibernate.
    @Column(name = "identificador_instalacao", length = 60, nullable = false, unique = true)
    val identificadorInstalacao: String,

    @Column(name = "nome_modelo", length = 50, nullable = false)
    val nomeModelo: String,

    @Column(name = "sistema_operacional", length = 40, nullable = false)
    val sistemaOperacional: String,

    val ativo: Boolean = true,

    @ManyToOne @JoinColumn(name = "id_familiar")
    val familiar: Usuario
)