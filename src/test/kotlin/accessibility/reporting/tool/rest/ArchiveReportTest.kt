package accessibility.reporting.tool.rest

import accessibility.reporting.tool.*
import accessibility.reporting.tool.assert
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArchiveReportTest : TestApi() {
    private val owner = TestUser(name = "Owner Ownerson")
    private val stranger = TestUser(name = "Stranger Strangerson")
    private val admin = TestUser.createAdminUser("some@admin.test", "Admin Adminser")

    @BeforeEach
    fun clean() = cleanDb()

    @Test
    fun `owner can archive and restore a report`() = withTestApi {
        val report = reportRepository.upsertReport(dummyReportV4(user = owner))

        client.postWithJwtUser(owner, "api/reports/${report.reportId}/archive") {
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"Appen er nedlagt"}""")
        }.status shouldBe HttpStatusCode.OK

        client.getWithJwtUser(owner, "api/reports").assert { json().toList().size shouldBe 0 }
        client.getWithJwtUser(owner, "api/reports/archived").assert {
            val items = json().toList()
            items.size shouldBe 1
            items.first()["id"].asText() shouldBe report.reportId
            items.first()["archive"]["reason"].asText() shouldBe "Appen er nedlagt"
            items.first()["archive"]["archivedBy"].asText() shouldBe owner.email.str()
            items.first()["canRestore"].asBoolean() shouldBe true
        }
        client.getWithJwtUser(owner, "api/reports/${report.reportId}").assert {
            json()["archive"]["reason"].asText() shouldBe "Appen er nedlagt"
        }

        client.deleteWithJwtUser(owner, "api/reports/${report.reportId}/archive").status shouldBe HttpStatusCode.OK

        client.getWithJwtUser(owner, "api/reports").assert { json().toList().size shouldBe 1 }
        client.getWithJwtUser(owner, "api/reports/archived").assert { json().toList().size shouldBe 0 }
        client.getWithJwtUser(owner, "api/reports/${report.reportId}").assert {
            json()["archive"].isNull shouldBe true
        }
    }

    @Test
    fun `reason is optional`() = withTestApi {
        val report = reportRepository.upsertReport(dummyReportV4(user = owner))

        client.postWithJwtUser(owner, "api/reports/${report.reportId}/archive") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.status shouldBe HttpStatusCode.OK

        client.getWithJwtUser(owner, "api/reports/archived").assert {
            json().toList().first()["archive"]["reason"].isNull shouldBe true
        }
    }

    @Test
    fun `user without write access cannot archive or restore`() = withTestApi {
        val report = reportRepository.upsertReport(dummyReportV4(user = owner))

        client.postWithJwtUser(stranger, "api/reports/${report.reportId}/archive") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.status shouldBe HttpStatusCode.Forbidden

        reportRepository.archiveReport(report.reportId, owner.email.str(), null)

        client.deleteWithJwtUser(stranger, "api/reports/${report.reportId}/archive").status shouldBe HttpStatusCode.Forbidden
        client.getWithJwtUser(stranger, "api/reports/archived").assert {
            json().toList().first()["canRestore"].asBoolean() shouldBe false
        }
    }

    @Test
    fun `archived report cannot be edited`() = withTestApi {
        val report = reportRepository.upsertReport(dummyReportV4(user = owner))
        reportRepository.archiveReport(report.reportId, owner.email.str(), null)

        client.patchWithJwtUser(owner, "api/reports/${report.reportId}") {
            contentType(ContentType.Application.Json)
            setBody("""{"descriptiveName":"nytt navn"}""")
        }.status shouldBe HttpStatusCode.Conflict
    }

    @Test
    fun `only admin can hard delete a report, also when archived`() = withTestApi {
        val report = reportRepository.upsertReport(dummyReportV4(user = owner))

        client.deleteWithJwtUser(owner, "api/admin/reports/${report.reportId}").status shouldBe HttpStatusCode.Forbidden
        client.getWithJwtUser(owner, "api/reports/${report.reportId}").status shouldBe HttpStatusCode.OK

        client.deleteWithJwtUser(admin, "api/admin/reports/${report.reportId}").status shouldBe HttpStatusCode.OK
        client.getWithJwtUser(owner, "api/reports/${report.reportId}").status shouldBe HttpStatusCode.NotFound

        val archived = reportRepository.upsertReport(dummyReportV4(user = owner))
        reportRepository.archiveReport(archived.reportId, owner.email.str(), null)
        client.deleteWithJwtUser(admin, "api/admin/reports/${archived.reportId}").status shouldBe HttpStatusCode.OK
        client.getWithJwtUser(owner, "api/reports/archived").assert { json().toList().size shouldBe 0 }
    }

    @Test
    fun `hard delete of unknown report is 404`() = withTestApi {
        client.deleteWithJwtUser(admin, "api/admin/reports/does-not-exist").status shouldBe HttpStatusCode.NotFound
    }
}
