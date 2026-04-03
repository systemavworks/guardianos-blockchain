package es.guardianos.blockchain

import io.ktor.server.application.*
import io.ktor.server.netty.*
import es.guardianos.blockchain.config.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // Configurar plugins Ktor: auth, routing, serialization, etc.
    // Se implementará en el Sprint 1 después de tener ChainConfig y EtherscanClient
    
    println("✅ GuardianOS Blockchain backend iniciado en puerto 8081")
}
