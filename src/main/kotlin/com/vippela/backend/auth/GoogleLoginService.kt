package com.vippela.backend.auth

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.google.auth.oauth2.GoogleCredentials
import com.vippela.backend.dominio.TipoConta
import com.vippela.backend.dominio.Usuario
import com.vippela.backend.repositorio.UsuarioRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.FileInputStream
import java.io.File

/** ID token do Google recusado pelo Firebase. */
class TokenGoogleInvalidoException(cause: String? = null) : RuntimeException(cause)

/** VIPPELA_GOOGLE_CREDENTIALS não aponta para um JSON de service account utilizável. */
class GoogleNaoConfiguradoException : RuntimeException("Login com Google indisponível neste servidor")

/** O e-mail já tem senha; vincular as duas contas ainda não existe. */
class EmailGoogleEmUsoException : RuntimeException("Este e-mail já tem senha. Entre com a senha e vincule o Google depois")

/**
 * O app continua usando o Firebase para obter o ID token, mas quem decide
 * se a conta existe é este backend: o token é conferido aqui antes de
 * qualquer escrita no banco.
 */
@Service
class GoogleLoginService(
    private val usuarioRepo: UsuarioRepository,
    private val service: AuthService,
    @Value("\${vippela.google.credentials:}") private val caminhoCredenciais: String
) {
    private val auth: FirebaseAuth? by lazy { iniciar() }

    @Transactional
    fun entrar(idToken: String, nome: String?, tipoConta: TipoConta): SessaoConcedida {
        val firebase = auth ?: throw GoogleNaoConfiguradoException()
        val token = decodificar(firebase, idToken)
        val email = token.email?.trim()?.lowercase()
            ?: throw TokenGoogleInvalidoException("O Google não devolveu o e-mail desta conta")

        // Já entrou antes com Google: é a mesma pessoa, nova sessão.
        usuarioRepo.findByGoogleUid(token.uid)?.let { return service.abrirSessao(it) }

        if (usuarioRepo.existsByEmailIgnoreCase(email)) throw EmailGoogleEmUsoException()

        val usuario = usuarioRepo.save(
            Usuario(
                nome = nome?.trim()?.takeIf { it.isNotEmpty() }
                    ?: token.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: email.substringBefore('@'),
                email = email,
                googleUid = token.uid,
                tipoConta = tipoConta
            )
        )
        return service.abrirSessao(usuario)
    }

    private fun decodificar(firebase: FirebaseAuth, idToken: String): FirebaseToken = try {
        val token = firebase.verifyIdToken(idToken.trim(), true)
        val provedor = (token.claims["firebase"] as? Map<*, *>)?.get("sign_in_provider")
        if (!token.isEmailVerified || token.email.isNullOrBlank() || provedor != "google.com") {
            throw TokenGoogleInvalidoException("Conta Google não verificada")
        }
        token
    } catch (e: TokenGoogleInvalidoException) {
        throw e
    } catch (e: Exception) {
        throw TokenGoogleInvalidoException(e.message)
    }

    private fun iniciar(): FirebaseAuth? {
        val caminho = caminhoCredenciais.trim()
        if (caminho.isEmpty()) return null
        val arquivo = File(caminho)
        if (!arquivo.isFile) return null
        val opcoes = FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(FileInputStream(arquivo)))
            .build()
        return FirebaseAuth.getInstance(FirebaseApp.initializeApp(opcoes, "vippela"))
    }
}
