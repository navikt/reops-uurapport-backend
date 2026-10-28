package accessibility.reporting.tool.rest

import accessibility.reporting.tool.*
import accessibility.reporting.tool.assert
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotliquery.queryOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminListTest : TestApi() {
    private val admin = TestUser.createAdminUser("some@admin.test", "Admin Adminser")
    private val otherAdmin = TestUser.createAdminUser("zed@admin.test", "Zed Zedson")
    private val user = TestUser(name = "Vanlig Bruker")

    @BeforeEach
    fun clean() = cleanDb()

    @Test
    fun `admins appear in list after using the app, normal users do not`() = withTestApi {
        client.getWithJwtUser(user, "api/admins").assert { json().toList().size shouldBe 0 }

        client.getWithJwtUser(user, "api/user").status shouldBe HttpStatusCode.OK
        client.getWithJwtUser(otherAdmin, "api/user").status shouldBe HttpStatusCode.OK
        client.getWithJwtUser(admin, "api/user").status shouldBe HttpStatusCode.OK
        client.getWithJwtUser(admin, "api/user").status shouldBe HttpStatusCode.OK

        client.getWithJwtUser(user, "api/admins").assert {
            val admins = json().toList()
            admins.map { it["email"].asText() } shouldBe listOf("some@admin.test", "zed@admin.test")
            admins.first()["name"].asText() shouldBe "Admin Adminser"
        }
    }

    @Test
    fun `admins not seen for a long time are left out`() = withTestApi {
        client.getWithJwtUser(admin, "api/user")
        database.update { queryOf("update admin_seen set last_seen = now() - interval '200 days'") }

        client.getWithJwtUser(user, "api/admins").assert { json().toList().size shouldBe 0 }
    }
}
