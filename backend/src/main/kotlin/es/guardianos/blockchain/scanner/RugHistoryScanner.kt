package es.guardianos.blockchain.scanner

import es.guardianos.blockchain.model.BlockchainFinding
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

private val log = LoggerFactory.getLogger("RugHistoryScanner")

private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }

// ── Modelos GoPlus address security ──────────────────────────────────────────

@Serializable
private data class GoPlusAddressSecurityResponse(
    val code: Int = 0,
    val message: String = "",
    val result: GoPlusAddressRisk? = null
)

@Serializable
private data class GoPlusAddressRisk(
    val cybercrime:              String? = null,
    val money_laundering:        String? = null,
    val number_of_malicious_contracts_created: String? = null,
    val financial_crime:         String? = null,
    val darkweb_transactions:    String? = null,
    val reinit:                  String? = null,
    val phishing_activities:     String? = null,
    val fake_kyc:                String? = null,
    val blackmail_activities:    String? = null,
    val stealing_attack:         String? = null,
    val sanctioned:              String? = null,
    val malicious_mining_activities: String? = null,
    val mixer:                   String? = null,
    val honeypot_related_address: String? = null
)

// ── Cache del listado de phishing de MetaMask ─────────────────────────────────

private const val METAMASK_URL =
    "https://raw.githubusercontent.com/MetaMask/eth-phishing-detect/master/src/config.json"

private data class PhishingCache(
    val domains: Set<String>,
    val mark: TimeSource.Monotonic.ValueTimeMark
)

private val phishingCache = AtomicReference<PhishingCache?>(null)

@Serializable
private data class MetaMaskConfig(
    val blacklist: List<String> = emptyList(),
    val fuzzylist: List<String> = emptyList()
)

/**
 * Scanner de historial de contratos/creadores problemáticos.
 *
 * Fuentes (gratuitas, sin API key):
 *   1. GoPlus Address Security API — cubre cybercrime, phishing, money laundering, DArkweb…
 *   2. MetaMask eth-phishing-detect — lista de dominios de scam (~2 MB, TTL 1h en memoria)
 *
 * Costo por auditoría: 1 llamada HTTP extra, ~<50ms latencia adicional.
 */
class RugHistoryScanner(private val httpClient: HttpClient) {

    suspend fun scan(address: String, chainId: String): List<BlockchainFinding> {
        val findings = mutableListOf<BlockchainFinding>()

        findings += checkGoPlusAddressSecurity(address, chainId)
        findings += checkPhishingListDeployer(address)

        return findings
    }

    // ── GoPlus Address Security ───────────────────────────────────────────────

