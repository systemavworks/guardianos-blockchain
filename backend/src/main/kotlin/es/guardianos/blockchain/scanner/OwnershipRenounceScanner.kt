package es.guardianos.blockchain.scanner

import es.guardianos.blockchain.model.BlockchainFinding
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("OwnershipRenounceScanner")

// Selector de la función owner() en ABI → keccak256("owner()")[0..3]
private const val OWNER_SELECTOR = "0x8da5cb5b"

// Direcciones que implican ownership renunciado
private val DEAD_ADDRESSES = setOf(
    "0x0000000000000000000000000000000000000000",
    "0x000000000000000000000000000000000000dead",
    "0xdead000000000000000042069420694206942069"
)

// RPCs públicas gratuitas por chain name
private val FREE_RPCS = mapOf(
    "ethereum"  to "https://ethereum.publicnode.com",
    "bsc"       to "https://bsc-dataseed.binance.org",
    "polygon"   to "https://polygon-rpc.com",
    "arbitrum"  to "https://arb1.arbitrum.io/rpc",
    "optimism"  to "https://mainnet.optimism.io",
    "avalanche" to "https://api.avax.network/ext/bc/C/rpc"
)

@Serializable
private data class RpcRequest(
    val jsonrpc: String = "2.0",
    val method:  String,
    val params:  JsonArray,
    val id:      Int = 1
)

/**
 * Verifica on-chain si el contrato tiene ownership renunciado.
 * Una sola llamada JSON-RPC a nodo público gratuito.
 *
 * Findings:
 *   BC-OWN-OK  → INFO   owner renunciado (buena señal)
 *   BC-OWN-001 → MEDIUM owner no renunciado en contrato potencialmente actualizable
 */
class OwnershipRenounceScanner(private val httpClient: HttpClient) {

    suspend fun scan(address: String, chainId: String): List<BlockchainFinding> {
        val rpcUrl = FREE_RPCS[chainId.lowercase()] ?: return emptyList()

        return try {
            val ownerAddress = callOwner(address, rpcUrl) ?: return emptyList()

            log.debug("OwnershipRenounceScanner $address: owner=$ownerAddress")

            if (ownerAddress in DEAD_ADDRESSES) {
                listOf(BlockchainFinding(
                    id = "BC-OWN-OK", risk = "INFO",
                    title = "Ownership renunciado (propietario = dirección quemada)",
                    description = "El contrato ha renunciado a la propiedad. La función owner() devuelve la dirección cero o una dirección quemada, lo que indica que ningún wallet puede ejercer privilegios de administrador.",
                    recommendation = "Verificar que las funciones críticas realmente requieren onlyOwner y que no existen puertas traseras mediante proxies o timelocks.",
                    evidence = "on-chain owner()=$ownerAddress",
                    category = "OWNERSHIP"
                ))
            } else {
                // Solo generamos finding negativo si el owner es no-renunciado
                // Los hallazgos de ownership modificable ya los cubre GoPlus.
                // Aquí añadimos un finding informativo con la dirección real del owner.
                listOf(BlockchainFinding(
                    id = "BC-OWN-ACTIVE", risk = "LOW",
                    title = "Propietario activo en el contrato",
                    description = "La función owner() responde con una dirección activa ($ownerAddress). El propietario actual tiene la capacidad de ejecutar funciones privilegiadas según el código del contrato.",
                    recommendation = "Investigar quién controla esa wallet y si existe un timelock o multisig. La ausencia de renuncio no implica scam, pero requiere confianza en el equipo.",
                    evidence = "on-chain owner()=$ownerAddress",
                    category = "OWNERSHIP"
                ))
            }
        } catch (e: Exception) {
            log.debug("OwnershipRenounceScanner: $address no responde a owner() o RPC no disponible: ${e.message}")
            emptyList() // Muchos contratos no tienen owner(), es graceful
        }
    }

    private suspend fun callOwner(address: String, rpcUrl: String): String? {
        val body = Json.encodeToString(
            RpcRequest.serializer(),
            RpcRequest(
                method = "eth_call",
                params = buildJsonArray {
                    addJsonObject {
                        put("to", address)
                        put("data", OWNER_SELECTOR)
                    }
                    add(JsonPrimitive("latest"))
                }
            )
        )

        val response = httpClient.post(rpcUrl) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()

        val json = Json.parseToJsonElement(response).jsonObject
        val result = json["result"]?.jsonPrimitive?.contentOrNull ?: return null

        // El resultado es un bytes32 hex: 0x + 24 ceros + 40 hex chars de dirección
        if (result.length < 66) return null
        val rawAddress = "0x" + result.takeLast(40)
        return rawAddress.lowercase()
    }
}
