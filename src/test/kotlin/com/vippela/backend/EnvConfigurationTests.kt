package com.vippela.backend

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

class EnvConfigurationTests {
    @TempDir lateinit var directory: Path
    @Configuration(proxyBeanMethods = false)
    class ConfigOnly

    private fun config(): Path {
        val original = Files.readString(Path.of("src/main/resources/application.properties"))
        val isolated = original.replace("optional:file:./.env[.properties]", "optional:${directory.resolve(".env").toUri()}[.properties]")
        return directory.resolve("application.properties").also { Files.writeString(it, isolated) }
    }
    private fun app() = SpringApplication(ConfigOnly::class.java).apply {
        setWebApplicationType(WebApplicationType.NONE)
        setLogStartupInfo(false)
    }
    @Test fun loadsDotEnvWithoutShellExpansionAndAllowsOverrides() {
        Files.writeString(directory.resolve(".env"), """
            VIPPELA_DB_URL=jdbc:postgresql://example.invalid:5432/postgres?sslmode=require
            VIPPELA_DB_USER=test_user
            VIPPELA_DB_PASSWORD=test#with=literal${'$'}value
            VIPPELA_GOOGLE_CREDENTIALS=/outside/repository/firebase.json
            PORT=8181
        """.trimIndent())
        app().run("--spring.config.location=${config().toUri()}", "--PORT=8282").use { context ->
            val env = context.environment
            assertEquals("jdbc:postgresql://example.invalid:5432/postgres?sslmode=require", env.getProperty("spring.datasource.url"))
            assertEquals("test_user", env.getProperty("spring.datasource.username"))
            assertEquals("test#with=literal${'$'}value", env.getProperty("spring.datasource.password"))
            assertEquals("/outside/repository/firebase.json", env.getProperty("vippela.google.credentials"))
            assertEquals("8282", env.getProperty("server.port"))
        }
    }
    @Test fun absentDotEnvDoesNotPreventEnvironmentConfiguration() {
        app().run("--spring.config.location=${config().toUri()}", "--VIPPELA_DB_USER=external_user").use {
            assertEquals("external_user", it.environment.getProperty("spring.datasource.username"))
        }
    }
}
