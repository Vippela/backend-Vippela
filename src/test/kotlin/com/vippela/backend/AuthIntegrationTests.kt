package com.vippela.backend

import com.vippela.backend.auth.AuthService
import com.vippela.backend.auth.CredenciaisInvalidasException
import com.vippela.backend.auth.EmailEmUsoException
import com.vippela.backend.dominio.TipoConta
import com.vippela.backend.repositorio.SessaoContaRepository
import com.vippela.backend.repositorio.UsuarioRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTests {
    @Autowired lateinit var auth: AuthService
    @Autowired lateinit var users: UsuarioRepository
    @Autowired lateinit var sessions: SessaoContaRepository
    @LocalServerPort var port: Int = 0

    @BeforeEach
    fun clear() {
        sessions.deleteAll()
        users.deleteAll()
    }

    private fun register(
        email: String = "parent@example.test",
        role: TipoConta = TipoConta.RESPONSAVEL,
    ) = auth.cadastrar("Teste", email, "Senha-teste-123", role)

    @Test
    fun rolesPasswordsAndSessionsAreIsolated() {
        val parent = register()
        val child = register("child@example.test", TipoConta.FAMILIAR)

        assertNotEquals(parent.usuario.id, child.usuario.id)
        assertEquals(TipoConta.FAMILIAR, auth.sessaoDe(child.token)!!.usuario.tipoConta)
        assertEquals(
            parent.usuario.id,
            auth.entrar("PARENT@example.test", "Senha-teste-123", null).usuario.id,
        )
        assertNotEquals("Senha-teste-123", users.findByEmailIgnoreCase("parent@example.test")!!.senhaHash)
        assertTrue(sessions.findAll().none { it.tokenHash == parent.token })
        assertThrows(CredenciaisInvalidasException::class.java) {
            auth.entrar("parent@example.test", "wrong", null)
        }
        assertThrows(EmailEmUsoException::class.java) { register("PARENT@example.test") }

        auth.sair(parent.token)
        assertNull(auth.sessaoDe(parent.token))
        assertEquals(child.usuario.id, auth.sessaoDe(child.token)!!.usuario.id)
    }

    @Test
    fun httpContractValidatesAndRevokesSession() {
        val client = HttpClient.newHttpClient()

        fun send(
            method: String,
            path: String,
            body: String = "",
            token: String? = null,
        ): HttpResponse<String> {
            val builder =
                HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                    .header("Content-Type", "application/json")
            if (token != null) builder.header("Authorization", "Bearer $token")
            val publisher =
                if (body.isEmpty()) HttpRequest.BodyPublishers.noBody()
                else HttpRequest.BodyPublishers.ofString(body)
            return client.send(
                builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }

        assertEquals(
            400,
            send(
                "POST",
                "/auth/register",
                """{"nome":"Teste","email":"bad","senha":"123","tipoConta":"ADMIN"}""",
            ).statusCode(),
        )
        val response =
            send(
                "POST",
                "/auth/register",
                """{"nome":"Teste","email":"http@example.test","senha":"Senha-teste-123","tipoConta":"FAMILIAR"}""",
            )
        assertEquals(201, response.statusCode())
        val token = Regex("\"token\":\"([^\"]+)\"").find(response.body())!!.groupValues[1]
        assertEquals(200, send("GET", "/auth/me", token = token).statusCode())
        assertFalse(response.body().contains("senhaHash"))
        assertEquals(204, send("POST", "/auth/logout", token = token).statusCode())
        assertEquals(401, send("GET", "/auth/me", token = token).statusCode())
        assertEquals(401, send("GET", "/auth/me").statusCode())
        assertEquals(
            200,
            send(
                "POST",
                "/auth/login",
                """{"email":"http@example.test","senha":"Senha-teste-123"}""",
            ).statusCode(),
        )
        assertEquals(
            "{\"existe\":true}",
            send("GET", "/auth/existe?email=http@example.test").body(),
        )
    }
}
