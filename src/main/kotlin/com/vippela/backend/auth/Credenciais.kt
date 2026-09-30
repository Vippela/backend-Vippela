package com.vippela.backend.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Senha nunca é gravada em claro. O custo 12 é o padrão do BCrypt e
 * encarece a força bruta sem deixar o login lento na prática.
 */
@Component
class SenhaHasher {
    private val encoder = BCryptPasswordEncoder(12)

    fun gerar(senha: String): String = encoder.encode(senha) ?: error("BCrypt não devolveu hash")

    fun confere(senha: String, hash: String?): Boolean =
        hash != null && encoder.matches(senha, hash)
}

/**
 * Token de sessão: 256 bits aleatórios em Base64URL, entregues uma única
 * vez. O banco guarda somente o SHA-256 — quem ler a tabela não consegue
 * abrir a sessão de ninguém.
 */
@Component
class TokenService {
    private val random = SecureRandom()

    fun gerarToken(): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also { random.nextBytes(it) })

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
