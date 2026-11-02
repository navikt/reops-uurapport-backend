package accessibility.reporting.tool.database

import kotliquery.queryOf
import java.time.LocalDate

class SettingsRepository(private val database: Database) {

    /** Admin-chosen deadline for when teams must have sent in their reports. Null means "use the yearly default". */
    fun getDeadline(): LocalDate? = database.query {
        queryOf("select value from app_setting where key = :key", mapOf("key" to DEADLINE_KEY))
            .map { row -> LocalDate.parse(row.string("value")) }.asSingle
    }

    fun setDeadline(deadline: LocalDate?) {
        if (deadline == null) {
            database.update { queryOf("delete from app_setting where key = :key", mapOf("key" to DEADLINE_KEY)) }
        } else {
            database.update {
                queryOf(
                    """insert into app_setting (key, value) values (:key, :value)
                        |on conflict (key) do update set value = :value""".trimMargin(),
                    mapOf("key" to DEADLINE_KEY, "value" to deadline.toString())
                )
            }
        }
    }

    private companion object {
        const val DEADLINE_KEY = "report_deadline"
    }
}
