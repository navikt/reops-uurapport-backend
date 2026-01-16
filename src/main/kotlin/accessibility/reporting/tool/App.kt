package accessibility.reporting.tool

import accessibility.reporting.tool.authenitcation.AzureAuthContext
import accessibility.reporting.tool.authenitcation.installAuthentication
import accessibility.reporting.tool.database.Flyway
import accessibility.reporting.tool.database.OrganizationRepository
import accessibility.reporting.tool.database.PostgresDatabase
import accessibility.reporting.tool.database.ReportRepository
import accessibility.reporting.tool.rest.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.UUID

fun main() {
    val environment = Environment()
    val authContext = AzureAuthContext()
    Flyway.runFlywayMigrations(environment)
    val repository = ReportRepository(PostgresDatabase(environment))
    val organizationRepository = OrganizationRepository(PostgresDatabase(environment))
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toInt() ?: 8081,
        module = {
            api(
                corsAllowedOrigins = environment.corsAllowedOrigin,
                reportRepository = repository,
                organizationRepository = organizationRepository,
            ) {
                installAuthentication(authContext)
            }
        }
    ).start(wait = true)
}

fun Application.api(
    corsAllowedOrigins: List<String>,
    corsAllowedSchemes: List<String> = listOf("https"),
    reportRepository: ReportRepository,
    organizationRepository: OrganizationRepository,
    authInstaller: Application.() -> Unit
) {
    val prometehusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val log = KotlinLogging.logger {}

    authInstaller()

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.length in 8..128 }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(RequestLifecycleLogging)

    install(CallLogging) {
        level = Level.INFO
        logger = LoggerFactory.getLogger("CallLogging")

        mdc("callId") { it.callId }
        mdc("method") { it.request.httpMethod.value }
        mdc("uri") { it.request.uri }

        filter { call ->
            val path = call.request.path()
            path != "/isalive" && path != "/open/metrics"
        }

        format { call ->
            "${call.request.httpMethod.value} ${call.request.uri} -> ${call.response.status() ?: "unhandled"}"
        }
    }

    install(CORS) {
        corsAllowedOrigins.forEach { allowedHost ->
            allowHost(host = allowedHost, schemes = corsAllowedSchemes)
        }
        allowHeaders { true }
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            val callId = call.callId

            when (cause) {
                is IllegalArgumentException,
                is BadRequestException -> {
                    log.warn(cause) { "4xx method=$method uri=$uri callId=$callId msg=${cause.message}" }
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = cause.message ?: "Bad request"
                    )
                }

                is RequestException -> {
                    log.error(cause) { "RequestException method=$method uri=$uri callId=$callId" }
                    call.respondText(status = cause.responseStatus, text = cause.message!!)
                }

                else -> {
                    log.error(cause) { "5xx method=$method uri=$uri callId=$callId" }
                    call.respondText(
                        status = HttpStatusCode.InternalServerError,
                        text = "500: Internal server error"
                    )
                }
            }
        }
    }

    routing {
        authenticate {
            route("api") {
                jsonApiReports(organizationRepository = organizationRepository, reportRepository = reportRepository)
                jsonapiteams(organizationRepository = organizationRepository)
                jsonApiUsers(organizationRepository = organizationRepository, reportRepository = reportRepository)
                jsonapiadmin(reportRepository = reportRepository, organizationRepository = organizationRepository)
                jsonApiAggregatedReports(reportRepository = reportRepository)
            }
        }

        meta(prometehusRegistry)

        staticResources("/static", "static") {
            preCompressed(CompressedFileType.GZIP)
        }
    }
}

class Environment(
    val environment: String = System.getenv("ENVIRONMENT") ?: "local",
    val dbHost: String = System.getenv("DB_HOST") ?: "",
    val dbPort: String = System.getenv("DB_PORT") ?: "",
    val dbName: String = System.getenv("DB_DATABASE") ?: "",
    val dbUser: String = System.getenv("DB_USERNAME") ?: "",
    val dbPassword: String = System.getenv("DB_PASSWORD") ?: "",
    val corsAllowedOrigin: List<String> = System.getenv("CORS_ALLOWED_ORIGIN").split(",")
) {
    val dbUrl: String = if (environment == "local") {
        "jdbc:postgresql://${dbHost}:${dbPort}/${dbName}"
    } else {
        System.getenv("DATABASE_URL") ?: throw IllegalArgumentException("DATABASE_URL not set")
    }
}