package com.vippela.backend

import com.vippela.backend.dominio.*
import com.vippela.backend.linking.VinculoService
import com.vippela.backend.repositorio.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@SpringBootTest
class VinculoIntegrationTests {
    @Autowired lateinit var service: VinculoService
    @Autowired lateinit var usuarioRepo: UsuarioRepository
    @Autowired lateinit var vinculoRepo: VinculoFamiliarRepository
    @Autowired lateinit var conviteRepo: ConviteVinculoRepository
    @Autowired lateinit var dispositivoRepo: DispositivoRepository
    @Autowired lateinit var sessaoRepo: SessaoContaRepository

    @BeforeEach
    fun limpar() {
        // Ordem importa por causa das foreign keys.
        dispositivoRepo.deleteAll()
        sessaoRepo.deleteAll()
        conviteRepo.deleteAll()
        vinculoRepo.deleteAll()
        usuarioRepo.deleteAll()
    }

    @Test
    fun gerarConviteCriaFamiliarEVinculoAtivo() {
        service.gerarConvite("Ana")

        val familiares = usuarioRepo.findAll().filter { it.tipoConta == TipoConta.FAMILIAR }
        assertEquals(1, familiares.size)
        assertEquals("Ana", familiares.first().nome)

        val vinculos = vinculoRepo.findAll()
        assertEquals(1, vinculos.size)
        assertEquals(StatusVinculo.ATIVO, vinculos.first().status)
    }

    @Test
    fun confirmarConviteCriaDispositivoEConsomeOCodigo() {
        val convite = service.gerarConvite("Ana")

        val dispositivo = service.confirmarConvite(convite.codigo, "device-123", "Pixel 6", "Android 14")

        assertEquals("device-123", dispositivo.identificadorInstalacao)
        assertTrue(dispositivo.ativo)

        val salvo = conviteRepo.findAll().first()
        assertNotNull(salvo.consumidoEm)
    }

    @Test
    fun codigoInvalidoLancaExcecao() {
        assertThrows(IllegalArgumentException::class.java) {
            service.confirmarConvite("000000", "device-x", "Pixel", "Android")
        }
    }

    @Test
    fun codigoJaConsumidoNaoPodeSerReutilizado() {
        val convite = service.gerarConvite("Ana")
        service.confirmarConvite(convite.codigo, "device-1", "Pixel", "Android")

        assertThrows(IllegalArgumentException::class.java) {
            service.confirmarConvite(convite.codigo, "device-2", "Pixel", "Android")
        }
    }

    @Test
    fun codigoExpiradoLancaExcecao() {
        val responsavel = usuarioRepo.save(usuarioTeste(TipoConta.RESPONSAVEL))
        val familiar = usuarioRepo.save(usuarioTeste(TipoConta.FAMILIAR))
        val vinculo = vinculoRepo.save(VinculoFamiliar(responsavel = responsavel, familiar = familiar))
        conviteRepo.save(
            ConviteVinculo(
                codigoHash = service.hash("111111"),
                expiraEm = Instant.now().minus(1, ChronoUnit.MINUTES),
                vinculo = vinculo,
                responsavel = responsavel
            )
        )

        assertThrows(IllegalStateException::class.java) {
            service.confirmarConvite("111111", "device-x", "Pixel", "Android")
        }
    }

    @Test
    fun mesmoDispositivoNaoPodeSerVinculadoDuasVezes() {
        val convite1 = service.gerarConvite("Ana")
        val convite2 = service.gerarConvite("Bruno")

        service.confirmarConvite(convite1.codigo, "device-repetido", "Pixel", "Android")

        assertThrows(DataIntegrityViolationException::class.java) {
            service.confirmarConvite(convite2.codigo, "device-repetido", "Pixel", "Android")
        }
    }

    @Test
    fun familiarComDispositivoAtivoNaoPodeVincularOutro() {
        val responsavel = usuarioRepo.save(usuarioTeste(TipoConta.RESPONSAVEL))
        val familiar = usuarioRepo.save(usuarioTeste(TipoConta.FAMILIAR))

        val vinculo1 = vinculoRepo.save(VinculoFamiliar(responsavel = responsavel, familiar = familiar))
        conviteRepo.save(
            ConviteVinculo(
                codigoHash = service.hash("222222"),
                expiraEm = Instant.now().plus(5, ChronoUnit.MINUTES),
                vinculo = vinculo1,
                responsavel = responsavel
            )
        )
        service.confirmarConvite("222222", "device-primeiro", "Pixel", "Android")

        val outroResponsavel = usuarioRepo.save(usuarioTeste(TipoConta.RESPONSAVEL))
        val vinculo2 = vinculoRepo.save(VinculoFamiliar(responsavel = outroResponsavel, familiar = familiar))
        conviteRepo.save(
            ConviteVinculo(
                codigoHash = service.hash("333333"),
                expiraEm = Instant.now().plus(5, ChronoUnit.MINUTES),
                vinculo = vinculo2,
                responsavel = outroResponsavel
            )
        )

        assertThrows(IllegalStateException::class.java) {
            service.confirmarConvite("333333", "device-segundo", "Pixel", "Android")
        }
    }

    @Test
    fun umCodigoNaoPodeSerReivindicadoPorDoisDispositivosAoMesmoTempo() {
        val convite = service.gerarConvite("Ana")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val resultados = executor.invokeAll(
                listOf("device-A", "device-B").map { deviceId ->
                    Callable {
                        runCatching {
                            service.confirmarConvite(convite.codigo, deviceId, "Pixel", "Android")
                        }.isSuccess
                    }
                }
            ).map { it.get() }

            assertEquals(1, resultados.count { it })
            assertEquals(1, dispositivoRepo.findAll().size)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun usuarioTeste(tipo: TipoConta) = Usuario(
        nome = "Usuário Teste",
        dataNascimento = LocalDate.of(1990, 1, 1),
        email = "teste.${UUID.randomUUID()}@vippela.com",
        senhaHash = "senha-fake-hash",
        tipoConta = tipo
    )
}