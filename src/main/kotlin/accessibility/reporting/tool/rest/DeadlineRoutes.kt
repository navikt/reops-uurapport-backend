package accessibility.reporting.tool.rest

import accessibility.reporting.tool.database.SettingsRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

class DeadlineResponse(val deadline: String?)

class DeadlineRequest(val deadline: String? = null)

fun Route.jsonApiDeadline(settingsRepository: SettingsRepository) {
    get("deadline") {
        call.respond(DeadlineResponse(settingsRepository.getDeadline()?.toString()))
    }
}

fun Route.deadlineAdminRoutes(settingsRepository: SettingsRepository) {
    patch("deadline") {
        val request = call.receive<DeadlineRequest>()
        val deadline = try {
            request.deadline?.let { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            throw BadDeadlineException("deadline must be formatted as yyyy-MM-dd or null")
        }
        settingsRepository.setDeadline(deadline)
        call.respond(HttpStatusCode.OK, DeadlineResponse(deadline?.toString()))
    }
}
