package accessibility.reporting.tool.rest

import accessibility.reporting.tool.*
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeadlineTest : TestApi() {
    private val admin = TestUser.createAdminUser("some@admin.test", "Admin Adminser")
    private val user = TestUser(name = "Vanlig Bruker")

    @BeforeEach
    fun clean() = cleanDb()

    private suspend fun io.ktor.client.HttpClient.setDeadline(who: TestUser, body: String) =
        patchWithJwtUser(who, "api/admin/deadline") {
            setBody(body)
            contentType(ContentType.Application.Json)
        }

    @Test
    fun `no deadline is set by default`() = withTestApi {
        client.getWithJwtUser(user, "api/deadline").assert {
            status shouldBe HttpStatusCode.OK
            json()["deadline"].isNull shouldBe true
        }
    }

    @Test
    fun `admin can set and clear the deadline`() = withTestApi {
        client.setDeadline(admin, """{"deadline":"2026-12-01"}""").status shouldBe HttpStatusCode.OK
        client.getWithJwtUser(user, "api/deadline").assert { json()["deadline"].asText() shouldBe "2026-12-01" }

        client.setDeadline(admin, """{"deadline":null}""").status shouldBe HttpStatusCode.OK
        client.getWithJwtUser(user, "api/deadline").assert { json()["deadline"].isNull shouldBe true }
    }

    @Test
    fun `normal user cannot set the deadline`() = withTestApi {
        client.setDeadline(user, """{"deadline":"2026-12-01"}""").status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `malformed deadline is rejected`() = withTestApi {
        client.setDeadline(admin, """{"deadline":"tomorrow"}""").status shouldBe HttpStatusCode.BadRequest
    }
}
