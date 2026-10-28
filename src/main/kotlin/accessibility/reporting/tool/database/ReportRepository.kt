package accessibility.reporting.tool.database

import accessibility.reporting.tool.authentication.User.Oid
import accessibility.reporting.tool.wcag.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.Row
import kotliquery.queryOf
import org.postgresql.util.PGobject
import java.lang.StringBuilder
import java.time.LocalDateTime


class ReportRepository(database: Database) : BaseRepository(database) {
    val objectmapper = jacksonObjectMapper()

    fun upsertReport(report: Report) = upsertReportReturning<Report>(report)
    inline fun <reified T : ReportContent> getReport(reportId: String): T? =
        database.query {
            queryOf(
                "select created, last_changed, report_data ->> 'version' as version, report_data from report where report_id=:reportid",
                mapOf("reportid" to reportId)
            ).map { row -> report<T>(row) }.asSingle
        }
    inline fun <reified T : ReportContent> getReportsForUser(oid: Oid): List<T> = database.list {
        queryOf(
            """select created, last_changed, report_data ->> 'version' as version, report_data from report
                | where archived_at is null
                |  AND (report_data -> 'user'->>'oid'=:oid
                |  OR report_data -> 'author'->>'oid'=:oid)""".trimMargin(),
            mapOf(
                "oid" to oid.str()
            )
        ).map { row -> report<T>(row) }.asList
    }
    inline fun <reified T> getReports(type: ReportType? = null, ids: List<String>? = null): List<T> {
        val query = StringBuilder("select created, last_changed,report_data ->> 'version' as version, report_data from report where archived_at is null")
            .apply {
                if (type != null)
                    append(" and report_data ->> 'reportType' = :type")
                if (ids != null)
                    append(" and report_id IN ${ids.sqlList()}")
            }
            .toString()
        return try {
            database.list {
                queryOf(query, mapOf("type" to type?.name)).map { row -> report<T>(row) }.asList
            }
        } catch (e: Exception) {
            log.error { "Error fetching reports: $query" }
            throw e
        }
    }

    inline fun <reified T> getReportsByDateRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        type: ReportType? = null
    ): List<T> =
        try {
            database.list {
                queryOf(
                    StringBuilder("select created, last_changed, report_data ->> 'version' as version, report_data from report")
                        .apply {
                            append(" where archived_at is null and last_changed BETWEEN :startDate AND :endDate")
                            if (type != null) {
                                append(" AND report_data ->> 'reportType' = :type")
                            }
                            append(" ORDER BY last_changed DESC")
                        }
                        .toString(),
                    mapOf(
                        "startDate" to startDate,
                        "endDate" to endDate,
                        "type" to type?.name
                    )
                ).map { row -> report<T>(row) }.asList
            }
        } catch (e: Exception) {
            log.error {
                "Error fetching reports by date range: startDate=$startDate, endDate=$endDate, type=$type - ${e.message}"
            }
            throw e
        }

    fun archiveReport(reportId: String, archivedBy: String, reason: String?): Boolean =
        database.updateCount {
            queryOf(
                """update report set archived_at=:archivedAt, archived_by=:archivedBy, archive_reason=:reason
                    |where report_id=:id and archived_at is null""".trimMargin(),
                mapOf(
                    "id" to reportId,
                    "archivedAt" to LocalDateTimeHelper.nowAtUtc(),
                    "archivedBy" to archivedBy,
                    "reason" to reason
                )
            )
        } > 0

    fun unarchiveReport(reportId: String): Boolean =
        database.updateCount {
            queryOf(
                """update report set archived_at=null, archived_by=null, archive_reason=null
                    |where report_id=:id and archived_at is not null""".trimMargin(),
                mapOf("id" to reportId)
            )
        } > 0

    fun getArchiveInfo(reportId: String): ArchiveInfo? =
        database.query {
            queryOf(
                "select archived_at, archived_by, archive_reason from report where report_id=:id and archived_at is not null",
                mapOf("id" to reportId)
            ).map { row -> archiveInfo(row) }.asSingle
        }

    fun getArchivedReports(): List<ArchivedReport> = database.list {
        queryOf(
            """select created, last_changed, report_data ->> 'version' as version, report_data,
                |archived_at, archived_by, archive_reason
                |from report where archived_at is not null order by archived_at desc""".trimMargin()
        ).map { row -> ArchivedReport(report<Report>(row), archiveInfo(row)) }.asList
    }

    fun archiveInfo(row: Row) = ArchiveInfo(
        archivedAt = row.localDateTime("archived_at"),
        archivedBy = row.stringOrNull("archived_by") ?: "",
        reason = row.stringOrNull("archive_reason")
    )

    fun deleteReport(reportId: String): Boolean {
        database.update {
            queryOf("delete from changelog where report_id=:id", mapOf("id" to reportId))
        }
        return database.updateCount {
            queryOf("delete from report where report_id=:id", mapOf("id" to reportId))
        } > 0
    }
}

data class ArchiveInfo(val archivedAt: LocalDateTime, val archivedBy: String, val reason: String?)

class ArchivedReport(val report: Report, val info: ArchiveInfo)

fun List<String>.sqlList(): String = joinToString(",", "(", ")") { "'$it'" }

fun String.jsonB() = PGobject().apply {
    type = "jsonb"
    value = this@jsonB
}

