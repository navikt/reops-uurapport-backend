package accessibility.reporting.tool.rest

import accessibility.reporting.tool.authentication.AdminCheck
import accessibility.reporting.tool.authentication.user
import accessibility.reporting.tool.database.OrganizationRepository
import accessibility.reporting.tool.database.ReportRepository
import accessibility.reporting.tool.wcag.AggregatedReport
import accessibility.reporting.tool.wcag.Report
import accessibility.reporting.tool.wcag.ReportType
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun Route.aggregatedAdminRoutes(reportRepository: ReportRepository, organizationRepository: OrganizationRepository) {
    route("reports/aggregated") {
        install(AdminCheck)
        route("new") {
            post {
                val newReportRequest = call.receive<NewAggregatedReportRequest>()

                // Validate request has either reports or date range
                newReportRequest.validate()

                // Parse date range if provided
                val dateRange = if (newReportRequest.startDate != null && newReportRequest.endDate != null) {
                    val startDate = parseIsoDateTime(newReportRequest.startDate)
                    val endDate = parseIsoDateTime(newReportRequest.endDate)

                    if (startDate.isAfter(endDate)) {
                        throw BadAggregatedReportRequestException("startDate must be before or equal to endDate")
                    }

                    startDate to endDate
                } else {
                    null
                }

                // Fetch source reports based on request type
                val sourceReports = when {
                    !newReportRequest.reports.isNullOrEmpty() && dateRange != null -> {
                        // Both report IDs and date range provided: fetch by IDs then filter by date range
                        val allReports = reportRepository.getReports<Report>(ids = newReportRequest.reports)
                        allReports.filter { report ->
                            !report.lastChanged.isBefore(dateRange.first) && !report.lastChanged.isAfter(dateRange.second)
                        }
                    }
                    !newReportRequest.reports.isNullOrEmpty() -> {
                        // Only report IDs provided
                        reportRepository.getReports<Report>(ids = newReportRequest.reports)
                    }
                    dateRange != null -> {
                        // Only date range provided
                        reportRepository.getReportsByDateRange<Report>(
                            startDate = dateRange.first,
                            endDate = dateRange.second,
                            type = ReportType.SINGLE
                        )
                    }
                    else -> {
                        throw BadAggregatedReportRequestException(
                            "Must provide either 'reports' (list of report IDs) or both 'startDate' and 'endDate'"
                        )
                    }
                }

                // Validate source reports
                when {
                    sourceReports.isEmpty() -> {
                        if (newReportRequest.reports != null) {
                            if (dateRange != null) {
                                throw BadAggregatedReportRequestException("No reports from the provided list fall within the specified date range")
                            } else {
                                throw BadAggregatedReportRequestException("Could not find reports with ids ${newReportRequest.reports}")
                            }
                        } else {
                            throw BadAggregatedReportRequestException("No reports found in the specified date range")
                        }
                    }
                    newReportRequest.reports != null && dateRange == null && sourceReports.size != newReportRequest.reports.size -> {
                        // Only check size mismatch if we're NOT using date range filtering
                        // (date range filtering may legitimately reduce the number of reports)
                        throw BadAggregatedReportRequestException(
                            "Could not find reports with ids ${newReportRequest.diff(sourceReports)}"
                        )
                    }
                    sourceReports.any { it.reportType == ReportType.AGGREGATED } -> {
                        throw BadAggregatedReportRequestException(
                            "report with ids ${sourceReports.aggregatedReports()} are aggregated reports and are not valid sources for a new aggregated report"
                        )
                    }
                }

                val organizationUnit = newReportRequest.teamId?.let { organizationRepository.getOrganizationUnit(it) }

                val newReport = AggregatedReport(
                    url = newReportRequest.url,
                    descriptiveName = newReportRequest.descriptiveName,
                    organizationUnit = organizationUnit,
                    reports = sourceReports,
                    user = call.user,
                    notes = newReportRequest.notes,
                ).let {
                    reportRepository.upsertReportReturning<AggregatedReport>(it)
                }
                call.respond(HttpStatusCode.Created, """{ "id": "${newReport.reportId}" }""".trimIndent())
            }
        }
        route("{id}") {
            patch {
                val updateReportRequest = call.receive<AggregatedReportUpdateRequest>()
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing report {id}")
                val originalReport = reportRepository.getReport<AggregatedReport>(id)
                val organizationUnit = updateReportRequest.teamId?.let { organizationRepository.getOrganizationUnit(it) }

                val updatedReport = originalReport?.updatedWith(
                    title = updateReportRequest.descriptiveName,
                    pageUrl = updateReportRequest.url,
                    notes = updateReportRequest.notes,
                    updateBy = call.user,
                    changedCriteria = updateReportRequest.successCriteria,
                    organizationUnit = organizationUnit
                ) ?: throw ResourceNotFoundException(type = "Aggregated Report", id = id)
                reportRepository.upsertReportReturning(updatedReport)
                call.respond(HttpStatusCode.OK)
            }
            delete {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing report {id}")
                reportRepository.deleteReport(id)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

private fun List<Report>.aggregatedReports() = filter { it.reportType == ReportType.AGGREGATED }

private fun parseIsoDateTime(dateString: String): LocalDateTime {
    return try {
        // Try parsing ISO 8601 format with time
        LocalDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
    } catch (e: DateTimeParseException) {
        try {
            // Try parsing just the date part (yyyy-MM-dd) and set time to start of day
            val date = java.time.LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
            date.atStartOfDay()
        } catch (e2: DateTimeParseException) {
            throw BadAggregatedReportRequestException(
                "Invalid date format. Expected ISO 8601 format (e.g., '2024-01-01' or '2024-01-01T00:00:00'). Got: $dateString"
            )
        }
    }
}
