package com.vippela.backend

import com.vippela.backend.auth.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.net.http.*
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTests {
    @Autowired lateinit var auth: AuthService
    @Autowired lateinit var users: AuthUsers
    @Autowired lateinit var sessions: AuthSessions
    @MockitoBean lateinit var google: GoogleTokenVerifier
    @LocalServerPort var port: Int = 0
    @BeforeEach fun clear() { sessions.deleteAll(); users.deleteAll(); reset(google) }
    private fun register(email: String = "parent@example.test", role: String = "RESPONSAVEL") = auth.register(RegisterRequest("Teste", email, "Senha-teste-123", role))
    @Test fun rolesPasswordsAndSessionsAreIsolated() {
        val parent = register()
        val child = register("child@example.test", "FAMILIAR")
        assertNotEquals(parent.id, child.id)
        assertEquals("FAMILIAR", auth.me("Bearer ${child.token}").tipoConta)
        assertEquals(parent.id, auth.login(LoginRequest("PARENT@example.test", "Senha-teste-123")).id)
        assertNotEquals("Senha-teste-123", users.findByEmail("parent@example.test")!!.senhaHash)
        assertTrue(sessions.findAll().none { it.tokenHash == parent.token })
        assertEquals(401, assertThrows(ResponseStatusException::class.java) { auth.login(LoginRequest("parent@example.test", "wrong")) }.statusCode.value())
        assertEquals(409, assertThrows(ResponseStatusException::class.java) { register("PARENT@example.test") }.statusCode.value())
        auth.logout("Bearer ${parent.token}")
        assertEquals(401, assertThrows(ResponseStatusException::class.java) { auth.me("Bearer ${parent.token}") }.statusCode.value())
        assertEquals(child.id, auth.me("Bearer ${child.token}").id)
        sessions.findAll().forEach { it.expiraEm = Instant.now().minusSeconds(1); sessions.save(it) }
        assertEquals(401, assertThrows(ResponseStatusException::class.java) { auth.me("Bearer ${child.token}") }.statusCode.value())
    }
    @Test fun googleKeepsExistingRoleAndDoesNotMergePasswordAccounts() {
        `when`(google.verify("valid")).thenReturn(GoogleIdentity("google-1", "google@example.test", "Google Teste"))
        val first = auth.google(GoogleRequest("valid", "FAMILIAR"))
        val second = auth.google(GoogleRequest("valid", "RESPONSAVEL"))
        assertEquals(first.id, second.id)
        assertEquals("FAMILIAR", second.tipoConta)
        register()
        `when`(google.verify("conflict")).thenReturn(GoogleIdentity("google-2", "parent@example.test", "Outro"))
        assertEquals(409, assertThrows(ResponseStatusException::class.java) { auth.google(GoogleRequest("conflict", "RESPONSAVEL")) }.statusCode.value())
        `when`(google.verify("invalid")).thenThrow(ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED))
        assertEquals(401, assertThrows(ResponseStatusException::class.java) { auth.google(GoogleRequest("invalid", "FAMILIAR")) }.statusCode.value())
        assertEquals(2, users.count())
    }
    @Test fun googleWithoutServerConfigurationIsUnavailable() {
        assertEquals(503, assertThrows(ResponseStatusException::class.java) { FirebaseTokenVerifier("").verify("anything") }.statusCode.value())
    }
    @Test fun httpContractValidatesAndRevokesSession() {
        val client = HttpClient.newHttpClient()
        fun send(method: String, path: String, body: String = "", token: String? = null): HttpResponse<String> {
            val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type", "application/json")
            if (token != null) builder.header("Authorization", "Bearer $token")
            return client.send(builder.method(method, if(body.isEmpty()) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
        }
        assertEquals(400, send("POST", "/auth/register", """{"nome":"Teste","email":"bad","senha":"123","tipoConta":"ADMIN"}""").statusCode())
        val response = send("POST", "/auth/register", """{"nome":"Teste","email":"http@example.test","senha":"Senha-teste-123","tipoConta":"FAMILIAR"}""")
        assertEquals(200, response.statusCode())
        val token = Regex("\"token\":\"([^\"]+)\"").find(response.body())!!.groupValues[1]
        assertEquals(200, send("GET", "/auth/me", token = token).statusCode())
        assertFalse(response.body().contains("senhaHash"))
        assertEquals(204, send("POST", "/auth/logout", token = token).statusCode())
        assertEquals(401, send("GET", "/auth/me", token = token).statusCode())
        assertEquals(401, send("GET", "/auth/me").statusCode())
        assertEquals(200, send("POST", "/auth/login", """{"email":"http@example.test","senha":"Senha-teste-123"}""").statusCode())
        assertEquals("{\"existe\":true}", send("GET", "/auth/existe?email=http@example.test").body())
    }
}
