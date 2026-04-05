package es.guardianos.blockchain.service

import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init(databaseUrl: String) {
        // Flyway — solo gestiona el schema blockchain
        val flyway = Flyway.configure()
            .dataSource(databaseUrl, null, null)
            .schemas("blockchain")
            .defaultSchema("blockchain")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .locations("classpath:db/migration")
            .load()

        flyway.repair()
        val result = flyway.migrate()
        log.info("Flyway blockchain: ${result.migrationsExecuted} migración(es) aplicada(s)")

        // Exposed — mismo DataSource
        Database.connect(databaseUrl, driver = "org.postgresql.Driver")
        log.info("DatabaseFactory blockchain conectado")
    }

    suspend fun <T> dbQuery(block: () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
