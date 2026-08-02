package Ir.co.tfs.farazaman.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for VehicleZoneWork API
 */
data class VehicleZoneWorkResponse(
    @SerializedName("assetVehicleZoneWorkUseCase")
    val assetVehicleZoneWorkUseCase: AssetVehicleZoneWorkUseCase
)

data class AssetVehicleZoneWorkUseCase(
    @SerializedName("data")
    val data: List<VehicleZoneWorkItem>,
    @SerializedName("response")
    val response: VehicleZoneWorkResponseData?,
    @SerializedName("pagination")
    val pagination: Pagination?
)

data class VehicleZoneWorkItem(
    @SerializedName("zoneWorkVehicleID")
    val zoneWorkVehicleID: Int,
    @SerializedName("assetShift")
    val assetShift: AssetShift?,
    @SerializedName("assetVehicle")
    val assetVehicle: AssetVehicle,
    @SerializedName("contract")
    val contract: List<Contract>?,
    @SerializedName("organ")
    val organ: Organ,
    @SerializedName("organ1")
    val organ1: Organ?,
    @SerializedName("organIdFrom")
    val organIdFrom: Int?,
    @SerializedName("vehicleId")
    val vehicleId: Int?,
    @SerializedName("organIdTo")
    val organIdTo: Int?,
    @SerializedName("shiftId")
    val shiftId: Int?,
    @SerializedName("contractId")
    val contractId: Int?,
    @SerializedName("isDeleted")
    val isDeleted: Boolean,
    @SerializedName("createDate")
    val createDate: String?,
    @SerializedName("userName")
    val userName: String?,
    @SerializedName("startDate")
    val startDate: String?,
    @SerializedName("isActive")
    val isActive: Boolean
) {
    /**
     * Get display text for this zone work item
     * Format: "منطقه: [organName] - پلاک: [plakNumber] - شیفت: [shiftName]"
     */
    fun getDisplayText(): String {
        val organName = organ.organName ?: "نامشخص"
        val plakNumber = assetVehicle.plakNumber ?: "نامشخص"
        val shiftName = assetShift?.shiftName ?: "نامشخص"
        val contractCode = contract?.firstOrNull()?.contractCode ?: ""
        
        return buildString {
            append("منطقه: $organName")
            append("\n")
            append("پلاک: $plakNumber")
            if (contractCode.isNotEmpty()) {
                append(" - قرارداد: $contractCode")
            }
            append("\n")
            append("شیفت: $shiftName")
        }
    }
    
    /**
     * Get short summary for mission card
     * Format: "منطقه: [organName] | پلاک: [plakNumber]"
     */
    fun getShortSummary(): String {
        val organName = organ.organName ?: "نامشخص"
        val plakNumber = assetVehicle.plakNumber ?: "نامشخص"
        return "منطقه: $organName | پلاک: $plakNumber"
    }
}

data class AssetShift(
    @SerializedName("shiftID")
    val shiftID: Int,
    @SerializedName("shiftName")
    val shiftName: String?,
    @SerializedName("startHour")
    val startHour: Int?,
    @SerializedName("endHour")
    val endHour: Int?,
    @SerializedName("isDeleted")
    val isDeleted: Boolean,
    @SerializedName("createDate")
    val createDate: String?,
    @SerializedName("userName")
    val userName: String?
)

data class AssetVehicle(
    @SerializedName("vehicleID")
    val vehicleID: Int,
    @SerializedName("driverId")
    val driverId: Int?,
    @SerializedName("vehicleBrandId")
    val vehicleBrandId: Int?,
    @SerializedName("vehicleSystemId")
    val vehicleSystemId: Int?,
    @SerializedName("vehicleUsageId")
    val vehicleUsageId: Int?,
    @SerializedName("plakNumber")
    val plakNumber: String?,
    @SerializedName("plakCity")
    val plakCity: String?,
    @SerializedName("rfidCode")
    val rfidCode: String?,
    @SerializedName("vin")
    val vin: String?,
    @SerializedName("vehicleModelYear")
    val vehicleModelYear: String?,
    @SerializedName("isActive")
    val isActive: Boolean,
    @SerializedName("isDeleted")
    val isDeleted: Boolean,
    @SerializedName("assetVehicleBrand")
    val assetVehicleBrand: AssetVehicleBrand?,
    @SerializedName("assetVehicleUsage")
    val assetVehicleUsage: AssetVehicleUsage?
)

data class AssetVehicleBrand(
    @SerializedName("vehicleBrandID")
    val vehicleBrandID: Int,
    @SerializedName("vehicleBrandName")
    val vehicleBrandName: String?
)

data class AssetVehicleUsage(
    @SerializedName("vehicleUsageID")
    val vehicleUsageID: Int,
    @SerializedName("vehicleUsageName")
    val vehicleUsageName: String?
)

data class Contract(
    @SerializedName("contractID")
    val contractID: Int,
    @SerializedName("contractorId")
    val contractorId: Int?,
    @SerializedName("organId")
    val organId: Int?,
    @SerializedName("contractTypeId")
    val contractTypeId: Int?,
    @SerializedName("contractCode")
    val contractCode: String?,
    @SerializedName("isDeleted")
    val isDeleted: Boolean,
    @SerializedName("createDate")
    val createDate: String?,
    @SerializedName("userName")
    val userName: String?,
    @SerializedName("contractor")
    val contractor: Contractor?
)

data class Contractor(
    @SerializedName("contractorID")
    val contractorID: Int,
    @SerializedName("contractorName")
    val contractorName: String?
)

data class Organ(
    @SerializedName("organID")
    val organID: Int,
    @SerializedName("organTypeId")
    val organTypeId: Int?,
    @SerializedName("organName")
    val organName: String?,
    @SerializedName("isDeleted")
    val isDeleted: Boolean,
    @SerializedName("createDate")
    val createDate: String?,
    @SerializedName("userName")
    val userName: String?
)

data class VehicleZoneWorkResponseData(
    @SerializedName("statuses")
    val statuses: List<VehicleZoneDropdownItem>?,
    @SerializedName("stations")
    val stations: List<VehicleZoneDropdownItem>?,
    @SerializedName("organs")
    val organs: List<VehicleZoneDropdownItem>?,
    @SerializedName("usages")
    val usages: List<VehicleZoneDropdownItem>?,
    @SerializedName("shifts")
    val shifts: List<VehicleZoneDropdownItem>?,
    @SerializedName("contractors")
    val contractors: List<VehicleZoneDropdownItem>?,
    @SerializedName("zoneWorkVehicleStates")
    val zoneWorkVehicleStates: List<VehicleZoneDropdownItem>?,
    @SerializedName("contractStates")
    val contractStates: List<VehicleZoneDropdownItem>?
)

/**
 * Dropdown item for VehicleZoneWork API where value can be Boolean
 * (Different from DropdownItem in FormDataResponse which has Int value)
 */
data class VehicleZoneDropdownItem(
    @SerializedName("text")
    val text: String,
    @SerializedName("value")
    val value: Boolean,
    @SerializedName("selected")
    val selected: Boolean
)
