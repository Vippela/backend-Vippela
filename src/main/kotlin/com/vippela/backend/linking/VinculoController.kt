package com.vippela.backend.linking

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class GerarConviteRequest(val nomeFamiliar: String)
data class ConfirmarConviteRequest(
    val codigo: String,
    val deviceId: String,
    val modelo: String,
    val sistemaOperacional: String
)

@RestController
@RequestMapping("/links")
class VinculoController(private val service: VinculoService) {

    @ExceptionHandler(DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun vinculoDuplicado(): Map<String, String> =
        mapOf("error" to "Este dispositivo ou vínculo já existe")

    @PostMapping("/generate")
    fun gerar(@RequestBody request: GerarConviteRequest): ResponseEntity<ConviteGeradoResponse> {
        return ResponseEntity.ok(service.gerarConvite(request.nomeFamiliar))
    }

    @PostMapping("/confirm")
    fun confirmar(@RequestBody request: ConfirmarConviteRequest): ResponseEntity<Any> {
        return try {
            val dispositivo = service.confirmarConvite(
                request.codigo, request.deviceId, request.modelo, request.sistemaOperacional
            )
            ResponseEntity.ok(dispositivo)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.GONE).body(mapOf("error" to e.message))
        }
    }
}