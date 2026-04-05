package es.guardianos.blockchain.db

import es.guardianos.blockchain.model.AuditListItem
import es.guardianos.blockchain.model.BlockchainReport
import es.guardianos.blockchain.model.BlockchainStats
import es.guardianos.blockchain.model.BlockchainTarget
import es.guardianos.blockchain.model.RiskDistribution
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class BlockchainRepository {

    // ── Targets ───────────────────────────────────────────────────────────────

    fun findOrCreateTarget(tenantId: String, address: String, chainId: String, label: String?): BlockchainTarget {
        val addrLower = address.lowercase()
        val existing = BlockchainTargets.selectAll()
            .where {
                (BlockchainTargets.tenantId eq UUID.fromString(tenantId)) and
                (BlockchainTargets.address eq addrLower) and
                (BlockchainTargets.chainId eq chainId)
            }.firstOrNull()

        if (existing != null) {
            return rowToTarget(existing)
        }

        val newId = UUID.randomUUID()
        val now = Instant.now().toString()
        BlockchainTargets.insert {
            it[id]        = newId
            it[this.tenantId] = UUID.fromString(tenantId)
            it[this.address]  = addrLower
            it[this.chainId]  = chainId
            it[this.label]    = label
            it[createdAt] = now
            it[updatedAt] = now
        }
        return BlockchainTarget(newId.toString(), tenantId, addrLower, chainId, label, now)
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    fun insertReport(tenantId: String, targetId: String): String {
        val newId = UUID.randomUUID()
        val now   = Instant.now().toString()
        BlockchainReports.insert {
            it[id]              = newId
            it[this.tenantId]   = UUID.fromString(tenantId)
            it[this.targetId]   = UUID.fromString(targetId)
            it[status]          = "running"
            it[startedAt]       = now
            it[createdAt]       = now
            it[updatedAt]       = now
        }
        return newId.toString()
    }

    fun updateReportCompleted(report: BlockchainReport) {
        val now = Instant.now().toString()
        BlockchainReports.update({ BlockchainReports.id eq UUID.fromString(report.id) }) {
            it[status]        = "completed"
            it[overallScore]  = report.overallScore
            it[riskLevel]     = report.riskLevel
            it[findingsCount] = report.findingsCount
            it[reportJson]    = json.encodeToString(report)
            it[tokenName]     = report.tokenName
            it[tokenSymbol]   = report.tokenSymbol
            it[totalSupply]   = report.totalSupply
            it[holderCount]   = report.holderCount
            it[chain]         = report.chainId
            it[completedAt]   = now
            it[updatedAt]     = now
        }
    }

    fun updateReportFailed(reportId: String, reason: String) {
        val now = Instant.now().toString()
        BlockchainReports.update({ BlockchainReports.id eq UUID.fromString(reportId) }) {
            it[status]     = "failed"
            it[reportJson] = """{"error":"$reason"}"""
            it[completedAt] = now
            it[updatedAt]   = now
        }
    }

    fun findReportById(reportId: String, tenantId: String): BlockchainReport? {
        val row = BlockchainReports.join(BlockchainTargets, JoinType.LEFT,
            onColumn = BlockchainReports.targetId,
            otherColumn = BlockchainTargets.id
        ).selectAll().where {
            (BlockchainReports.id eq UUID.fromString(reportId)) and
            (BlockchainReports.tenantId eq UUID.fromString(tenantId))
        }.firstOrNull() ?: return null

        val stored = row[BlockchainReports.reportJson]
        if (stored != null && row[BlockchainReports.status] == "completed") {
            return try { json.decodeFromString<BlockchainReport>(stored) } catch (_: Exception) { rowToReport(row) }
        }
        return rowToReport(row)
    }

    fun listReports(tenantId: String, limit: Int = 50): List<AuditListItem> {
        return BlockchainReports.join(BlockchainTargets, JoinType.LEFT,
            onColumn = BlockchainReports.targetId,
            otherColumn = BlockchainTargets.id
        ).selectAll().where {
            BlockchainReports.tenantId eq UUID.fromString(tenantId)
        }.orderBy(BlockchainReports.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AuditListItem(
                    id           = row[BlockchainReports.id].toString(),
                    address      = row[BlockchainTargets.address],
                    chainId      = row[BlockchainTargets.chainId],
                    label        = row[BlockchainTargets.label],
                    tokenName    = row[BlockchainReports.tokenName],
                    tokenSymbol  = row[BlockchainReports.tokenSymbol],
                    status       = row[BlockchainReports.status],
                    overallScore = row[BlockchainReports.overallScore] ?: 0,
                    riskLevel    = row[BlockchainReports.riskLevel] ?: "UNKNOWN",
                    findingsCount = row[BlockchainReports.findingsCount],
                    createdAt    = row[BlockchainReports.createdAt],
                    completedAt  = row[BlockchainReports.completedAt]
                )
            }
    }

    // ── Estadísticas ─────────────────────────────────────────────────────────

    fun getStats(tenantId: String): BlockchainStats {
        val rows = BlockchainReports.join(BlockchainTargets, JoinType.LEFT,
            onColumn = BlockchainReports.targetId, otherColumn = BlockchainTargets.id
        ).selectAll().where { BlockchainReports.tenantId eq UUID.fromString(tenantId) }.toList()

        val completed = rows.filter { it[BlockchainReports.status] == "completed" }
        val scores    = completed.mapNotNull { it[BlockchainReports.overallScore] }

        val riskDist = RiskDistribution(
            CRITICAL = completed.count { it[BlockchainReports.riskLevel] == "CRITICAL" },
            HIGH     = completed.count { it[BlockchainReports.riskLevel] == "HIGH"     },
            MEDIUM   = completed.count { it[BlockchainReports.riskLevel] == "MEDIUM"   },
            LOW      = completed.count { it[BlockchainReports.riskLevel] == "LOW"      },
            INFO     = completed.count { it[BlockchainReports.riskLevel] == "INFO"     }
        )

        val chainDist = rows.mapNotNull { it[BlockchainTargets.chainId] }
            .groupingBy { it }.eachCount()

        return BlockchainStats(
            totalAudits       = rows.size,
            completedAudits   = completed.size,
            failedAudits      = rows.count { it[BlockchainReports.status] == "failed" },
            runningAudits     = rows.count { it[BlockchainReports.status] in listOf("running", "pending") },
            avgScore          = if (scores.isEmpty()) null else scores.average(),
            riskDistribution  = riskDist,
            chainDistribution = chainDist
        )
    }

    // ── Re-auditoría ──────────────────────────────────────────────────────────

    fun resetReportForReaudit(reportId: String, tenantId: String): Boolean {
        val now = Instant.now().toString()
        val updated = BlockchainReports.update({
            (BlockchainReports.id eq UUID.fromString(reportId)) and
            (BlockchainReports.tenantId eq UUID.fromString(tenantId))
        }) {
            it[status]        = "running"
            it[overallScore]  = null
            it[riskLevel]     = null
            it[findingsCount] = 0
            it[reportJson]    = null
            it[tokenName]     = null
            it[tokenSymbol]   = null
            it[totalSupply]   = null
            it[holderCount]   = null
            it[startedAt]     = now
            it[completedAt]   = null
            it[updatedAt]     = now
        }
        return updated > 0
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun rowToTarget(row: ResultRow) = BlockchainTarget(
        id        = row[BlockchainTargets.id].toString(),
        tenantId  = row[BlockchainTargets.tenantId].toString(),
        address   = row[BlockchainTargets.address],
        chainId   = row[BlockchainTargets.chainId],
        label     = row[BlockchainTargets.label],
        createdAt = row[BlockchainTargets.createdAt]
    )

    private fun rowToReport(row: ResultRow) = BlockchainReport(
        id           = row[BlockchainReports.id].toString(),
        tenantId     = row[BlockchainReports.tenantId].toString(),
        targetId     = row[BlockchainReports.targetId].toString(),
        address      = row[BlockchainTargets.address],
        chainId      = row[BlockchainTargets.chainId],
        label        = row[BlockchainTargets.label],
        tokenName    = row[BlockchainReports.tokenName],
        tokenSymbol  = row[BlockchainReports.tokenSymbol],
        totalSupply  = row[BlockchainReports.totalSupply],
        holderCount  = row[BlockchainReports.holderCount],
        status       = row[BlockchainReports.status],
        overallScore = row[BlockchainReports.overallScore] ?: 0,
        riskLevel    = row[BlockchainReports.riskLevel] ?: "UNKNOWN",
        findings     = emptyList(),
        findingsCount = row[BlockchainReports.findingsCount],
        startedAt    = row[BlockchainReports.startedAt],
        completedAt  = row[BlockchainReports.completedAt],
        createdAt    = row[BlockchainReports.createdAt]
    )
}
