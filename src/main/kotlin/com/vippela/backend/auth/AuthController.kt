package com.vippela.backend.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/auth")
class AuthController(private val auth: AuthService) {
    private val attempts = mutableMapOf<String, Pair<Long, Int>>()
    @Synchronized private fun limit(request: HttpServletRequest) {
        val now = System.currentTimeMillis()
        attempts.entries.removeIf { now - it.value.first >= 60_000 }
        val ip = request.remoteAddr
        val previous = attempts[ip] ?: (now to 0)
        if (previous.second >= 30 || attempts.size >= 10000) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde um minuto")
        attempts[ip] = previous.first to previous.second + 1
    }
    @PostMapping("/register") fun register(@Valid @RequestBody body: RegisterRequest, request: HttpServletRequest): SessionResponse { limit(request); return auth.register(body) }
    @PostMapping("/login") fun login(@Valid @RequestBody body: LoginRequest, request: HttpServletRequest): SessionResponse { limit(request); return auth.login(body) }
    @PostMapping("/google") fun google(@Valid @RequestBody body: GoogleRequest, request: HttpServletRequest): SessionResponse { limit(request); return auth.google(body) }
    @GetMapping("/me") fun me(@RequestHeader("Authorization", required = false) header: String?) = auth.me(header)
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestHeader("Authorization", required = false) header: String?) = auth.logout(header)
    @GetMapping("/existe") fun exists(@RequestParam email: String, request: HttpServletRequest): Map<String, Boolean> { limit(request); return mapOf("existe" to auth.exists(email)) }
    @ExceptionHandler(DataIntegrityViolationException::class) @ResponseStatus(HttpStatus.CONFLICT)
    fun conflict() = mapOf("error" to "Conta já cadastrada")
    @ExceptionHandler(ResponseStatusException::class)
    fun failure(error: ResponseStatusException) = org.springframework.http.ResponseEntity.status(error.statusCode).body(mapOf("error" to error.reason))
}
