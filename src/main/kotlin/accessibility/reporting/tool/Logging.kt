package accessibility.reporting.tool

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory
import kotlin.math.roundToLong

private val StartNanosKey = AttributeKey<Long>("request-start-nanos")

/**
 * Logs START/END for every request with status + duration.
 * Keeps it simple, but gives immediate operational insight.
 */
val RequestLifecycleLogging = createApplicationPlugin("RequestLifecycleLogging") {
    val log = LoggerFactory.getLogger("HttpAccess")

    onCall { call ->
        call.attributes.put(StartNanosKey, System.nanoTime())

        val origin = call.request.origin
        log.info(
            "START method={} uri={} callId={} remote={} ua={}",
            call.request.httpMethod.value,
            call.request.uri,
            call.callId,
            origin.remoteHost,
            call.request.headers[HttpHeaders.UserAgent]
        )
    }

    onCallRespond { call ->
        val start = call.attributes.getOrNull(StartNanosKey)
        val durMs = if (start != null) {
            ((System.nanoTime() - start) / 1_000_000.0).roundToLong()
        } else null

        log.info(
            "END method={} uri={} callId={} status={} durMs={}",
            call.request.httpMethod.value,
            call.request.uri,
            call.callId,
            call.response.status()?.value,
            durMs
        )
    }
}