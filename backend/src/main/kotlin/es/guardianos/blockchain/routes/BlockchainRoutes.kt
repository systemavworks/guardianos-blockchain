package es.guardianos.blockchain.routes

import es.guardianos.blockchain.model.ErrorResponse
import es.guardianos.blockchain.model.NewAuditRequest
import es.guardianos.blockchain.service.BlockchainAuditService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.blockchainRoutes(auditService: BlockchainAuditService) {

    // ── Listado de auditorías del tenant ──────────────────────────────────────
    get("/api/v1/blockchain/audits") {
        val tenantId = call.principal<JWTPrincipal>()!!.payload.getClaim("tenantId").asString()
        val list = auditService.listAudits(tenantId)
        call.respond(list)
    }

    // ── Lanzar nueva auditoría ────────────────────────────────────────────────
    post("/api/v1/blockchain/audits") {
        val tenantId = call.principal<JWTPrincipal>()!!.payload.getClaim("tenantId").asString()
        val req = try { call.receive<NewAuditRequest>() }
                  catch (e: Exception) {
                      return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Body inválido: ${e.message}"))
                  }

        return@post try {
            val reportId = auditService.startAudit(tenantId, req.address, req.chainId, req.label)
            call.respond(HttpStatusCode.Created, mapOf("reportId" to reportId, "status" to "running"))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Dirección inválida"))
        }
    }

    // ── Obtener un reporte ────────────────────────────────────────────────────
    get("/api/v1/blockchain/audits/{reportId}") {
        val tenantId = call.principal<JWTPrincipal>()!!.payload.getClaim("tenantId").asString()
        val reportId = call.parameters["reportId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("reportId requerido"))

        val report = auditService.getReport(reportId, tenantId)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Reporte no encontrado"))

        call.respond(report)
    }

    // ── Re-auditoría ──────────────────────────────────────────────────────────
    post("/api/v1/blockchain/audits/{reportId}/reaudit") {
        val tenantId = call.principal<JWTPrincipal>()!!.payload.getClaim("tenantId").asString()
        val reportId = call.parameters["reportId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("reportId requerido"))

        val ok = auditService.reaudit(reportId, tenantId)
        if (ok) call.respond(mapOf("reportId" to reportId, "status" to "running"))
        else    call.respond(HttpStatusCode.NotFound, ErrorResponse("Reporte no encontrado"))
    }

    // ── Estadísticas del tenant ───────────────────────────────────────────────
    get("/api/v1/blockchain/stats") {
        val tenantId = call.principal<JWTPrincipal>()!!.payload.getClaim("tenantId").asString()
        call.respond(auditService.getStats(tenantId))
    }
}
