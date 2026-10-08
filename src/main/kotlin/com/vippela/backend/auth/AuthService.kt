package com.vippela.backend.auth

import com.vippela.backend.dominio.SessaoConta
import com.vippela.backend.dominio.TipoConta
import com.vippela.backend.dominio.Usuario
import com.vippela.backend.repositorio.SessaoContaRepository
import com.vippela.backend.repositorio.UsuarioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

data class SessaoConcedida(
    val usuario: Usuario,
    val token: String,
    val expiraEm: Instant
)

/** E-mail já cadastrado. A API transforma em 409. */
class EmailEmUsoException : RuntimeException("Este e-mail já está cadastrado")

/** E-mail ou senha conferidos sem sucesso. A API transforma em 401. */
class CredenciaisInvalidasException : RuntimeException("E-mail ou senha incorretos")

/** Muitas tentativas seguidas do mesmo endereço. A API transforma em 429. */
class MuitasTentativasException : RuntimeException("Muitas tentativas. Aguarde alguns minutos")

/**
 * Cadastro e login com e-mail e senha. A sessão é um token opaco
 * guardado apenas como hash em sessao_conta, então dá para revogar
 * (logout) e expirar sem estado no servidor.
 */
@Service
@Transactional
class AuthService(
    private val usuarioRepo: UsuarioRepository,
    private val sessaoRepo: SessaoContaRepository,
    private val senhas: SenhaHasher,
    private val tokens: TokenService,
    private val config: AuthConfiguracao
) {
    // Tentativas por IP, em memória: cobre a instalação inteira sem
    // precisar de uma tabela extra. Reiniciar o backend zera a contagem.
    private val tentativas = ConcurrentHashMap<String, MutableList<Instant>>()

    fun cadastrar(
        nome: String,
        email: String,
        senha: String,
        tipoConta: TipoConta,
        dataNascimento: LocalDate? = null,
        ip: String? = null
    ): SessaoConcedida {
        conferirLimite("cadastro", ip)
        val endereco = normalizar(email)
        if (usuarioRepo.existsByEmailIgnoreCase(endereco)) {
            registrarFalha("cadastro", ip)
            throw EmailEmUsoException()
        }

        val usuario = usuarioRepo.save(
            Usuario(
                nome = nome.trim(),
                dataNascimento = dataNascimento,
                email = endereco,
                senhaHash = senhas.gerar(senha),
                tipoConta = tipoConta
            )
        )
        limparTentativas("cadastro", ip)
        return abrirSessao(usuario)
    }

    fun entrar(email: String, senha: String, ip: String?): SessaoConcedida {
        conferirLimite("login", ip)
        val endereco = normalizar(email)
        val usuario = usuarioRepo.findByEmailIgnoreCase(endereco)
        // A mesma resposta para e-mail inexistente e senha errada: não
        // entregamos ao atacante a lista de quem tem conta.
        if (usuario == null || !senhas.confere(senha, usuario.senhaHash)) {
            registrarFalha("login", ip)
            throw CredenciaisInvalidasException()
        }
        limparTentativas("login", ip)
        return abrirSessao(usuario)
    }

    /** Grava a sessão e devolve o token, que não aparece de novo. */
    fun abrirSessao(usuario: Usuario): SessaoConcedida {
        val token = tokens.gerarToken()
        val agora = Instant.now()
        val expiraEm = agora.plus(config.validadeHoras, ChronoUnit.HOURS)
        sessaoRepo.save(
            SessaoConta(
                usuario = usuario,
                tokenHash = tokens.hash(token),
                criadoEm = agora,
                expiraEm = expiraEm
            )
        )
        return SessaoConcedida(usuario, token, expiraEm)
    }

    /** Sessão ativa para o token informado, ou null se estiver revogada/expirada. */
    @Transactional(readOnly = true)
    fun sessaoDe(token: String?): SessaoConcedida? {
        if (token.isNullOrBlank()) return null
        val agora = Instant.now()
        val sessao = sessaoRepo.comUsuarioPorTokenHash(tokens.hash(token.trim())) ?: return null
        if (!sessao.ativaEm(agora)) return null
        return SessaoConcedida(sessao.usuario, token, sessao.expiraEm)
    }

    fun sair(token: String?) {
        if (token.isNullOrBlank()) return
        val agora = Instant.now()
        sessaoRepo.findByTokenHash(tokens.hash(token.trim()))
            ?.takeIf { it.ativaEm(agora) }
            ?.also {
                it.revogadoEm = agora
                sessaoRepo.save(it)
            }
    }

    fun existeEmail(email: String) = usuarioRepo.existsByEmailIgnoreCase(normalizar(email))

    private fun normalizar(email: String) = email.trim().lowercase()

    private fun conferirLimite(escopo: String, ip: String?) {
        if (ip == null) return
        if (registrar(escopo, ip).size >= config.maxTentativas) throw MuitasTentativasException()
    }

    private fun registrarFalha(escopo: String, ip: String?) {
        if (ip == null) return
        synchronized(tentativas) {
            val registro = registrar(escopo, ip)
            registro.add(Instant.now())
            tentativas[chave(escopo, ip)] = registro
        }
    }

    private fun registrar(escopo: String, ip: String): MutableList<Instant> {
        val limite = Instant.now().minus(config.janelaMinutos, ChronoUnit.MINUTES)
        val existente = tentativas[chave(escopo, ip)]
        return (existente ?: mutableListOf()).filter { it.isAfter(limite) }.toMutableList()
    }

    private fun limparTentativas(escopo: String, ip: String?) {
        if (ip == null) return
        synchronized(tentativas) { tentativas.remove(chave(escopo, ip)) }
    }

    private fun chave(escopo: String, ip: String) = "$escopo:$ip"
}
