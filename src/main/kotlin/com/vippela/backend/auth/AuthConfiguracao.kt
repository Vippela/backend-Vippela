package com.vippela.backend.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Valores de vippela.auth.* em um lugar só. Evita @Value espalhado no
 * construtor dos serviços e deixa o ajuste por variável de ambiente
 * explícito na configuração.
 */
@Component
class AuthConfiguracao(
    @Value("\${vippela.auth.token-ttl-hours:720}") val validadeHoras: Long,
    @Value("\${vippela.auth.max-tentativas:10}") val maxTentativas: Int,
    @Value("\${vippela.auth.janela-tentativas-minutos:15}") val janelaMinutos: Long
)