    private suspend fun checkGoPlusAddressSecurity(
        address: String,
        chainId: String
    ): List<BlockchainFinding> {
        val goPlusChainId = CHAIN_IDS[chainId] ?: return emptyList()
        val findings = mutableListOf<BlockchainFinding>()

        return try {
            val resp = httpClient.get(
                "https://api.gopluslabs.io/api/v1/address_security/$address?chain_id=$goPlusChainId"
            ).body<GoPlusAddressSecurityResponse>()

            val risk = resp.result ?: return emptyList()

            if (risk.cybercrime == "1" || risk.stealing_attack == "1") {
                findings += BlockchainFinding(
                    id = "BC-RUG-001", risk = "CRITICAL",
                    title = "Dirección del contrato asociada a cibercrimen",
                    description = "GoPlus clasifica esta dirección como involucrada en actividades de cibercrimen o ataques de robo. Altísimo riesgo.",
                    recommendation = "Evitar completamente cualquier interacción con este contrato.",
                    evidence = "GoPlus address_security: cybercrime=${risk.cybercrime} stealing=${risk.stealing_attack}",
                    category = "RUG_HISTORY"
                )
            }

            if (risk.phishing_activities == "1") {
                findings += BlockchainFinding(
                    id = "BC-RUG-002", risk = "CRITICAL",
                    title = "Dirección con historial de phishing",
                    description = "Esta dirección está catalogada en bases de datos públicas como usada en campañas de phishing.",
                    recommendation = "No interactuar con este contrato ni firmar transacciones que lo involucren.",
                    evidence = "GoPlus address_security: phishing=1",
                    category = "RUG_HISTORY"
                )
            }

            if (risk.money_laundering == "1" || risk.darkweb_transactions == "1") {
                findings += BlockchainFinding(
                    id = "BC-RUG-003", risk = "HIGH",
                    title = "Dirección relacionada con blanqueo o darkweb",
                    description = "La dirección del contrato aparece en registros de transacciones con entidades de darkweb o blanqueo de capitales.",
                    recommendation = "Alto riesgo regulatorio y reputacional. Evitar interacción.",
                    evidence = "GoPlus: money_laundering=${risk.money_laundering} darkweb=${risk.darkweb_transactions}",
                    category = "RUG_HISTORY"
                )
            }

            if (risk.honeypot_related_address == "1") {
                findings += BlockchainFinding(
                    id = "BC-RUG-004", risk = "HIGH",
                    title = "Dirección relacionada con honeypots anteriores",
                    description = "Esta dirección ha sido asociada previamente con contratos honeypot, lo que indica un patrón de fraude sistemático.",
                    recommendation = "El creador o esta dirección tiene antecedentes de honeypots. No invertir.",
                    evidence = "GoPlus: honeypot_related_address=1",
                    category = "RUG_HISTORY"
                )
            }

            if (risk.blackmail_activities == "1" || risk.financial_crime == "1") {
                findings += BlockchainFinding(
                    id = "BC-RUG-005", risk = "HIGH",
                    title = "Actividad ilegal registrada en esta dirección",
                    description = "Actividad de extorsión o crimen financiero detectada por GoPlus en esta dirección.",
                    recommendation = "Evitar cualquier tipo de interacción financiera con este contrato.",
                    evidence = "GoPlus: blackmail=${risk.blackmail_activities} financial_crime=${risk.financial_crime}",
                    category = "RUG_HISTORY"
                )
            }

            if (risk.sanctioned == "1") {
                findings += BlockchainFinding(
                    id = "BC-RUG-006", risk = "CRITICAL",
                    title = "Dirección en lista de sanciones internacionales",
                    description = "Esta dirección está en la OFAC Specially Designated Nationals list u otras listas de sanciones internacionales.",
                    recommendation = "Interactuar con este contrato puede tener consecuencias legales graves según tu jurisdicción.",
                    evidence = "GoPlus: sanctioned=1",
                    category = "RUG_HISTORY"
                )
            }

            log.debug("RugHistoryScanner (GoPlus) $address: ${findings.size} finding(s)")
            findings

        } catch (e: Exception) {
            log.warn("RugHistoryScanner (GoPlus) error para $address: ${e.message}")
            emptyList()
        }
    }

    // ── MetaMask phishing-detect list ─────────────────────────────────────────

    private suspend fun checkPhishingListDeployer(address: String): List<BlockchainFinding> {
        // La lista de MetaMask contiene dominios de scam, no direcciones.
        // La usamos para verificar si la dirección del contrato aparece literalmente
        // en alguna entrada del blacklist (algunos proyectos scam incluyen su contrato).
        return try {
            val cache = getOrRefreshPhishingCache() ?: return emptyList()
            val addrLower = address.lowercase()
            if (cache.domains.any { it.contains(addrLower) }) {
                listOf(BlockchainFinding(
                    id = "BC-RUG-007", risk = "CRITICAL",
                    title = "Dirección en lista de phishing de MetaMask",
                    description = "La dirección figura en la base de datos pública eth-phishing-detect de MetaMask, " +
                        "utilizada para proteger a millones de usuarios de contratos fraudulentos.",
                    recommendation = "No interactuar bajo ningún concepto.",
                    evidence = "MetaMask eth-phishing-detect blacklist",
                    category = "RUG_HISTORY"
                ))
            } else emptyList()
        } catch (e: Exception) {
            log.debug("RugHistoryScanner (MetaMask list) no disponible: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getOrRefreshPhishingCache(): PhishingCache? {
        val now = TimeSource.Monotonic.markNow()
        val current = phishingCache.get()
        if (current != null && (now - current.mark) < 1.hours) return current

        return try {
            val raw = httpClient.get(METAMASK_URL).body<String>()
            val config = jsonParser.decodeFromString<MetaMaskConfig>(raw)
            val domains = (config.blacklist + config.fuzzylist).toHashSet()
            val fresh = PhishingCache(domains, TimeSource.Monotonic.markNow())
            phishingCache.set(fresh)
            log.info("RugHistoryScanner: cache MetaMask actualizado — ${domains.size} entradas")
            fresh
        } catch (e: Exception) {
            log.warn("RugHistoryScanner: no se pudo descargar MetaMask phishing list: ${e.message}")
            null
        }
    }

    companion object {
        private val CHAIN_IDS = mapOf(
            "ethereum"  to "1",
            "bsc"       to "56",
            "polygon"   to "137",
            "arbitrum"  to "42161",
            "optimism"  to "10",
            "avalanche" to "43114"
        )
    }
}
