package accessibility.reporting.tool.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotliquery.Query
import kotliquery.Session
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.sessionOf
import org.flywaydb.core.Flyway

object EmbeddedPostgresDatabase {

    private var pg: EmbeddedPostgres? = null

    fun cleanDb(): Database {
        val embedded = pg ?: EmbeddedPostgres.builder()
            .setPort(0)
            .start()
            .also { pg = it }

        val pgDataSource = embedded.postgresDatabase

        Flyway.configure()
            .dataSource(pgDataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
            .apply {
                clean()
                migrate()
            }

        val hikari = HikariDataSource(
            HikariConfig().apply {
                dataSource = pgDataSource
                maximumPoolSize = 4
            }
        )

        return object : Database {
            override val dataSource: HikariDataSource = hikari

            fun <T> session(block: (Session) -> T): T =
                sessionOf(dataSource).use(block)

            override fun update(block: () -> Query) {
                session { s -> s.run(block().asUpdate) }
            }

            override fun <T> query(block: () -> NullableResultQueryAction<T>): T? =
                session { s -> s.run(block()) }

            override fun <T> list(block: () -> ListResultQueryAction<T>): List<T> =
                session { s -> s.run(block()) }
        }
    }
}