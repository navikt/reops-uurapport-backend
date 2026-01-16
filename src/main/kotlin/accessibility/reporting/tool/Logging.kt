package accessibility.reporting.tool

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

private val StartNanosKey = AttributeKey<Long>("request-start-nanos")
private val ignoredPaths = setOf("/isalive", "/isready", "/open/metrics")

val RequestLifecycleLogging = createApplicationPlugin("RequestLifecycleLogging") {
    val log = LoggerFactory.getLogger("HttpAccess")

    onCall { call ->
        val path = call.request.path()
        if (path in ignoredPaths) return@onCall

        call.attributes.put(StartNanosKey, System.nanoTime())

        val origin = call.request.origin
        log.info(
            "START method={} uri={} callId={} remote={} ua={}",
            call.request.httpMethod.value,
            path,
            call.callId,
            origin.remoteHost,
            call.request.headers[HttpHeaders.UserAgent]
        )
    }

    onCallRespond { call ->
        val path = call.request.path()
        if (path in ignoredPaths) return@onCallRespond

        val start = call.attributes.getOrNull(StartNanosKey)
        val durMs = if (start != null) {
            ((System.nanoTime() - start) / 1_000_000.0).toLong()
        } else null

        log.info(
            "END method={} uri={} callId={} status={} durMs={}",
            call.request.httpMethod.value,
            path,
            call.callId,
            call.response.status()?.value,
            durMs
        )
    }
}