package es.guardianos.blockchain.db

import org.jetbrains.exposed.sql.Table

object BlockchainTargets : Table("blockchain.blockchain_targets") {
    val id         = uuid("id")
    val tenantId   = uuid("tenant_id")
    val address    = text("address")
    val chainId    = text("chain_id").default("ethereum")
    val label      = text("label").nullable()
    val createdAt  = text("created_at")
    val updatedAt  = text("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object BlockchainReports : Table("blockchain.blockchain_reports") {
    val id           = uuid("id")
    val tenantId     = uuid("tenant_id")
    val targetId     = uuid("target_id").references(BlockchainTargets.id)
    val status       = text("status").default("pending")
    val reportJson   = text("report_json").nullable()
    val findingsCount = integer("findings_count").default(0)
    val overallScore = integer("overall_score").nullable()
    val riskLevel    = text("risk_level").nullable()
    val tokenName    = text("token_name").nullable()
    val tokenSymbol  = text("token_symbol").nullable()
    val totalSupply  = text("total_supply").nullable()
    val holderCount  = text("holder_count").nullable()
    val chain        = text("chain").nullable()
    val startedAt    = text("started_at").nullable()
    val completedAt  = text("completed_at").nullable()
    val createdAt    = text("created_at")
    val updatedAt    = text("updated_at")
    override val primaryKey = PrimaryKey(id)
}
