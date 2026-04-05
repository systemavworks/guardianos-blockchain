package es.guardianos.blockchain

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import es.guardianos.blockchain.db.BlockchainRepository
import es.guardianos.blockchain.routes.authRoutes
import es.guardianos.blockchain.routes.blockchainRoutes
import es.guardianos.blockchain.scanner.ContractAgeScanner
import es.guardianos.blockchain.scanner.EtherscanAbiScanner
import es.guardianos.blockchain.scanner.GoPlusSecurityScanner
import es.guardianos.blockchain.scanner.OwnershipRenounceScanner
import es.guardianos.blockchain.scanner.RugHistoryScanner
import es.guardianos.blockchain.service.BlockchainAuditService
import es.guardianos.blockchain.service.DatabaseFactory
import es.guardianos.blockchain.util.EtherscanClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
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
    val jwtSecret    = environment.config.property("auth.jwtSecret").getString()
    val issuer       = environment.config.property("auth.issuer").getString()
    val databaseUrl  = environment.config.property("database.url").getString()
    val etherscanKey = System.getenv("ETHERSCAN_API_KEY")

    // ── Base de datos ──────────────────────────────────────────────────────
    DatabaseFactory.init(databaseUrl)

    // ── HTTP client (para GoPlus y Etherscan) ─────────────────────────────
    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis  = 15_000
            connectTimeoutMillis  = 5_000
        }
    }

    // ── Servicios ─────────────────────────────────────────────────────────
    val repo          = BlockchainRepository()
    val goPlusScanner = GoPlusSecurityScanner(httpClient)
    val etherscanClient = if (!etherscanKey.isNullOrBlank())
        EtherscanClient.getInstance(etherscanKey, httpClient) else null
    val ageScanner       = ContractAgeScanner(etherscanClient)
    val rugScanner       = RugHistoryScanner(httpClient)
    val ownershipScanner = OwnershipRenounceScanner(httpClient)
    val etherscanAbiScanner = EtherscanAbiScanner(etherscanClient)
    val auditService  = BlockchainAuditService(repo, goPlusScanner, ageScanner, rugScanner, ownershipScanner, etherscanAbiScanner)

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
        allowHost("guardianos.es",            schemes = listOf("https"))
        allowHost("audit.guardianos.es",      schemes = listOf("https"))
        allowHost("blockchain.guardianos.es", schemes = listOf("https"))
        allowHost("localhost:5173",           schemes = listOf("http"))
        allowHost("localhost:5174",           schemes = listOf("http"))
    }

    // ── Auth JWT ───────────────────────────────────────────────────────────
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
                if (tenantId != null && blockchainEnabled) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized,
                    mapOf("error" to "Token inválido o plan sin acceso blockchain"))
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
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "guardianos-blockchain"))
        }

        // SSO handoff — sin auth JWT (valida manualmente el token de guardianos-audit)
        authRoutes(jwtSecret, issuer)

        // Rutas protegidas
        authenticate("jwt") {
            blockchainRoutes(auditService)
        }
    }

    logger.info("✅ GuardianOS Blockchain backend iniciado en puerto 8081")
}
