package es.guardianos.blockchain.scanner

import es.guardianos.blockchain.model.BlockchainFinding
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.Instant

private val log = LoggerFactory.getLogger("LiquidityLockScanner")

private val CHAIN_IDS = mapOf(
    "ethereum"  to "1",
    "bsc"       to "56",
    "polygon"   to "137",
    "arbitrum"  to "42161",
    "optimism"  to "10",
    "avalanche" to "43114"
)

// ── Modelos GoPlus token_security (campos LP) ────────────────────────────────

@Serializable
private data class GoPlusLPResponse(
    val code: Int = 0,
    val result: JsonObject = JsonObject(emptyMap())
)

/**
 * Scanner de liquidez bloqueada.
 *
 * Usa GoPlus token_security (misma API ya usada por GoPlusSecurityScanner, llamada
 * independiente para no acoplar los scanners) y extrae los campos LP:
 *   - lp_holders: quién tiene LP tokens y si están bloqueados
 *   - locked_detail: fechas de desbloqueo y cantidades
 *   - dex: info del pool (nombre, liquidez en USD)
 *
 * Findings generados:
 *   BC-LIQ-003         MEDIUM — no hay pool DEX detectado
 *   BC-LIQ-NOLOCK      HIGH   — 0% del LP bloqueado (riesgo rug pull)
 *   BC-LIQ-PARTIAL     MEDIUM — entre 1-94% bloqueado
 *   BC-LIQ-LOCK-OK     INFO   — ≥95% del LP bloqueado (señal positiva)
 *   BC-LIQ-EXPIRE      MEDIUM — el lock expira en menos de 30 días
 */
class LiquidityLockScanner(private val httpClient: HttpClient) {

