package accessibility.reporting.tool.rest

import accessibility.reporting.tool.authenitcation.Admins
import accessibility.reporting.tool.authenitcation.User
import accessibility.reporting.tool.authenitcation.user
import accessibility.reporting.tool.database.OrganizationRepository
import accessibility.reporting.tool.database.ReportRepository
import accessibility.reporting.tool.wcag.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
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
        aggregatedAdminRoutes(reportRepository)
    }
}


val AdminCheck = createRouteScopedPlugin("adminCheck") {
    on(AuthenticationChecked) { call ->
        val user = call.user
        if (!user.isAdmin())
            throw NotAdminUserException(route = call.request.uri, userName = user.username)
    }
}


class NewAggregatedReportRequest(
    val descriptiveName: String,
    val url: String,
    val reports: List<String>,
    val notes: String
) {
    fun diff(foundReports: List<ReportContent>): String {
        val foundIds = foundReports.map { it.reportId }
        return reports.filterNot { foundIds.contains(it) }.joinToString(",")
    }
}

data class AggregatedReportUpdateRequest(
    val descriptiveName: String? = null,
    val url: String? = null,
    val successCriteria: List<SuccessCriterionUpdate>? = null,
    val notes: String? = null
)


