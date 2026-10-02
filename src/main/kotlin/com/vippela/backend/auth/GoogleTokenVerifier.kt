package com.vippela.backend.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.io.FileInputStream

interface GoogleTokenVerifier { fun verify(token: String): GoogleIdentity }

@Component
class FirebaseTokenVerifier(@Value("\${vippela.google.credentials:}") private val credentialsPath: String) : GoogleTokenVerifier {
    private val firebase: FirebaseAuth by lazy {
        try {
            val credentials = FileInputStream(credentialsPath).use { GoogleCredentials.fromStream(it) }
            val options = FirebaseOptions.builder().setCredentials(credentials).build()
            FirebaseAuth.getInstance(FirebaseApp.initializeApp(options, "vippela-${java.util.UUID.randomUUID()}"))
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google não configurado no servidor")
        }
    }
    override fun verify(token: String): GoogleIdentity {
        if (credentialsPath.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google não configurado no servidor")
        val decoded = try { firebase.verifyIdToken(token, true) }
        catch (_: FirebaseAuthException) { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token Google inválido") }
        val provider = (decoded.claims["firebase"] as? Map<*, *>)?.get("sign_in_provider")
        if (!decoded.isEmailVerified || decoded.email.isNullOrBlank() || provider != "google.com")
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Conta Google não verificada")
        return GoogleIdentity(decoded.uid, decoded.email, decoded.name)
    }
}
