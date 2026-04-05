package es.guardianos.blockchain.scanner

import es.guardianos.blockchain.config.BlockchainChains
import es.guardianos.blockchain.model.BlockchainFinding
import es.guardianos.blockchain.util.EtherscanClient
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = LoggerFactory.getLogger("ContractAgeScanner")

class ContractAgeScanner(private val etherscanClient: EtherscanClient?) {

    suspend fun scan(address: String, chainId: String): List<BlockchainFinding> {
        if (etherscanClient == null) {
            log.debug("ContractAgeScanner: sin API key de Etherscan, omitiendo")
            return emptyList()
        }

        val chain = try { BlockchainChains.get(chainId) } catch (_: Exception) { return emptyList() }

        return try {
            val txList = etherscanClient.getTxList(address, chain, page = 1, offset = 1)
            val firstTx = txList.result.firstOrNull() ?: return emptyList()

            val deployTs = firstTx.timeStamp.toLongOrNull() ?: return emptyList()
            val deployedAt = Instant.ofEpochSecond(deployTs)
            val agedays = ChronoUnit.DAYS.between(deployedAt, Instant.now())

            val findings = mutableListOf<BlockchainFinding>()

            when {
                agedays < 7 -> findings += BlockchainFinding(
                    id = "BC-AGE-001", risk = "HIGH",
                    title = "Contrato muy reciente (${agedays} días)",
                    description = "El contrato fue desplegado hace solo $agedays días. Los contratos nuevos no tienen historial que permita evaluar el comportamiento del equipo ni la solidez del proyecto.",
                    recommendation = "Esperar al menos 30-90 días para evaluar la legitimidad del proyecto, la actividad del team y la evolución de la liquidez.",
                    evidence = "Etherscan: primer tx hash=${firstTx.hash} · timestamp=$deployTs",
                    category = "AGE"
                )
                agedays < 30 -> findings += BlockchainFinding(
                    id = "BC-AGE-001", risk = "MEDIUM",
                    title = "Contrato reciente ($agedays días)",
                    description = "El contrato tiene $agedays días de vida. Es pronto para evaluar la fiabilidad a largo plazo del proyecto.",
                    recommendation = "Hacer DYOR exhaustivo sobre el equipo y los fundamentos antes de invertir en proyectos con menos de 30 días.",
                    evidence = "Etherscan: primer tx timestamp=$deployTs",
                    category = "AGE"
                )
                else -> log.debug("ContractAgeScanner: $address tiene $agedays días — sin finding")
            }

            log.info("ContractAgeScanner $address (chain=$chainId): $agedays días · ${findings.size} finding(s)")
            findings
        } catch (e: Exception) {
            log.warn("ContractAgeScanner error para $address: ${e.message}")
            emptyList()
        }
    }
}
