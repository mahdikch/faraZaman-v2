package Ir.co.tfs.farazaman.data.model

import com.google.gson.annotations.SerializedName

data class FormDataResponse(
    @SerializedName("dto") val dto: FormDataDto,
    @SerializedName("request") val request: FormDataRequest,
    @SerializedName("response") val response: FormDataResponseData,
    @SerializedName("pagination") val pagination: Pagination,
    @SerializedName("encryption") val encryption: Map<String, Any>,
    @SerializedName("encryptions") val encryptions: Map<String, Any>
)

data class FormDataDto(
    @SerializedName("violationLimit") val violationLimit: String,
    @SerializedName("violationLimitText") val violationLimitText: String,
    @SerializedName("frequentlyViolations") val frequentlyViolations: List<Any>,
    @SerializedName("billCleaningItemImportanceRate") val billCleaningItemImportanceRate: List<Any>? = null,
    @SerializedName("factors") val factors: String,
    @SerializedName("violationGroupOverheadRatio") val violationGroupOverheadRatio: String,
    @SerializedName("billOriginOverheadRatio") val billOriginOverheadRatio: String,
    @SerializedName("contractFactor") val contractFactor: String,
    @SerializedName("daysOfMonth") val daysOfMonth: String,
    @SerializedName("costType") val costType: String,
    @SerializedName("increaseRatio") val increaseRatio: String,
    @SerializedName("overheadRatio") val overheadRatio: String,
    @SerializedName("repeatFineValue") val repeatFineValue: String,
    @SerializedName("unitPrice") val unitPrice: String,
    @SerializedName("dailyItemPrice") val dailyItemPrice: String,
    @SerializedName("baseValue") val baseValue: String,
    @SerializedName("workLoad") val workLoad: String,
    @SerializedName("checkWorkLoad") val checkWorkLoad: String,
    @SerializedName("sumNumber") val sumNumber: String,
    @SerializedName("sumPrice") val sumPrice: String,
    @SerializedName("costTypeTitle") val costTypeTitle: String,
    @SerializedName("maxFinePercent") val maxFinePercent: String,
    @SerializedName("unit") val unit: String,
    @SerializedName("finePrice") val finePrice: String,
    @SerializedName("finePercent") val finePercent: String,
    @SerializedName("itemType") val itemType: String,
    @SerializedName("gisGeolocationTracking") val gisGeolocationTracking: String,
    @SerializedName("geoFence") val geoFence: String,
    @SerializedName("fixedAmount") val fixedAmount: String? = null
)

data class FormDataRequest(
    @SerializedName("contractIds") val contractIds: List<Int>,
    @SerializedName("organIds") val organIds: List<Int>,
    @SerializedName("billCleaningViolationGroupIds") val billCleaningViolationGroupIds: List<Int>,
    @SerializedName("billCleaningViolationIds") val billCleaningViolationIds: List<Int>,
    @SerializedName("billCleaningItemGroupIds") val billCleaningItemGroupIds: List<Int>,
    @SerializedName("billCleaningItemIds") val billCleaningItemIds: List<Int>,
    @SerializedName("visitDate") val visitDate: String,
    @SerializedName("maxVisitDate") val maxVisitDate: String,
    @SerializedName("minVisitDate") val minVisitDate: String,
    @SerializedName("isDeleted") val isDeleted: Boolean,
    @SerializedName("tenantId") val tenantId: Int,
    @SerializedName("billOriginCleaningItemIds") val billOriginCleaningItemIds: List<Int>,
    @SerializedName("billCleaningViolationId") val billCleaningViolationId: Int,
    @SerializedName("contractId") val contractId: Int,
    @SerializedName("organId") val organId: Int,
    @SerializedName("number") val number: Int? = null,
    @SerializedName("visitedFault") val visitedFault: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("billCleaningViolationGroupId") val billCleaningViolationGroupId: Int? = null,
    @SerializedName("billCleaningItemGroupId") val billCleaningItemGroupId: Int? = null,
    @SerializedName("billOriginCleaningItemId") val billOriginCleaningItemId: Int? = null,
    @SerializedName("TenantId") val TenantId: Int? = null
)

data class FormDataResponseData(
    @SerializedName("organs") val organs: List<DropdownItem>,
    @SerializedName("contracts") val contracts: List<DropdownItem>,
    @SerializedName("billCleaningViolationGroups") val billCleaningViolationGroups: List<DropdownItem>,
    @SerializedName("billCleaningItemGroups") val billCleaningItemGroups: List<DropdownItem>,
    @SerializedName("billCleaningViolations") val billCleaningViolations: List<DropdownItem>,
    @SerializedName("billOriginCleaningItems") val billOriginCleaningItems: List<DropdownItem>
)

data class DropdownItem(
    @SerializedName(value = "text", alternate = ["Text"])
    val text: String,
    @SerializedName(value = "value", alternate = ["Value"])
    val value: Int,
    @SerializedName(value = "selected", alternate = ["Selected"])
    val selected: Boolean,
)

data class Pagination(
    @SerializedName("page") val page: PageInfo
)

data class PageInfo(
    @SerializedName("currentPage") val currentPage: Int,
    @SerializedName("pageCount") val pageCount: Int,
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("pageSize") val pageSize: Int,
    @SerializedName("skip") val skip: Int,
    @SerializedName("take") val take: Int,
    @SerializedName("sortExpression") val sortExpression: String
)

data class FormDataApiRequest(
    @SerializedName("request") val request: FormDataRequest
)

data class RequestData(
    @SerializedName("tenantId") val tenantId: Int
) 
