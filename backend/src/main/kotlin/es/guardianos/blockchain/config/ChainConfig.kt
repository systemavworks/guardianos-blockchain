package es.guardianos.blockchain.config

data class ChainConfig(
    val chainId: String,
    val etherscanBaseUrl: String,
    val explorerUrl: String,
    val nativeCurrency: String = "ETH"
)

object BlockchainChains {
    val SUPPORTED = mapOf(
        "ethereum" to ChainConfig(
            chainId = "ethereum",
            etherscanBaseUrl = "https://api.etherscan.io/api",
            explorerUrl = "https://etherscan.io"
        ),
        "polygon" to ChainConfig(
            chainId = "polygon",
            etherscanBaseUrl = "https://api.polygonscan.com/api",
            explorerUrl = "https://polygonscan.com",
            nativeCurrency = "MATIC"
        ),
        "bsc" to ChainConfig(
            chainId = "bsc",
            etherscanBaseUrl = "https://api.bscscan.com/api",
            explorerUrl = "https://bscscan.com",
            nativeCurrency = "BNB"
        )
        // Añadir más chains aquí sin refactorizar scanners
    )
    
    fun get(chainId: String): ChainConfig {
        return SUPPORTED[chainId.lowercase()] 
            ?: error("Chain no soportada: $chainId. Chains disponibles: ${SUPPORTED.keys}")
    }
    
    fun isSupported(chainId: String): Boolean = SUPPORTED.containsKey(chainId.lowercase())
}
