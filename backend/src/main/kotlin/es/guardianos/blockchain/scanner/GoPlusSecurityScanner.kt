package es.guardianos.blockchain.scanner

import es.guardianos.blockchain.config.BlockchainChains
import es.guardianos.blockchain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GoPlusSecurityScanner")
private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Cadena → ID numérico que usa GoPlus */
private val GOPLUS_CHAIN_IDS = mapOf(
    "ethereum" to "1",
    "bsc"      to "56",
    "polygon"  to "137",
    "arbitrum" to "42161",
    "optimism" to "10",
    "avalanche" to "43114"
)

class GoPlusSecurityScanner(private val httpClient: HttpClient) {

    suspend fun scan(address: String, chainId: String): List<BlockchainFinding> {
        val goplusChainId = GOPLUS_CHAIN_IDS[chainId.lowercase()]
            ?: return listOf(infoFinding("BC-CHAIN-001", "Chain no soportada por GoPlus",
                "La chain '$chainId' no está soportada en el análisis de seguridad GoPlus.", "VERIFICATION"))

        val addrLower = address.lowercase()
        log.info("GoPlus scan: $addrLower (chain=$chainId → goplusId=$goplusChainId)")

        val response = try {
            httpClient.get("https://api.gopluslabs.io/api/v1/token_security/$goplusChainId?contract_addresses=$addrLower")
                .body<GoPlusResponse>()
        } catch (e: Exception) {
            log.warn("GoPlus API error para $addrLower: ${e.message}")
            return listOf(infoFinding("BC-GOPLUS-ERR", "Análisis GoPlus no disponible",
                "No se pudo conectar con la API de GoPlus Security. Intenta de nuevo en unos minutos.", "VERIFICATION"))
        }

        if (response.code != 1 || response.result.isEmpty()) {
            log.warn("GoPlus sin datos para $addrLower: code=${response.code} msg=${response.message}")
            return listOf(infoFinding("BC-GOPLUS-NODATA", "Contrato no encontrado en GoPlus",
                "GoPlus no tiene datos de seguridad para esta dirección. Puede ser un contrato muy nuevo, no verificado o no listado.", "VERIFICATION"))
        }

        val data = response.result.values.first()
        val findings = mutableListOf<BlockchainFinding>()

        // ── Honeypot ──────────────────────────────────────────────────────────
        if (data.is_honeypot == "1") {
            findings += BlockchainFinding(
                id = "BC-HONEYPOT-001", risk = "CRITICAL",
                title = "Contrato honeypot detectado",
                description = "Este token es un honeypot: permite comprar pero bloquea las ventas. Los inversores quedan atrapados sin poder recuperar sus fondos.",
                recommendation = "No interactuar con este contrato bajo ninguna circunstancia. Reportar en plataformas anti-scam como TokenSniffer o HoneyPot.is.",
                evidence = "GoPlus: is_honeypot=1",
                category = "HONEYPOT"
            )
        }
        if (data.honeypot_with_same_creator == "1") {
            findings += BlockchainFinding(
                id = "BC-HONEYPOT-002", risk = "CRITICAL",
                title = "Creador con historial de honeypots",
                description = "El mismo creador de este contrato ha desplegado anteriormente contratos honeypot confirmados.",
                recommendation = "El historial del creador es una señal de alerta máxima. Evitar cualquier interacción.",
                evidence = "GoPlus: honeypot_with_same_creator=1 · creator=${data.creator_address}",
                category = "HONEYPOT"
            )
        }
        if (data.cannot_buy == "1") {
            findings += BlockchainFinding(
                id = "BC-HONEYPOT-003", risk = "CRITICAL",
                title = "Función de compra deshabilitada",
                description = "El contrato bloquea activamente las operaciones de compra. Probablemente en fase de preparación de scam o contrato mal configurado.",
                recommendation = "No depositar fondos en este contrato.",
                evidence = "GoPlus: cannot_buy=1",
                category = "HONEYPOT"
            )
        }
        if (data.cannot_sell_all == "1") {
            findings += BlockchainFinding(
                id = "BC-HONEYPOT-004", risk = "HIGH",
                title = "Venta total bloqueada",
                description = "No es posible vender el 100% del saldo en una sola transacción. Técnica común en contratos tramposos para retener fondos.",
                recommendation = "Evitar acumular posiciones grandes en este token.",
                evidence = "GoPlus: cannot_sell_all=1",
                category = "HONEYPOT"
            )
        }

        // ── Impuestos de transacción ──────────────────────────────────────────
        val buyTax   = data.buy_tax?.toDoubleOrNull() ?: 0.0
        val sellTax  = data.sell_tax?.toDoubleOrNull() ?: 0.0
        if (buyTax > 0.10) {
            findings += BlockchainFinding(
                id = "BC-TAX-001", risk = "HIGH",
                title = "Tax de compra elevado: ${(buyTax * 100).toInt()}%",
                description = "El contrato aplica un impuesto del ${(buyTax * 100).toInt()}% en cada compra. Taxes superiores al 10% son señal habitual de proyectos fraudulentos.",
                recommendation = "Evaluar la legitimidad del proyecto. Un tax >25% prácticamente imposibilita el trading rentable.",
                evidence = "GoPlus: buy_tax=${data.buy_tax}",
                category = "TRADING"
            )
        }
        if (sellTax > 0.10) {
            findings += BlockchainFinding(
                id = "BC-TAX-002", risk = "HIGH",
                title = "Tax de venta elevado: ${(sellTax * 100).toInt()}%",
                description = "El contrato aplica un impuesto del ${(sellTax * 100).toInt()}% en cada venta. Fórmula habitual en contratos tipo 'slow rug' donde los holders se descapitalizan gradualmente.",
                recommendation = "Considerar el impacto real del tax antes de invertir. Comprobar si el tax es modificable por el owner.",
                evidence = "GoPlus: sell_tax=${data.sell_tax}",
                category = "TRADING"
            )
        }

        // ── Privilegios del propietario ───────────────────────────────────────
        if (data.owner_change_balance == "1") {
            findings += BlockchainFinding(
                id = "BC-OWNER-001", risk = "CRITICAL",
                title = "Owner puede modificar balances de wallets",
                description = "El propietario del contrato tiene capacidad para modificar el saldo de cualquier wallet arbitrariamente. Esto equivale a control total sobre los fondos de todos los inversores.",
                recommendation = "Riesgo crítico inaceptable. El owner podría drenar cualquier wallet de forma instantánea.",
                evidence = "GoPlus: owner_change_balance=1 · owner=${data.owner_address}",
                category = "OWNERSHIP"
            )
        }
        if (data.selfdestruct == "1") {
            findings += BlockchainFinding(
                id = "BC-OWNER-002", risk = "CRITICAL",
                title = "Función selfdestruct activa",
                description = "El contrato contiene la función SELFDESTRUCT. Al ejecutarse, el contrato desaparece de la blockchain y todos los fondos almacenados se pueden redirigir.",
                recommendation = "La presencia de selfdestruct en un token no es nunca justificable. Señal de exit scam potencial.",
                evidence = "GoPlus: selfdestruct=1",
                category = "OWNERSHIP"
            )
        }
        if (data.is_mintable == "1") {
            findings += BlockchainFinding(
                id = "BC-OWNER-003", risk = "HIGH",
                title = "Owner puede acuñar tokens ilimitados",
                description = "El propietario tiene capacidad para crear nuevos tokens sin límite. Esto diluye el suministro y puede colapsar el precio en cualquier momento.",
                recommendation = "Verificar si el minteo tiene límites o está bloqueado detrás de un timelock/multisig. Proyectos legítimos suelen renunciar al minteo o usar gobernanza.",
                evidence = "GoPlus: is_mintable=1",
                category = "OWNERSHIP"
            )
        }
        if (data.transfer_pausable == "1") {
            findings += BlockchainFinding(
                id = "BC-OWNER-004", risk = "HIGH",
                title = "Owner puede pausar todas las transferencias",
                description = "El propietario puede congelar todas las transferencias del token de forma unilateral. Potencial vector de honeypot diferido.",
                recommendation = "Aceptable solo en protocolos DeFi regulados con motivos técnicos legítimos. En tokens estándar es una señal de alerta.",
                evidence = "GoPlus: transfer_pausable=1",
                category = "OWNERSHIP"
            )
        }
        if (data.hidden_owner != null && data.hidden_owner != "0" && data.hidden_owner!!.isNotBlank()) {
            findings += BlockchainFinding(
                id = "BC-OWNER-005", risk = "HIGH",
                title = "Owner oculto detectado",
                description = "El contrato tiene un mecanismo de owner oculto que no es visible de forma directa. Permite control no declarado sobre el token.",
                recommendation = "Los contratos con owners ocultos son señal de mala fe intencionada. Evitar este asset.",
                evidence = "GoPlus: hidden_owner=${data.hidden_owner}",
                category = "OWNERSHIP"
            )
        }
        if (data.can_take_back_ownership == "1") {
            findings += BlockchainFinding(
                id = "BC-OWNER-006", risk = "HIGH",
                title = "Owner puede recuperar la titularidad",
                description = "Aunque el contrato haya renunciado a la propiedad, existe un mecanismo para recuperarla. Así se evade el análisis de contratos 'renounced'.",
                recommendation = "Un renounce falso es una de las tácticas más comunes en rugpulls. No confiar en que el owner haya renunciado si esta flag está activa.",
                evidence = "GoPlus: can_take_back_ownership=1",
                category = "OWNERSHIP"
            )
        }

        // ── Restricciones de trading ──────────────────────────────────────────
        if (data.is_blacklisted == "1") {
            findings += BlockchainFinding(
                id = "BC-TRADING-001", risk = "MEDIUM",
                title = "Sistema de blacklist activo",
                description = "El contrato puede bloquear wallets específicas para que no puedan transferir tokens. El propietario puede añadir cualquier dirección a la blacklist.",
                recommendation = "Negativo para descentralización. Puede ser aceptable en stablecoins con requisitos de compliance, pero es una señal de alerta en tokens estándar.",
                evidence = "GoPlus: is_blacklisted=1",
                category = "TRADING"
            )
        }
        if (data.is_whitelisted == "1") {
            findings += BlockchainFinding(
                id = "BC-TRADING-002", risk = "MEDIUM",
                title = "Sistema de whitelist activo (solo ciertas wallets pueden operar)",
                description = "Las transferencias solo están permitidas para wallets en whitelist. El owner controla qué wallets pueden comerciar con el token.",
                recommendation = "Inaceptable en proyectos públicos. Señal de scam donde el equipo controla quién puede vender.",
                evidence = "GoPlus: is_whitelisted=1",
                category = "TRADING"
            )
        }
        if (data.trading_cooldown == "1") {
            findings += BlockchainFinding(
                id = "BC-TRADING-003", risk = "MEDIUM",
                title = "Cooldown entre transacciones",
                description = "El contrato impone un tiempo de espera mínimo entre transacciones para la misma wallet. Puede ser anti-bot legítimo o restricción arbitraria.",
                recommendation = "Verificar la duración del cooldown. Cooldowns >1 minuto en tokens estándar son señal de alerta.",
                evidence = "GoPlus: trading_cooldown=1",
                category = "TRADING"
            )
        }
        if (data.external_call == "1") {
            findings += BlockchainFinding(
                id = "BC-CODE-001", risk = "MEDIUM",
                title = "Llamadas externas en funciones de transferencia",
                description = "El contrato realiza llamadas a contratos externos durante las transferencias. Potencial vector de reentrancy o comportamiento inesperado.",
                recommendation = "Revisar el código fuente para identificar qué contratos externos se invocan y bajo qué condiciones.",
                evidence = "GoPlus: external_call=1",
                category = "OWNERSHIP"
            )
        }

        // ── Verificación y proxy ──────────────────────────────────────────────
        if (data.is_open_source != "1") {
            findings += BlockchainFinding(
                id = "BC-VERIFY-001", risk = "HIGH",
                title = "Código fuente no verificado en Etherscan",
                description = "El código fuente del contrato no está publicado en el explorador de bloques. Es imposible auditar su comportamiento real sin ingeniería inversa del bytecode.",
                recommendation = "Nunca invertir en contratos con código no verificado sin una auditoría independiente de seguridad del bytecode.",
                evidence = "GoPlus: is_open_source=${data.is_open_source ?: "0"}",
                category = "VERIFICATION"
            )
        }
        if (data.is_proxy == "1") {
            findings += BlockchainFinding(
                id = "BC-PROXY-001", risk = "MEDIUM",
                title = "Contrato proxy upgradeable detectado",
                description = "El contrato es un proxy que delega la lógica a otro contrato. El owner puede actualizar la implementación y cambiar completamente el comportamiento del token.",
                recommendation = "Verificar si el upgrade está controlado por multisig o timelock. Un proxy controlado unilateralmente es equivalente a tener código mutable.",
                evidence = "GoPlus: is_proxy=1",
                category = "PROXY"
            )
        }

        // ── Concentración de holders ──────────────────────────────────────────
        val holders = data.holders ?: emptyList()
        if (holders.isNotEmpty()) {
            val topHolderPct   = holders.firstOrNull()?.percent?.toDoubleOrNull() ?: 0.0
            val top3Pct        = holders.take(3).sumOf { it.percent?.toDoubleOrNull() ?: 0.0 }
            val top10Pct       = holders.take(10).sumOf { it.percent?.toDoubleOrNull() ?: 0.0 }
            val ownerPct       = data.owner_percent?.toDoubleOrNull() ?: 0.0

            if (topHolderPct > 0.50) {
                findings += BlockchainFinding(
                    id = "BC-CONC-001", risk = "CRITICAL",
                    title = "Un solo holder controla +${(topHolderPct * 100).toInt()}% del supply",
                    description = "El mayor holder posee el ${(topHolderPct * 100).toInt()}% del suministro total. Una venta masiva hundiría el precio completamente.",
                    recommendation = "Concentración >50% en un solo holder es incompatible con un proyecto descentralizado serio.",
                    evidence = "GoPlus holders[0]: ${holders.firstOrNull()?.address} = ${(topHolderPct * 100).toInt()}%",
                    category = "CONCENTRATION"
                )
            } else if (top3Pct > 0.80) {
                findings += BlockchainFinding(
                    id = "BC-CONC-002", risk = "HIGH",
                    title = "Top 3 holders concentran ${(top3Pct * 100).toInt()}% del supply",
                    description = "Los 3 mayores holders controlan el ${(top3Pct * 100).toInt()}% del suministro. Alta concentración con riesgo de dump coordinado.",
                    recommendation = "Evaluar si los wallets son exchanges centralizados (legítimo) o wallets de team/dev (señal de alerta).",
                    evidence = "GoPlus top3 concentration: ${(top3Pct * 100).toInt()}%",
                    category = "CONCENTRATION"
                )
            } else if (top10Pct > 0.80) {
                findings += BlockchainFinding(
                    id = "BC-CONC-003", risk = "MEDIUM",
                    title = "Top 10 holders concentran ${(top10Pct * 100).toInt()}% del supply",
                    description = "Los 10 mayores holders controlan el ${(top10Pct * 100).toInt()}% del suministro. Riesgo moderado de manipulación de precio.",
                    recommendation = "Comprobar si los grandes holders son contratos de staking/vesting (legítimo) o wallets de personas físicas.",
                    evidence = "GoPlus top10 concentration: ${(top10Pct * 100).toInt()}%",
                    category = "CONCENTRATION"
                )
            }

            if (ownerPct > 0.05) {
                findings += BlockchainFinding(
                    id = "BC-CONC-004", risk = "MEDIUM",
                    title = "Owner controla el ${(ownerPct * 100).toInt()}% del supply",
                    description = "El wallet del propietario del contrato posee el ${(ownerPct * 100).toInt()}% del suministro total. Combina privilegios de código con poder de mercado.",
                    recommendation = "Doble riesgo: privilegios técnicos + capacidad de dump masivo. Evaluar si existe vesting o lock-up de los tokens del equipo.",
                    evidence = "GoPlus: owner_percent=${data.owner_percent} owner=${data.owner_address}",
                    category = "CONCENTRATION"
                )
            }
        }

        // ── Liquidez ──────────────────────────────────────────────────────────
        if (data.is_in_dex != "1") {
            findings += BlockchainFinding(
                id = "BC-LIQ-001", risk = "MEDIUM",
                title = "Token no listado en ningún DEX",
                description = "El token no tiene liquidez en DEXes conocidos. No hay forma de comprarlo ni venderlo en mercado abierto actualmente.",
                recommendation = "Un token sin liquidez puede estar en fase pre-launch (legítimo) o ser un proyecto abandonado.",
                evidence = "GoPlus: is_in_dex=${data.is_in_dex ?: "0"}",
                category = "TRADING"
            )
        } else {
            // Comprobar liquidez LP concentrada
            val lpHolders = data.lp_holders ?: emptyList()
            val unlockedLpPct = lpHolders.filter { it.is_locked != 1 }
                .sumOf { it.percent?.toDoubleOrNull() ?: 0.0 }
            if (unlockedLpPct > 0.80) {
                findings += BlockchainFinding(
                    id = "BC-LIQ-002", risk = "HIGH",
                    title = "Liquidez sin bloquear (${(unlockedLpPct * 100).toInt()}% LP desbloqueado)",
                    description = "El ${(unlockedLpPct * 100).toInt()}% de los tokens de liquidez (LP) no están bloqueados en ningún contrato de lock. El team puede retirar toda la liquidez en cualquier momento (rug pull).",
                    recommendation = "Exigir que la liquidez esté bloqueada al menos 6-12 meses en plataformas como Team.Finance o Uncx antes de invertir.",
                    evidence = "GoPlus LP holders unlocked: ${(unlockedLpPct * 100).toInt()}%",
                    category = "TRADING"
                )
            }
        }

        // ── Riesgos adicionales de GoPlus ─────────────────────────────────────
        if (!data.other_potential_risks.isNullOrBlank()) {
            findings += BlockchainFinding(
                id = "BC-OTHER-001", risk = "MEDIUM",
                title = "Riesgos adicionales detectados por GoPlus",
                description = data.other_potential_risks,
                recommendation = "Revisar los riesgos específicos indicados por GoPlus y evaluar su impacto en el contexto del proyecto.",
                evidence = "GoPlus: other_potential_risks=${data.other_potential_risks}",
                category = "OWNERSHIP"
            )
        }

        log.info("GoPlus scan $addrLower → ${findings.size} finding(s)")
        return findings
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun infoFinding(id: String, title: String, desc: String, cat: String) = BlockchainFinding(
        id = id, risk = "INFO", title = title, description = desc,
        recommendation = "Sin acción recomendada.", category = cat
    )

    /** Datos de token (nombre, símbolo, etc.) para enriquecer el reporte */
    suspend fun getTokenInfo(address: String, chainId: String): GoPlusTokenData? {
        val goplusChainId = GOPLUS_CHAIN_IDS[chainId.lowercase()] ?: return null
        return try {
            val response = httpClient.get(
                "https://api.gopluslabs.io/api/v1/token_security/$goplusChainId?contract_addresses=${address.lowercase()}"
            ).body<GoPlusResponse>()
            response.result.values.firstOrNull()
        } catch (_: Exception) { null }
    }
}
