package accessibility.reporting.tool.rest

import accessibility.reporting.tool.wcag.*
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import accessibility.reporting.tool.database.ArchiveInfo
import accessibility.reporting.tool.database.ArchivedReport
import java.time.LocalDateTime

data class ValidationWarning(
    val field: String,
    val message: String,
    val severity: String = "warning"
)

class ReportListItem(
    @JsonProperty("id")
    override val reportId: String,
    @JsonProperty("title")
    override val descriptiveName: String?,
    override val url: String,
    val teamId: String,
    val teamName:String,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @JsonProperty("date")
    val lastChanged: LocalDateTime,
    val isPartOfNavNo: Boolean,
    val reportType: String
) : ReportContent {

    companion object {
        fun fromJson(jsonNode: JsonNode) = ReportListItem(
            reportId = jsonNode.reportId,
            descriptiveName = jsonNode.descriptiveName,
            url = jsonNode.url,
            teamId = jsonNode.organizationUnit()?.id ?: "",
            teamName = jsonNode.organizationUnit()?.name ?: "",
            lastChanged = jsonNode.lastChangedOrDefault(),
            isPartOfNavNo = jsonNode.isPartOfNavNo,
            reportType = jsonNode["reportType"]?.asText() ?: "SINGLE"
        )
    }
}

class ArchiveInfoResponse(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val archivedAt: LocalDateTime,
    val archivedBy: String,
    val reason: String?
) {
    constructor(info: ArchiveInfo) : this(info.archivedAt, info.archivedBy, info.reason)
}

class ArchivedReportListItem(
    @JsonProperty("id")
    val reportId: String,
    @JsonProperty("title")
    val descriptiveName: String?,
    val url: String,
    val teamId: String,
    val teamName: String,
    val reportType: String,
    val archive: ArchiveInfoResponse,
    val canRestore: Boolean
) {
    constructor(archived: ArchivedReport, canRestore: Boolean) : this(
        reportId = archived.report.reportId,
        descriptiveName = archived.report.descriptiveName,
        url = archived.report.url,
        teamId = archived.report.organizationUnit?.id ?: "",
        teamName = archived.report.organizationUnit?.name ?: "",
        reportType = archived.report.reportType.name,
        archive = ArchiveInfoResponse(archived.info),
        canRestore = canRestore
    )
}
