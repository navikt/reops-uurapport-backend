package accessibility.reporting.tool.rest

import accessibility.reporting.tool.authentication.AdminCheck
import accessibility.reporting.tool.database.OrganizationRepository
import accessibility.reporting.tool.database.ReportRepository
import accessibility.reporting.tool.wcag.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.jsonapiadmin(reportRepository: ReportRepository, organizationRepository: OrganizationRepository) {
    route("admin") {
        install(AdminCheck)
        delete("teams/{id}") {
            organizationRepository.deleteOrgUnit(call.parameters["id"] ?: throw BadPathParameterException("id"))
            call.respond(HttpStatusCode.OK)
        }
        get("reports/diagnostics") {
            val allReports = reportRepository.getReports<Report>()
            val reportsWithNullOrg = allReports.filter { it.organizationUnit == null }
            
            call.respond(mapOf(
                "totalReports" to allReports.size,
                "reportsWithNullOrganizationUnit" to reportsWithNullOrg.size,
                "nullOrgReportIds" to reportsWithNullOrg.map { 
                    mapOf(
                        "reportId" to it.reportId,
                        "url" to it.url,
                        "descriptiveName" to (it.descriptiveName ?: "N/A"),
                        "reportType" to it.reportType.name
                    )
                }
            ))
        }
        aggregatedAdminRoutes(reportRepository, organizationRepository)
    }
}


class NewAggregatedReportRequest(
    val descriptiveName: String,
    val url: String,
    val reports: List<String>? = null,
    val notes: String,
    val teamId: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
) {
    fun diff(foundReports: List<ReportContent>): String {
        val foundIds = foundReports.map { it.reportId }
        return (reports ?: emptyList()).filterNot { foundIds.contains(it) }.joinToString(",")
    }

    fun validate() {
        when {
            reports != null && reports.isNotEmpty() -> {
                // Using explicit report IDs - valid
                // If date range is also provided, it will filter these IDs by the date range
            }
            startDate != null && endDate != null -> {
                // Using date range - valid
            }
            else -> throw BadAggregatedReportRequestException(
                "Must provide either 'reports' (list of report IDs), both 'startDate' and 'endDate', or both"
            )
        }
    }
}

data class AggregatedReportUpdateRequest(
    val descriptiveName: String? = null,
    val url: String? = null,
    val successCriteria: List<SuccessCriterionUpdate>? = null,
    val notes: String? = null,
    val teamId: String? = null
)

