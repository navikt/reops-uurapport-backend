package accessibility.reporting.tool.database

import accessibility.reporting.tool.authentication.User
import kotliquery.queryOf
import java.time.Duration

class AdminRepository(private val database: Database) {

    /** Records that this admin is active. Writes at most once per [UPDATE_INTERVAL] per admin. */
    fun recordSeen(user: User) {
        val now = LocalDateTimeHelper.nowAtUtc()
        database.update {
            queryOf(
                """insert into admin_seen (oid, email, name, last_seen) values (:oid, :email, :name, :now)
                    |on conflict (oid) do update set email=:email, name=:name, last_seen=:now
                    |where admin_seen.last_seen < :threshold""".trimMargin(),
                mapOf(
                    "oid" to user.oid.str(),
                    "email" to user.email.str(),
                    "name" to user.name,
                    "now" to now,
                    "threshold" to now.minus(UPDATE_INTERVAL)
                )
            )
        }
    }

    fun getRecentAdmins(): List<AdminContact> = database.list {
        queryOf(
            "select email, name from admin_seen where last_seen > :since order by lower(coalesce(name, email))",
            mapOf("since" to LocalDateTimeHelper.nowAtUtc().minus(ACTIVE_WINDOW))
        ).map { row -> AdminContact(name = row.stringOrNull("name"), email = row.string("email")) }.asList
    }

    companion object {
        private val UPDATE_INTERVAL: Duration = Duration.ofHours(1)
        private val ACTIVE_WINDOW: Duration = Duration.ofDays(90)
    }
}

class AdminContact(val name: String?, val email: String)
