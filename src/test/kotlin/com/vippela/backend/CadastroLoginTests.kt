package com.vippela.backend.auth

import com.vippela.backend.dominio.TipoConta
import com.vippela.backend.repositorio.SessaoContaRepository
import com.vippela.backend.repositorio.UsuarioRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CadastroLoginTests {
    @Autowired lateinit var service: AuthService
    @Autowired lateinit var usuarioRepo: UsuarioRepository
    @Autowired lateinit var sessaoRepo: SessaoContaRepository

    @BeforeEach
    fun limpar() {
        sessaoRepo.deleteAll()
        usuarioRepo.deleteAll()
    }

    @Test
    fun cadastroGravaHashEEntregaTokenNovo() {
        val sessao = service.cadastrar("Ana Souza", "ana@exemplo.com", "senha-segura", TipoConta.RESPONSAVEL)

        val usuario = usuarioRepo.findByEmailIgnoreCase("ana@exemplo.com")!!
        assertNotEquals("senha-segura", usuario.senhaHash)
        assertTrue(usuario.senhaHash!!.startsWith("$2"))
        assertTrue(SenhaHasher().confere("senha-segura", usuario.senhaHash))
        assertNull(usuario.googleUid)
        assertEquals(TipoConta.RESPONSAVEL, usuario.tipoConta)

        // O banco guarda o hash do token; o token em claro não fica lá.
        val guardado = sessaoRepo.findByTokenHash(TokenService().hash(sessao.token))!!
        assertNotEquals(sessao.token, guardado.tokenHash)
        assertEquals(usuario.id, guardado.usuario.id)
    }

    @Test
    fun emailGravadoEmMinusculasEDuplicadoRecusado() {
        service.cadastrar("Ana", "Ana@Exemplo.COM ", "senha-segura", TipoConta.FAMILIAR)
        assertTrue(usuarioRepo.existsByEmailIgnoreCase("ana@exemplo.com"))

        assertThrows(EmailEmUsoException::class.java) {
            service.cadastrar("Outra Ana", "ana@exemplo.com", "outra-senha", TipoConta.FAMILIAR)
        }
        assertEquals(1, usuarioRepo.count())
    }

    @Test
    fun senhaErradaEEmailDesconhecidoFalhamIgual() {
        service.cadastrar("Ana", "ana@exemplo.com", "senha-segura", TipoConta.RESPONSAVEL)

        assertThrows(CredenciaisInvalidasException::class.java) {
            service.entrar("ana@exemplo.com", "errada", "1.1.1.1")
        }
        assertThrows(CredenciaisInvalidasException::class.java) {
            service.entrar("ninguem@exemplo.com", "senha-segura", "1.1.1.1")
        }
    }

    @Test
    fun loginEntregaSessaoQueVoltaEmMe() {
        service.cadastrar("Ana", "ana@exemplo.com", "senha-segura", TipoConta.RESPONSAVEL)

        val sessao = service.entrar("Ana@exemplo.com", "senha-segura", "1.1.1.1")

        val encontrada = service.sessaoDe(sessao.token)!!
        assertEquals("ana@exemplo.com", encontrada.usuario.email)
        assertTrue(encontrada.expiraEm.isAfter(sessao.expiraEm.minusSeconds(1)))
    }

    @Test
    fun logoutRevogaOToken() {
        val sessao = service.cadastrar("Ana", "ana@exemplo.com", "senha-segura", TipoConta.RESPONSAVEL)

        service.sair(sessao.token)

        assertNull(service.sessaoDe(sessao.token))
        assertNotNull(sessaoRepo.findByTokenHash(TokenService().hash(sessao.token))!!.revogadoEm)
    }

    @Test
    fun tokenInvalidoOuVazioNaoAbreSessao() {
        assertNull(service.sessaoDe(null))
        assertNull(service.sessaoDe(""))
        assertNull(service.sessaoDe("token-que-nunca-existiu"))
        // Esquema de Authorization diferente também não abre.
        assertNull(AuthController.bearer("Basic abc"))
        assertNull(AuthController.bearer("Bearer   "))
        assertEquals("abc", AuthController.bearer("bearer abc"))
    }

    @Test
    fun loginCertaLimpaOHistoricoDeFalhas() {
        service.cadastrar("Ana", "ana@exemplo.com", "senha-segura", TipoConta.RESPONSAVEL)
        val ip = "2.2.2.2"
        repeat(2) {
            runCatching { service.entrar("ana@exemplo.com", "errada", ip) }
        }

        service.entrar("ana@exemplo.com", "senha-segura", ip)

        // O contador zerou: duas falhas novas ainda não estouram o limite de 3.
        repeat(2) { runCatching { service.entrar("ana@exemplo.com", "errada", ip) } }
    }

    @Test
    fun muitasTentativasDoMesmoIpSaoBloqueadas() {
        service.cadastrar("Ana", "ana@exemplo.com", "senha-segura", TipoConta.RESPONSAVEL)
        val ip = "3.3.3.3"
        // vippela.auth.max-tentativas=3 nos testes.
        repeat(3) { runCatching { service.entrar("ana@exemplo.com", "errada", ip) } }

        assertThrows(MuitasTentativasException::class.java) {
            service.entrar("ana@exemplo.com", "senha-segura", ip)
        }
    }

    @Test
    fun limiteDeCadastroNaoAtingeOLimiteDeLogin() {
        val ip = "4.4.4.4"
        repeat(3) {
            service.cadastrar("Familiar $it", "familiar$it@exemplo.com", "senha-segura", TipoConta.FAMILIAR, ip = ip)
        }

        // Três cadastros não podem zerar o histórico de login do mesmo IP.
        service.cadastrar("Ana", "ana@exemplo.com", "senha-segura", TipoConta.RESPONSAVEL, ip = ip)
        assertDoesNotThrow { service.entrar("ana@exemplo.com", "senha-segura", ip) }
    }

    @Test
    fun googleSemCredencialRespondeQueNaoEstaConfigurado() {
        assertThrows(GoogleNaoConfiguradoException::class.java) {
            GoogleLoginService(usuarioRepo, service, "").entrar("token", "Ana", TipoConta.RESPONSAVEL)
        }
    }
}
