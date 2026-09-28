package com.vippela.backend.linking

import com.vippela.backend.dominio.*
import com.vippela.backend.repositorio.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ConviteGeradoResponse(val codigo: String, val expiraEm: String)

@Service
@Transactional
class VinculoService(
    private val usuarioRepo: UsuarioRepository,
    private val vinculoRepo: VinculoFamiliarRepository,
    private val conviteRepo: ConviteVinculoRepository,
    private val dispositivoRepo: DispositivoRepository
) {
    private fun responsavelDeTeste(): Usuario =
        usuarioRepo.findAll().firstOrNull { it.tipoConta == TipoConta.RESPONSAVEL }
            ?: usuarioRepo.save(
                Usuario(
                    nome = "Responsável Teste",
                    dataNascimento = java.time.LocalDate.of(1990, 1, 1),
                    email = "responsavel.teste@vippela.com",
                    senhaHash = "senha-fake-hash",
                    tipoConta = TipoConta.RESPONSAVEL
                )
            )

    fun gerarConvite(nomeFamiliar: String): ConviteGeradoResponse {
        val responsavel = responsavelDeTeste()

        val familiar = usuarioRepo.save(
            Usuario(
                nome = nomeFamiliar,
                dataNascimento = java.time.LocalDate.now().minusYears(10),
                email = "familiar.${UUID.randomUUID()}@vippela.com",
                senhaHash = "senha-fake-hash",
                tipoConta = TipoConta.FAMILIAR
            )
        )

        val vinculo = vinculoRepo.save(
            VinculoFamiliar(responsavel = responsavel, familiar = familiar)
        )

        val codigo = gerarCodigoSeguro()
        conviteRepo.save(
            ConviteVinculo(
                codigoHash = hash(codigo),
                expiraEm = Instant.now().plus(5, ChronoUnit.MINUTES),
                vinculo = vinculo,
                responsavel = responsavel
            )
        )

        return ConviteGeradoResponse(codigo, Instant.now().plus(5, ChronoUnit.MINUTES).toString())
    }

    fun confirmarConvite(codigo: String, deviceId: String, modelo: String, so: String): Dispositivo {
        val convite = conviteRepo.buscarParaConsumir(hash(codigo))
            ?: throw IllegalArgumentException("Código inválido")

        if (convite.expiraEm.isBefore(Instant.now())) {
            throw IllegalStateException("Código expirado")
        }

        val familiarId = convite.vinculo.familiar.id!!
        if (dispositivoRepo.findByFamiliar_IdAndAtivo(familiarId, true) != null) {
            throw IllegalStateException("Este familiar já tem um dispositivo vinculado")
        }

        convite.consumidoEm = Instant.now()
        conviteRepo.save(convite)

        return dispositivoRepo.save(
            Dispositivo(
                identificadorInstalacao = deviceId,
                nomeModelo = modelo,
                sistemaOperacional = so,
                familiar = convite.vinculo.familiar
            )
        )
    }

    fun gerarCodigoSeguro(): String {
        val random = SecureRandom()
        return (100000 + random.nextInt(900000)).toString()
    }

    fun hash(valor: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(valor.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}