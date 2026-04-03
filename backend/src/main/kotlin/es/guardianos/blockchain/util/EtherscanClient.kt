package es.guardianos.blockchain.util

import es.guardianos.blockchain.config.ChainConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

/**
 * Cliente singleton para Etherscan API con rate limiting global
 * Free tier: 5 req/s, Pro: 100k req/día
 */
class EtherscanClient private constructor(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val rateLimiter: RateLimiter = RateLimiter(maxPerSecond = 5)
) {
    
    companion object {
        @Volatile private var instance: EtherscanClient? = null
        
        fun getInstance(apiKey: String, httpClient: HttpClient): EtherscanClient {
            return instance ?: synchronized(this) {
                instance ?: EtherscanClient(apiKey, httpClient).also { instance = it }
            }
        }
    }
    
    suspend fun getSourceCode(address: String, chain: ChainConfig): SourceCodeResponse {
        return rateLimiter.withPermit {
            httpClient.get("${chain.etherscanBaseUrl}?module=contract&action=getsourcecode&address=$address&apikey=$apiKey")
                .body<SourceCodeResponse>()
        }
    }
    
    suspend fun getTokenHolders(address: String, chain: ChainConfig, page: Int = 1): TokenHolderResponse {
        return rateLimiter.withPermit {
            httpClient.get("${chain.etherscanBaseUrl}?module=token&action=tokenholderlist&contractaddress=$address&page=$page&apikey=$apiKey")
                .body<TokenHolderResponse>()
        }
    }
    
    suspend fun getTxList(address: String, chain: ChainConfig, page: Int = 1, offset: Int = 100): TxListResponse {
        return rateLimiter.withPermit {
            httpClient.get("${chain.etherscanBaseUrl}?module=account&action=txlist&address=$address&page=$page&offset=$offset&apikey=$apiKey")
                .body<TxListResponse>()
        }
    }
    
    suspend fun getContractABI(address: String, chain: ChainConfig): AbiResponse {
        return rateLimiter.withPermit {
            httpClient.get("${chain.etherscanBaseUrl}?module=contract&action=getabi&address=$address&apikey=$apiKey")
                .body<AbiResponse>()
        }
    }
}

// Rate limiter simple para respetar límites de API
class RateLimiter(private val maxPerSecond: Int) {
    private val semaphore = Semaphore(maxPerSecond)
    
    suspend fun <T> withPermit(block: suspend () -> T): T {
        semaphore.acquire()
        try {
            return block()
        } finally {
            // Liberar después de 1 segundo para mantener el rate
            kotlinx.coroutines.delay(1000 / maxPerSecond.toLong())
            semaphore.release()
        }
    }
}

// Respuestas Etherscan (simplificadas para Sprint 1)
@Serializable
data class SourceCodeResponse(
    val status: String,
    val message: String,
    val result: List<ContractSource>
)

@Serializable
data class ContractSource(
    val SourceCode: String,
    val ABI: String,
    val ContractName: String,
    val CompilerVersion: String,
    val OptimizationUsed: String,
    val Runs: String,
    val ConstructorArguments: String,
    val Proxy: String = "0",
    val Implementation: String? = null,
    val SwarmSource: String
)

@Serializable
data class TokenHolderResponse(
    val status: String,
    val message: String,
    val result: List<TokenHolder>
)

@Serializable
data class TokenHolder(
    val TokenHolderAddress: String,
    val TokenHolderQuantity: String,
    val TokenHolderPercentage: String
)

@Serializable
data class TxListResponse(
    val status: String,
    val message: String,
    val result: List<Transaction>
)

@Serializable
data class Transaction(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val timeStamp: String,
    val gasUsed: String,
    val gasPrice: String,
    val isError: String,
    val input: String
)

@Serializable
data class AbiResponse(
    val status: String,
    val message: String,
    val result: String
)
