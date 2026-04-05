package es.guardianos.blockchain.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

// ── Entidades del dominio ─────────────────────────────────────────────────────

@Serializable
data class BlockchainTarget(
    val id: String,
    val tenantId: String,
    val address: String,
    val chainId: String,
    val label: String? = null,
    val createdAt: String
)

@Serializable
data class BlockchainFinding(
    val id: String,
    val risk: String,           // CRITICAL | HIGH | MEDIUM | LOW | INFO
    val title: String,
    val description: String,
    val recommendation: String,
    val evidence: String? = null,
    val category: String        // HONEYPOT | OWNERSHIP | PROXY | CONCENTRATION | VERIFICATION | TRADING | AGE
)

@Serializable
data class BlockchainReport(
    val id: String,
    val tenantId: String,
    val targetId: String,
    val address: String,
    val chainId: String,
    val label: String? = null,
    val tokenName: String? = null,
    val tokenSymbol: String? = null,
    val totalSupply: String? = null,
    val holderCount: String? = null,
    val status: String,         // pending | running | completed | failed
    val overallScore: Int,
    val riskLevel: String,
    val findings: List<BlockchainFinding>,
    val findingsCount: Int,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: String
)

// ── DTOs de API ───────────────────────────────────────────────────────────────

@Serializable
data class NewAuditRequest(
    val address: String,
    val chainId: String = "ethereum",
    val label: String? = null
)

@Serializable
data class AuditListItem(
    val id: String,
    val address: String,
    val chainId: String,
    val label: String? = null,
    val tokenName: String? = null,
    val tokenSymbol: String? = null,
    val status: String,
    val overallScore: Int,
    val riskLevel: String,
    val findingsCount: Int,
    val createdAt: String,
    val completedAt: String? = null
)

@Serializable
data class HandoffResponse(val token: String)

@Serializable
data class ErrorResponse(val error: String)

// ── Sprint 2: estadísticas globales ──────────────────────────────────────────

@Serializable
data class RiskDistribution(
    val CRITICAL: Int = 0,
    val HIGH:     Int = 0,
    val MEDIUM:   Int = 0,
    val LOW:      Int = 0,
    val INFO:     Int = 0
)

@Serializable
data class BlockchainStats(
    val totalAudits:     Int,
    val completedAudits: Int,
    val failedAudits:    Int,
    val runningAudits:   Int,
    val avgScore:        Double?,
    val riskDistribution: RiskDistribution,
    val chainDistribution: Map<String, Int>
)

// ── GoPlus API response models ────────────────────────────────────────────────

@Serializable
data class GoPlusResponse(
    val code: Int,
    val message: String,
    val result: Map<String, GoPlusTokenData> = emptyMap()
)

@Serializable
data class GoPlusTokenData(
    val token_name: String? = null,
    val token_symbol: String? = null,
    val total_supply: String? = null,
    val holder_count: String? = null,
    val is_honeypot: String? = null,
    val honeypot_with_same_creator: String? = null,
    val cannot_buy: String? = null,
    val cannot_sell_all: String? = null,
    val buy_tax: String? = null,
    val sell_tax: String? = null,
    val is_mintable: String? = null,
    val transfer_pausable: String? = null,
    val hidden_owner: String? = null,
    val can_take_back_ownership: String? = null,
    val owner_change_balance: String? = null,
    val selfdestruct: String? = null,
    val external_call: String? = null,
    val is_blacklisted: String? = null,
    val is_whitelisted: String? = null,
    val trading_cooldown: String? = null,
    val is_anti_whale: String? = null,
    val anti_whale_modifiable: String? = null,
    val is_open_source: String? = null,
    val is_proxy: String? = null,
    val is_in_dex: String? = null,
    val owner_address: String? = null,
    val owner_percent: String? = null,
    val creator_address: String? = null,
    val holders: List<GoPlusHolder>? = null,
    val lp_holders: List<GoPlusHolder>? = null,
    val dex: List<GoPlusDex>? = null,
    val lp_total_supply: String? = null,
    val note: String? = null,
    val other_potential_risks: String? = null,
    val trust_list: String? = null
)

@Serializable
data class GoPlusHolder(
    val address: String? = null,
    val balance: String? = null,
    val percent: String? = null,
    val is_contract: Int? = null,
    val is_locked: Int? = null,
    val locked_detail: List<@Contextual Any>? = null,
    val tag: String? = null
)

@Serializable
data class GoPlusDex(
    val name: String? = null,
    val liquidity: String? = null,
    val pair: String? = null
)
