package com.vippela.backend.auth

import com.vippela.backend.dominio.TipoConta
import com.vippela.backend.dominio.Usuario
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.bind.MethodArgumentNotValidException
import java.time.Instant
import java.time.LocalDate

data class CadastroRequest(
    @field:NotBlank(message = "Informe o nome")
    @field:Size(min = 2, max = 50, message = "O nome deve ter entre 2 e 50 caracteres")
    val nome: String,

    @field:NotBlank(message = "Informe o e-mail")
    @field:Email(message = "Informe um e-mail válido")
    @field:Size(max = 254, message = "E-mail muito longo")
    val email: String,

    // 72 é o limite do BCrypt: o que passa disso é truncado, não aceito.
    @field:NotBlank(message = "Informe a senha")
    @field:Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
    val senha: String,

    @field:NotNull(message = "Escolha o tipo de conta")
    val tipoConta: TipoConta,

    val dataNascimento: LocalDate? = null
)

data class LoginRequest(
    @field:NotBlank(message = "Informe o e-mail")
    @field:Email(message = "Informe um e-mail válido")
    val email: String,

    @field:NotBlank(message = "Informe a senha")
    val senha: String
)

data class GoogleRequest(
    @field:NotBlank(message = "Token do Google ausente")
    val idToken: String,

    @field:NotNull(message = "Escolha o tipo de conta")
    val tipoConta: TipoConta,

    @field:Size(max = 50, message = "Nome muito longo")
    val nome: String? = null
)

/** Formato de erro igual ao usado em /links, para o app tratar tudo igual. */
typealias ErroResponse = Map<String, String>

data class SessaoResponse(
    val id: String,
    val nome: String,
    val email: String,
    val tipoConta: TipoConta,
    val token: String? = null,
    val expiraEm: Instant? = null
) {
    companion object {
        fun de(sessao: SessaoConcedida) = SessaoResponse(
            id = sessao.usuario.id.toString(),
            nome = sessao.usuario.nome,
            email = sessao.usuario.email,
            tipoConta = sessao.usuario.tipoConta,
            token = sessao.token,
            expiraEm = sessao.expiraEm
        )

        fun de(usuario: Usuario) = SessaoResponse(
            id = usuario.id.toString(),
            nome = usuario.nome,
            email = usuario.email,
            tipoConta = usuario.tipoConta
        )
    }
}

data class ExistenciaResponse(val existe: Boolean)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val service: AuthService,
    private val google: GoogleLoginService
) {

    @PostMapping("/register")
    fun cadastrar(
        @Valid @RequestBody request: CadastroRequest,
        http: HttpServletRequest
    ): ResponseEntity<SessaoResponse> {
        val sessao = service.cadastrar(
            nome = request.nome,
            email = request.email,
            senha = request.senha,
            tipoConta = request.tipoConta,
            dataNascimento = request.dataNascimento,
            ip = http.remoteAddr
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(SessaoResponse.de(sessao))
    }

    @PostMapping("/login")
    fun entrar(
        @Valid @RequestBody request: LoginRequest,
        http: HttpServletRequest
    ): ResponseEntity<SessaoResponse> =
        ResponseEntity.ok(
            SessaoResponse.de(service.entrar(request.email, request.senha, http.remoteAddr))
        )

    @PostMapping("/google")
    fun entrarComGoogle(
        @Valid @RequestBody request: GoogleRequest
    ): ResponseEntity<SessaoResponse> =
        ResponseEntity.ok(
            SessaoResponse.de(
                google.entrar(request.idToken, request.nome, request.tipoConta)
            )
        )

    @GetMapping("/me")
    fun eu(
        @RequestHeader(name = "Authorization", required = false) authorization: String?
    ): ResponseEntity<Any> {
        val sessao = service.sessaoDe(bearer(authorization))
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Sessão inválida ou expirada"))
        return ResponseEntity.ok(SessaoResponse.de(sessao))
    }

    @PostMapping("/logout")
    fun sair(@RequestHeader(name = "Authorization", required = false) authorization: String?):
        ResponseEntity<Void> {
        service.sair(bearer(authorization))
        return ResponseEntity.noContent().build()
    }

    /** O app usa isso para levar o usuário ao cadastro em vez de insistir no login. */
    @GetMapping("/existe")
    fun existe(
        @RequestParam @Email(message = "Informe um e-mail válido") email: String
    ): ResponseEntity<ExistenciaResponse> =
        ResponseEntity.ok(ExistenciaResponse(service.existeEmail(email)))

    companion object {
        fun bearer(authorization: String?): String? {
            val valor = authorization?.trim().orEmpty()
            if (!valor.startsWith("Bearer ", ignoreCase = true)) return null
            return valor.substring(7).trim().ifEmpty { null }
        }
    }
}

/** Só cobre o pacote de autenticação; o controller de vínculo tem os handlers dele. */
@RestControllerAdvice(basePackages = ["com.vippela.backend.auth"])
class AuthErros {

    @ExceptionHandler(EmailEmUsoException::class)
    fun emailEmUso(e: EmailEmUsoException): ResponseEntity<ErroResponse> =
        resposta(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler(CredenciaisInvalidasException::class)
    fun credenciais(e: CredenciaisInvalidasException): ResponseEntity<ErroResponse> =
        resposta(HttpStatus.UNAUTHORIZED, e.message)

    @ExceptionHandler(MuitasTentativasException::class)
    fun muitasTentativas(e: MuitasTentativasException): ResponseEntity<ErroResponse> =
        resposta(HttpStatus.TOO_MANY_REQUESTS, e.message)

    @ExceptionHandler(TokenGoogleInvalidoException::class)
    fun tokenGoogle(e: TokenGoogleInvalidoException): ResponseEntity<ErroResponse> =
        resposta(HttpStatus.UNAUTHORIZED, e.message)

    @ExceptionHandler(GoogleNaoConfiguradoException::class)
    fun googleDesligado(e: GoogleNaoConfiguradoException): ResponseEntity<ErroResponse> =
        resposta(HttpStatus.SERVICE_UNAVAILABLE, e.message)

    @ExceptionHandler(EmailGoogleEmUsoException::class)
    fun emailGoogleEmUso(e: EmailGoogleEmUsoException): ResponseEntity<ErroResponse> =
        resposta(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validacao(e: MethodArgumentNotValidException): ResponseEntity<ErroResponse> {
        val campos: Map<String, String> =
            e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Valor inválido") }
        return resposta(HttpStatus.BAD_REQUEST, campos.values.firstOrNull(), campos)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun validacaoDeParametro(e: HandlerMethodValidationException): ResponseEntity<ErroResponse> =
        resposta(HttpStatus.BAD_REQUEST, e.message.orEmpty().ifEmpty { "Dados inválidos" })

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun corpoInvalido(e: HttpMessageNotReadableException): ResponseEntity<ErroResponse> =
        resposta(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido")

    private fun resposta(
        status: HttpStatus,
        mensagem: String?,
        campos: Map<String, String>? = null
    ): ResponseEntity<ErroResponse> =
        ResponseEntity.status(status).body(campos ?: mapOf("error" to (mensagem ?: "Erro")))
}