    suspend fun scan(address: String, chainId: String): List<BlockchainFinding> {
        val goPlusChainId = CHAIN_IDS[chainId.lowercase()] ?: return emptyList()

        return try {
            val respRaw: GoPlusLPResponse = httpClient.get(
                "https://api.gopluslabs.io/api/v1/token_security/$goPlusChainId?contract_addresses=$address"
            ).body()

            if (respRaw.code != 1) return emptyList()

            // GoPlus devuelve el resultado con la dirección en minúsculas como clave
            val data = respRaw.result[address.lowercase()]?.jsonObject
                ?: respRaw.result.values.firstOrNull()?.jsonObject
                ?: return emptyList()

            val findings = mutableListOf<BlockchainFinding>()

            // ── 1. ¿Tiene pool en DEX? ───────────────────────────────────────
            val isInDex = data["is_in_dex"]?.jsonPrimitive?.contentOrNull
            val dexArray = data["dex"]?.jsonArray ?: JsonArray(emptyList())

            if (isInDex != "1" || dexArray.isEmpty()) {
                findings += BlockchainFinding(
                    id = "BC-LIQ-003", risk = "MEDIUM",
                    title = "No se detectó pool de liquidez en DEXes",
                    description = "El contrato no aparece listado en ningún exchange descentralizado conocido. Puede que aún no tenga liquidez pública o que opere en un DEX no indexado por GoPlus.",
                    recommendation = "Verificar si el token tiene liquidez activa antes de interactuar. Sin pool es imposible comprar/vender libremente.",
                    evidence = "GoPlus is_in_dex=${isInDex ?: "null"}",
                    category = "LIQUIDITY"
                )
                return findings
            }

            val dexNames = dexArray.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            }.take(2).joinToString(", ").ifBlank { "DEX desconocido" }

            val liquidityUsd = dexArray.mapNotNull {
                it.jsonObject["liquidity"]?.jsonPrimitive?.doubleOrNull
            }.sum()
            val liquidityStr = if (liquidityUsd > 0) "\$%,.0f".format(liquidityUsd) else "desconocida"

            // ── 2. Analizar LP holders ────────────────────────────────────────
            val lpHolders = data["lp_holders"]?.jsonArray ?: JsonArray(emptyList())
            if (lpHolders.isEmpty()) return findings

            val lockedPercent = lpHolders.sumOf { holder ->
                val isLocked = holder.jsonObject["is_locked"]?.jsonPrimitive?.contentOrNull
                val pct = holder.jsonObject["percent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                if (isLocked == "1") pct else 0.0
            }

            // ── 3. Fecha de desbloqueo más próxima ────────────────────────────
            val nowEpoch = Instant.now().epochSecond
            val earliestUnlock: Long? = lpHolders
                .filter { it.jsonObject["is_locked"]?.jsonPrimitive?.contentOrNull == "1" }
                .flatMap { holder ->
                    holder.jsonObject["locked_detail"]?.jsonArray?.mapNotNull { detail ->
                        detail.jsonObject["end_time"]?.jsonPrimitive?.longOrNull
                    } ?: emptyList()
                }
                .filter { it > nowEpoch }
                .minOrNull()

            val daysUntilUnlock = earliestUnlock?.let { (it - nowEpoch) / 86400 }

            // ── 4. Generar finding principal según porcentaje bloqueado ───────
            when {
                lockedPercent >= 95.0 -> {
                    val unlockNote = when {
                        daysUntilUnlock != null && daysUntilUnlock > 0 ->
                            " El lock expira en ~$daysUntilUnlock días."
                        daysUntilUnlock != null && daysUntilUnlock <= 0 ->
                            " ⚠️ El lock ha expirado recientemente."
                        else -> ""
                    }
                    findings += BlockchainFinding(
                        id = "BC-LIQ-LOCK-OK", risk = "INFO",
                        title = "Liquidez bloqueada (${lockedPercent.toInt()}% del LP)",
                        description = "El ${"%,.1f".format(lockedPercent)}% de los tokens LP están bloqueados en un contrato de custodia reconocido, lo que reduce significativamente el riesgo de rug pull.$unlockNote",
                        recommendation = "Verificar que la fecha de desbloqueo sea razonable (mínimo 6 meses) y que el equipo tenga plan de renovación.",
                        evidence = "GoPlus lp_holders: locked=${lockedPercent.toInt()}% — DEX: $dexNames — Liquidez: $liquidityStr",
                        category = "LIQUIDITY"
                    )
                }

                lockedPercent >= 1.0 -> {
                    findings += BlockchainFinding(
                        id = "BC-LIQ-PARTIAL", risk = "MEDIUM",
                        title = "Liquidez parcialmente bloqueada (${lockedPercent.toInt()}%)",
                        description = "Solo el ${"%,.1f".format(lockedPercent)}% del LP está bloqueado. El ${"%,.1f".format(100.0 - lockedPercent)}% restante puede retirarse en cualquier momento, lo que expone a un partial rug pull.",
                        recommendation = "Pedir al equipo que bloquee el 100% de la liquidez. Un bloqueo parcial no ofrece garantías suficientes.",
                        evidence = "GoPlus lp_holders: locked=${lockedPercent.toInt()}% — DEX: $dexNames — Liquidez: $liquidityStr",
                        category = "LIQUIDITY"
                    )
                }

                else -> {
                    findings += BlockchainFinding(
                        id = "BC-LIQ-NOLOCK", risk = "HIGH",
                        title = "Liquidez NO bloqueada — riesgo de rug pull",
                        description = "Los tokens LP no están bloqueados en ningún contrato de custodia. El equipo puede retirar toda la liquidez del pool en una sola transacción, dejando el token sin valor (rug pull clásico).",
                        recommendation = "No invertir hasta que el equipo bloquee la liquidez en Team.Finance, Unicrypt o PinkLock durante un mínimo de 6-12 meses y lo publique públicamente.",
                        evidence = "GoPlus lp_holders: locked=0% — DEX: $dexNames — Liquidez: $liquidityStr",
                        category = "LIQUIDITY"
                    )
                }
            }

            // ── 5. Alerta si el lock expira pronto ────────────────────────────
            if (lockedPercent > 0 && daysUntilUnlock != null && daysUntilUnlock in 1..30) {
                findings += BlockchainFinding(
                    id = "BC-LIQ-EXPIRE", risk = "MEDIUM",
                    title = "El bloqueo de liquidez expira en $daysUntilUnlock días",
                    description = "El lock de liquidez activo vence pronto. Tras el vencimiento, el equipo podrá retirar libremente los tokens LP sin ningún impedimento técnico.",
                    recommendation = "Verificar si el equipo tiene previsto renovar el lock antes del vencimiento. Un lock expirado equivale a liquidez no bloqueada.",
                    evidence = "Fecha de desbloqueo: ${Instant.ofEpochSecond(earliestUnlock!!)}",
                    category = "LIQUIDITY"
                )
            }

            log.info("LiquidityLockScanner $address: locked=${lockedPercent.toInt()}% dex=$dexNames findings=${findings.size}")
            findings

        } catch (e: Exception) {
            log.warn("LiquidityLockScanner error para $address: ${e.message}")
            emptyList()
        }
    }
}
