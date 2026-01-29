package accessibility.reporting.tool

import accessibility.reporting.tool.database.Database
import accessibility.reporting.tool.database.EmbeddedPostgresDatabase

/**
 * Backwards-compatible alias kept so older tests/utilities importing LocalPostgresDatabase still compile.
 * Uses in-memory H2 (PostgreSQL mode) under the hood.
 */
object LocalPostgresDatabase {
    fun cleanDb(): Database = EmbeddedPostgresDatabase.cleanDb()

    fun prepareForNextTest() {
        // no-op: cleanDb() returns a fresh in-memory DB instance
        // keeping this for API compatibility with existing tests
    }
}

// Keep existing helper used by tests
inline fun <T> T.assert(block: T.() -> Unit): T = apply { block() }