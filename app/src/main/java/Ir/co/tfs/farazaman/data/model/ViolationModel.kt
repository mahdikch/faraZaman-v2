// در فایل ViolationModels.kt

package Ir.co.tfs.farazaman.data.model

import com.google.gson.annotations.SerializedName

// مدل برای بدنه اصلی درخواست
data class SubmitViolationRequest(
    val request: ViolationData
)

// مدل جدید برای پاسخ موفقیت‌آمیز
data class ViolationSuccessResponse(
    val billOriginEventId: Long
)

// مدلی که قبلاً داشتیم را برای مدیریت پیام‌های خطا استفاده می‌کنیم
data class ViolationErrorResponse(
    @SerializedName("Message")
    val message: String?
)

// New error response class that matches the actual API error response structure
data class ApiErrorResponse(
    @SerializedName("data")
    val data: ErrorData?,
    @SerializedName("message")
    val message: String?
)

data class ErrorData(
    @SerializedName("id")
    val id: String?
)

// مدل برای محتوای داخلی درخواست - Updated to match working Postman request
data class ViolationData(
    @SerializedName("organIds")
    val organIds: List<Int>,
    @SerializedName("contractIds")
    val contractIds: List<Int>,
    @SerializedName("billCleaningViolationGroupIds")
    val billCleaningViolationGroupIds: List<Int>,
    @SerializedName("billCleaningViolationIds")
    val billCleaningViolationIds: List<Int>,
    @SerializedName("billCleaningItemGroupIds")
    val billCleaningItemGroupIds: List<Int>,
    @SerializedName("billCleaningItemIds")
    val billCleaningItemIds: List<Int>,
    @SerializedName("visitDate")
    val visitDate: String,
    @SerializedName("billOriginCleaningItemIds")
    val billOriginCleaningItemIds: List<Int>,
    @SerializedName("number")
    val number: Int,
    @SerializedName("visitedFault")
    val visitedFault: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("billCleaningViolationId")
    val billCleaningViolationId: Int,
    @SerializedName("contractId")
    val contractId: Int,
    @SerializedName("organId")
    val organId: Int,
    @SerializedName("billCleaningViolationGroupId")
    val billCleaningViolationGroupId: Int,
    @SerializedName("billCleaningItemGroupId")
    val billCleaningItemGroupId: Int,
    @SerializedName("billOriginCleaningItemId")
    val billOriginCleaningItemId: Int,
    @SerializedName("TenantId")
    val tenantId: Int,
    @SerializedName("BillOriginViEventGeoLocation")
    val billOriginViEventGeoLocation: BillOriginViEventGeoLocation
)

// مدل برای پاسخ سرور
data class SubmitViolationResponse(
    @SerializedName("Data")
    val data: ResponseData?,
    @SerializedName("Message")
    val message: String?,
    @SerializedName("HelpLink")
    val helpLink: String?
)

data class ResponseData(
    @SerializedName("id")
    val id: String
)

// New GPS location data structures for the updated API
data class BillOriginViEventGeoLocation(
    @SerializedName("GeoJson")
    val geoJson: String,
    @SerializedName("WellKnownText")
    val wellKnownText: String,
    @SerializedName("GisGeolocationTrack")
    val gisGeolocationTrack: GisGeolocationTrack
)

data class GisGeolocationTrack(
    @SerializedName("Latitude")
    val latitude: Double,
    @SerializedName("Longitude")
    val longitude: Double,
    @SerializedName("Elevation")
    val elevation: Double,
    @SerializedName("Time")
    val time: String,
    @SerializedName("MagneticVariant")
    val magneticVariant: Double? = null,
    @SerializedName("C_Baro")
    val cBaro: Double,
    @SerializedName("Accuracy")
    val accuracy: Double,
    @SerializedName("AltitudeAccuracy")
    val altitudeAccuracy: Double,
    @SerializedName("Heading")
    val heading: Double,
    @SerializedName("Speed")
    val speed: Double,
    @SerializedName("Type")
    val type: String,
    @SerializedName("SatelliteNumber")
    val satelliteNumber: Int
)
