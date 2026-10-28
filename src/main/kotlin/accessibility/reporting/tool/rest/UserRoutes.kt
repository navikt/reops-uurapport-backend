package accessibility.reporting.tool.rest

import accessibility.reporting.tool.authentication.User
import accessibility.reporting.tool.authentication.user
import accessibility.reporting.tool.database.AdminRepository
import accessibility.reporting.tool.database.ReportRepository
import accessibility.reporting.tool.database.OrganizationRepository
import accessibility.reporting.tool.wcag.*
import io.ktor.server.response.*
import mu.KotlinLogging
import io.ktor.server.routing.*

private val log = KotlinLogging.logger {}

fun Route.jsonApiUsers(reportRepository: ReportRepository, organizationRepository: OrganizationRepository) {
    val adminRepository = AdminRepository(reportRepository.database)

    get("admins") {
        call.respond(adminRepository.getRecentAdmins())
    }
    //TODO remove
    route("users") {
        get("details") {
            val reports = reportRepository.getReportsForUser<ReportListItem>(call.user.oid)
                .sortedBy { it.descriptiveName?.lowercase() ?: it.url }
            val teams = organizationRepository.getOrganizationForUser(call.user.email)
            val userDetails =
                UserDetails(call.user, reports = reports, teams = teams)
            call.respond(userDetails)
        }
    }

    get("user") {
        if (call.user.isAdmin()) {
            try {
                adminRepository.recordSeen(call.user)
            } catch (e: Exception) {
                log.warn(e) { "Could not record admin activity" }
            }
        }
        val reports = reportRepository.getReportsForUser<ReportListItem>(call.user.oid)
            .sortedBy { it.descriptiveName?.lowercase() ?: it.url }
        val teams = organizationRepository.getOrganizationForUser(call.user.email)
        val userDetails =
            UserDetails(call.user, reports = reports, teams = teams)
        call.respond(userDetails)
    }
}

class UserDetails(
    user: User,
    val reports: List<ReportListItem>,
    val teams: List<OrganizationUnit>
) {
    val name = user.name
    val email = user.email
    val isAdmin = user.isAdmin()
}

