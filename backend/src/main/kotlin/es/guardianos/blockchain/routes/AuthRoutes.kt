package es.guardianos.blockchain.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import es.guardianos.blockchain.model.ErrorResponse
import es.guardianos.blockchain.model.HandoffResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

/**
 * Handoff SSO: guardianos-audit redirige al usuario a blockchain.guardianos.es
 * con el JWT propio como ?handoff=<jwt>. Este endpoint lo valida (mismo secret)
 * y devuelve un token de sesión específico para el dashboard blockchain.
 */
fun Route.authRoutes(jwtSecret: String, issuer: String) {

    get("/api/v1/auth/session") {
        val handoff = call.request.queryParameters["handoff"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Parámetro 'handoff' requerido"))

        try {
            val verifier = JWT.require(Algorithm.HMAC256(jwtSecret))
                .withIssuer(issuer)
                .build()

            val decoded = verifier.verify(handoff)
            val tenantId = decoded.getClaim("tenantId").asString()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token sin tenantId"))

            val blockchainEnabled = decoded.getClaim("blockchainEnabled").asBoolean() ?: false
            if (!blockchainEnabled) {
                return@get call.respond(HttpStatusCode.Forbidden,
                    ErrorResponse("Tu plan no incluye acceso al módulo blockchain. Actualiza tu suscripción."))
            }

            // Emitir nuevo token de sesión blockchain (misma duración que el original)
            val expiresAt = decoded.expiresAt ?: Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
            val newToken = JWT.create()
                .withIssuer(issuer)
                .withClaim("tenantId", tenantId)
                .withClaim("blockchainEnabled", true)
                .withExpiresAt(expiresAt)
                .sign(Algorithm.HMAC256(jwtSecret))

            call.respond(HandoffResponse(token = newToken))

        } catch (e: Exception) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token inválido: ${e.message}"))
        }
    }
}
