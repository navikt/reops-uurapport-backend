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

fun Route.aggregatedAdminRoutes(reportRepository: ReportRepository, organizationRepository: OrganizationRepository) {
    route("reports/aggregated") {
        install(AdminCheck)
        route("new") {
            post {
                val newReportRequest = call.receive<NewAggregatedReportRequest>()
                val sourceReports = reportRepository.getReports<Report>(ids = newReportRequest.reports)
                when {
                    sourceReports.isEmpty() -> throw BadAggregatedReportRequestException("Could not find reports with ids ${newReportRequest.reports}")
                    sourceReports.size != newReportRequest.reports.size -> throw BadAggregatedReportRequestException(
                        "Could not find reports with ids ${newReportRequest.diff(sourceReports)}"
                    )

                    sourceReports.any { it.reportType == ReportType.AGGREGATED } -> throw BadAggregatedReportRequestException(
                        "report with ids ${sourceReports.aggregatedReports()} are aggregated reports and are not valid sources for a new aggregated report"
                    )
                }

                val organizationUnit = newReportRequest.teamId?.let { organizationRepository.getOrganizationUnit(it) }

                val newReport = AggregatedReport(
                    url = newReportRequest.url,
                    descriptiveName = newReportRequest.descriptiveName,
                    organizationUnit = organizationUnit,
                    reports = reportRepository.getReports<Report>(ids = newReportRequest.reports),
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