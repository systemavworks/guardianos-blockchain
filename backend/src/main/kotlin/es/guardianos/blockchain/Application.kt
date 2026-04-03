package es.guardianos.blockchain

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Application")

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val jwtSecret = environment.config.property("auth.jwtSecret").getString()
    val issuer    = environment.config.property("auth.issuer").getString()

    // ── Serialization ──────────────────────────────────────────────────────
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults    = true
            prettyPrint       = false
        })
    }

    // ── CORS ───────────────────────────────────────────────────────────────
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        // En producción: guardianos.es y blockchain.guardianos.es
        allowHost("guardianos.es", schemes = listOf("https"))
        allowHost("blockchain.guardianos.es", schemes = listOf("https"))
        // En desarrollo
        allowHost("localhost:5173", schemes = listOf("http"))
        allowHost("localhost:5174", schemes = listOf("http"))
    }

    // ── Auth JWT ───────────────────────────────────────────────────────────
    // Valida el mismo JWT emitido por guardianos-audit (mismo JWT_SECRET)
    install(Authentication) {
        jwt("jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                val tenantId = credential.payload.getClaim("tenantId").asString()
                val blockchainEnabled = credential.payload.getClaim("blockchainEnabled").asBoolean() ?: false
                if (tenantId != null && blockchainEnabled) {
                    JWTPrincipal(credential.payload)
                } else {
                    null  // 401 si el plan no tiene blockchain habilitado
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido o plan sin acceso blockchain"))
            }
        }
    }

    // ── Status pages ───────────────────────────────────────────────────────
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
        }
        exception<Throwable> { call, cause ->
            logger.error("Error no controlado", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error interno del servidor"))
        }
    }

    // ── Routing ────────────────────────────────────────────────────────────
    routing {
        // Health check — sin auth, para Docker y Caddy
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "guardianos-blockchain"))
        }

        // Rutas protegidas — se registrarán aquí en Sprint 1
        authenticate("jwt") {
            // blockchainRoutes()  ← se añade en Sprint 1
        }
    }

    logger.info("✅ GuardianOS Blockchain backend iniciado en puerto 8081")
}
