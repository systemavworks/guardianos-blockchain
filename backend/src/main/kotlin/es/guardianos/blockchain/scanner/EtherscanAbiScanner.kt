package es.guardianos.blockchain.scanner

import es.guardianos.blockchain.config.BlockchainChains
import es.guardianos.blockchain.model.BlockchainFinding
import es.guardianos.blockchain.util.EtherscanClient
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("EtherscanAbiScanner")

// Funciones típicas de mint abusivo (públicas/externas sin restricción obvia)
private val MINT_FUNCTIONS = setOf(
    "mint", "mintTokens", "mintTo", "createTokens", "issue",
    "generateTokens", "inflate", "_mint"
)

// Funciones que permiten cambiar impuestos/fees en tiempo de ejecución
private val TAX_FUNCTIONS = setOf(
    "setTax", "setFee", "setFees", "updateTax", "updateFee",
    "updateBuyTax", "updateSellTax", "setBuyTax", "setSellTax",
    "setTransferTax", "setMarketingFee", "setLiquidityFee",
    "changeTax", "changeFee", "setDevFee", "setRewardFee",
    "updateBuyFee", "updateSellFee", "setMaxWallet", "setMaxTxAmount"
)

// Función que parsea el ABI (JSON string de array) buscando nombres de función
private fun findFunctionsInAbi(abi: String, targets: Set<String>): List<String> {
    if (abi.isBlank() || abi == "Contract source code not verified") return emptyList()
    val found = mutableListOf<String>()
    for (fn in targets) {
        // Buscamos el nombre como string JSON dentro del ABI sin parsear el JSON completo
        if (abi.contains("\"$fn\"") || abi.contains("'$fn'")) {
            found += fn
        }
    }
    return found
}

/**
 * Scanner que hace UNA sola llamada a Etherscan getsourcecode y extrae:
 *   - MintAuthority: funciones mint() accesibles que no estén renunciadas
 *   - TaxChangeability: funciones para cambiar impuestos/fees en runtime
 *   - (refuerza) ContractVerification: código fuente verificado
 *
 * Requiere EtherscanClient (opcional — omite si no hay API key).
 */
class EtherscanAbiScanner(private val etherscanClient: EtherscanClient?) {

    suspend fun scan(address: String, chainId: String): List<BlockchainFinding> {
        if (etherscanClient == null) {
            log.debug("EtherscanAbiScanner: sin API key, omitiendo")
            return emptyList()
        }

        val chain = try { BlockchainChains.get(chainId) } catch (_: Exception) { return emptyList() }

        return try {
            val sourceResp = etherscanClient.getSourceCode(address, chain)
            val sourceData = sourceResp.result.firstOrNull() ?: return emptyList()

            val abi        = sourceData.ABI       ?: ""
            val sourceCode = sourceData.SourceCode ?: ""
            val isVerified = abi.isNotBlank() && abi != "Contract source code not verified"

            val findings = mutableListOf<BlockchainFinding>()

            // ── Verificación del código fuente ────────────────────────────────
            if (!isVerified) {
                // GoPlus ya genera BC-VERIFY-001, no duplicamos.
                // Solo lo añadimos si no viene de GoPlus (el service lo dedup por id)
                findings += BlockchainFinding(
                    id = "BC-VERIFY-001", risk = "HIGH",
                    title = "Código fuente no verificado en Etherscan",
                    description = "El contrato no tiene el código fuente verificado en el explorador de bloques. No es posible auditar la lógica ni confirmar la ausencia de funciones maliciosas ocultas.",
                    recommendation = "Exigir al equipo que verifique el código fuente. Un contrato no verificado es una señal de alerta importante.",
                    evidence = "Etherscan getsourcecode: ABI vacío",
                    category = "VERIFICATION"
                )
                log.debug("EtherscanAbiScanner $address: código no verificado")
                return findings // Sin ABI no podemos analizar nada más
            }

            // ── Mint authority ────────────────────────────────────────────────
            val mintFns = findFunctionsInAbi(abi, MINT_FUNCTIONS)
            if (mintFns.isNotEmpty()) {
                findings += BlockchainFinding(
                    id = "BC-MINT-001", risk = "HIGH",
                    title = "Función mint() accesible en el ABI",
                    description = "El ABI del contrato expone funciones de acuñación (${mintFns.take(3).joinToString()}). Si estas funciones no están protegidas o el ownership no está renunciado, el creador puede emitir tokens ilimitados diluyendo la supply.",
                    recommendation = "Verificar que las funciones mint estén detrás de onlyOwner con ownership renunciado, o que exista un cap máximo de supply no modificable.",
                    evidence = "ABI contiene: ${mintFns.joinToString(", ")}",
                    category = "TAXATION"
                )
            }

            // ── Tax changeability ─────────────────────────────────────────────
            val taxFns = findFunctionsInAbi(abi, TAX_FUNCTIONS)
            if (taxFns.isNotEmpty()) {
                findings += BlockchainFinding(
                    id = "BC-TAX-003", risk = "MEDIUM",
                    title = "Funciones de modificación de impuestos en runtime",
                    description = "El contrato incluye funciones que permiten cambiar los impuestos de compra/venta en tiempo de ejecución (${taxFns.take(3).joinToString()}). Esto permite al propietario subir los fees a niveles prohibitivos después de la inversión.",
                    recommendation = "Comprobar que estas funciones tienen límites máximos hardcodeados (por ejemplo, tax ≤ 10%) y que el propietario tiene historial limpio.",
                    evidence = "ABI contiene: ${taxFns.joinToString(", ")}",
                    category = "TAXATION"
                )
            }

            // ── Mención de auditoría en el código fuente ──────────────────────
            val auditKeywords = listOf("certik", "hacken", "quantstamp", "consensys diligence", "trail of bits", "peckshield", "slowmist")
            val hasAuditMention = auditKeywords.any { sourceCode.lowercase().contains(it) }
            if (hasAuditMention) {
                // Finding positivo/INFO
                val mentioned = auditKeywords.filter { sourceCode.lowercase().contains(it) }
                findings += BlockchainFinding(
                    id = "BC-AUDIT-OK", risk = "INFO",
                    title = "Mención de auditoría de seguridad en el código fuente",
                    description = "El código fuente menciona firmas auditoras reconocidas: ${mentioned.joinToString()}. Esto es una señal positiva, aunque no sustituye a verificar el informe oficial.",
                    recommendation = "Verificar el informe de auditoría en el sitio web de la firma y comprobar que la versión auditada coincide con el contrato desplegado.",
                    evidence = "Source code mentions: ${mentioned.joinToString()}",
                    category = "VERIFICATION"
                )
            }

            log.info("EtherscanAbiScanner $address: mint=${mintFns.size} tax=${taxFns.size} findings → ${findings.size}")
            findings

        } catch (e: Exception) {
            log.warn("EtherscanAbiScanner error para $address: ${e.message}")
            emptyList()
        }
    }
}
