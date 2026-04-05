package es.guardianos.blockchain.service

import es.guardianos.blockchain.db.BlockchainRepository
import es.guardianos.blockchain.model.*
import es.guardianos.blockchain.scanner.ContractAgeScanner
import es.guardianos.blockchain.scanner.EtherscanAbiScanner
import es.guardianos.blockchain.scanner.GoPlusSecurityScanner
import es.guardianos.blockchain.scanner.LiquidityLockScanner
import es.guardianos.blockchain.scanner.OwnershipRenounceScanner
import es.guardianos.blockchain.scanner.RugHistoryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant

private val log = LoggerFactory.getLogger("BlockchainAuditService")

class BlockchainAuditService(
    private val repo: BlockchainRepository,
    private val goPlusScanner: GoPlusSecurityScanner,
    private val ageScanner: ContractAgeScanner,
    private val rugScanner: RugHistoryScanner,
    private val ownershipScanner: OwnershipRenounceScanner,
    private val etherscanAbiScanner: EtherscanAbiScanner,
    private val liquidityScanner: LiquidityLockScanner
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Lanzar auditoría (async) ──────────────────────────────────────────────

    suspend fun startAudit(tenantId: String, address: String, chainId: String, label: String?): String {
        val addrNorm = address.lowercase().trim()
        require(addrNorm.matches(Regex("0x[0-9a-f]{40}"))) {
            "Dirección inválida: debe ser una dirección Ethereum válida (0x + 40 hex)"
        }

        // Upsert target
        val target = DatabaseFactory.dbQuery {
            repo.findOrCreateTarget(tenantId, addrNorm, chainId, label)
        }

        // Crear report en BD con status=running
        val reportId = DatabaseFactory.dbQuery {
            repo.insertReport(tenantId, target.id)
        }

        log.info("[$reportId] Auditoría blockchain iniciada: $addrNorm (chain=$chainId)")

        // Ejecutar scanners en background
        scope.launch {
            runAudit(reportId, tenantId, target, addrNorm, chainId)
        }

        return reportId
    }

    // ── Ejecución de scanners ─────────────────────────────────────────────────

    private suspend fun runAudit(
        reportId: String,
        tenantId: String,
        target: BlockchainTarget,
        address: String,
        chainId: String
    ) {
        try {
            log.info("[$reportId] Ejecutando scanners para $address")

            // 1. GoPlus (scanner principal — cubre honeypot, ownership, trading, proxy, holders)
            val goPlusFindings = goPlusScanner.scan(address, chainId)
            val tokenInfo      = goPlusScanner.getTokenInfo(address, chainId)

            // 2. Edad del contrato (requiere Etherscan key — graceful si no hay)
            val ageFindings    = ageScanner.scan(address, chainId)

            // 3. Historial de rug/phishing del deployer (GoPlus address_security + MetaMask list)
            val rugFindings    = rugScanner.scan(address, chainId)

            // 4. Ownership on-chain via RPC público gratuito
            val ownerFindings  = ownershipScanner.scan(address, chainId)

            // 5. ABI analysis — mint authority + tax changeability (Etherscan, 1 sola llamada)
            val abiFindings       = etherscanAbiScanner.scan(address, chainId)

            // 6. Liquidez bloqueada — GoPlus lp_holders
            val liquidityFindings = liquidityScanner.scan(address, chainId)

            val allFindings = (goPlusFindings + ageFindings + rugFindings + ownerFindings + abiFindings + liquidityFindings)
                .distinctBy { it.id }

            // 3. Scoring: 100 - Σ penalizaciones
            val score = calculateScore(allFindings)
            val risk  = scoreToRisk(score)

            val report = BlockchainReport(
                id           = reportId,
                tenantId     = tenantId,
                targetId     = target.id,
                address      = address,
                chainId      = chainId,
                label        = target.label,
                tokenName    = tokenInfo?.token_name,
                tokenSymbol  = tokenInfo?.token_symbol,
                totalSupply  = tokenInfo?.total_supply,
                holderCount  = tokenInfo?.holder_count,
                status       = "completed",
                overallScore = score,
                riskLevel    = risk,
                findings     = allFindings,
                findingsCount = allFindings.size,
                startedAt    = Instant.now().toString(),
                completedAt  = Instant.now().toString(),
                createdAt    = Instant.now().toString()
            )

            DatabaseFactory.dbQuery { repo.updateReportCompleted(report) }
            log.info("[$reportId] Completado: score=$score risk=$risk findings=${allFindings.size}")

        } catch (e: Exception) {
            log.error("[$reportId] Error en auditoría blockchain: ${e.message}", e)
            DatabaseFactory.dbQuery { repo.updateReportFailed(reportId, e.message ?: "Error desconocido") }
        }
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    private fun calculateScore(findings: List<BlockchainFinding>): Int {
        val penalty = findings.sumOf { f ->
            when (f.risk) {
                "CRITICAL" -> 25
                "HIGH"     -> 10
                "MEDIUM"   -> 5
                "LOW"      -> 1
                else       -> 0
            }.toLong()
        }.toInt()
        return maxOf(0, 100 - penalty)
    }

    private fun scoreToRisk(score: Int) = when {
        score <= 30 -> "CRITICAL"
        score <= 54 -> "HIGH"
        score <= 74 -> "MEDIUM"
        score <= 89 -> "LOW"
        else        -> "INFO"
    }

    // ── Consulta ──────────────────────────────────────────────────────────────

    suspend fun getReport(reportId: String, tenantId: String): BlockchainReport? =
        DatabaseFactory.dbQuery { repo.findReportById(reportId, tenantId) }

    suspend fun listAudits(tenantId: String): List<AuditListItem> =
        DatabaseFactory.dbQuery { repo.listReports(tenantId) }

    suspend fun getStats(tenantId: String): BlockchainStats =
        DatabaseFactory.dbQuery { repo.getStats(tenantId) }

    // ── Re-auditoría ──────────────────────────────────────────────────────────

    suspend fun reaudit(reportId: String, tenantId: String): Boolean {
        // Obtener target para saber address+chain
        val existing = DatabaseFactory.dbQuery { repo.findReportById(reportId, tenantId) }
            ?: return false

        val reset = DatabaseFactory.dbQuery { repo.resetReportForReaudit(reportId, tenantId) }
        if (!reset) return false

        log.info("[$reportId] Re-auditoría iniciada para ${existing.address} (${existing.chainId})")

        scope.launch {
            val fakeTarget = BlockchainTarget(
                id        = existing.targetId,
                tenantId  = tenantId,
                address   = existing.address,
                chainId   = existing.chainId,
                label     = existing.label,
                createdAt = existing.createdAt
            )
            runAudit(reportId, tenantId, fakeTarget, existing.address, existing.chainId)
        }
        return true
    }
}
