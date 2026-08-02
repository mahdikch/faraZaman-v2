package Ir.co.tfs.farazaman.data.model

import com.google.gson.annotations.SerializedName

// Root response for GisLayer/Index
// Only the 'data' field is needed for SpeedDial items

data class GisLayerIndexResponse(
    @SerializedName("data") val data: List<GisLayerItem>
)

data class GisLayerItem(
    @SerializedName("gisLayersId") val gisLayersId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("userName") val userName: String?,
    @SerializedName("createDate") val createDate: String?,
    @SerializedName("isDeleted") val isDeleted: Boolean,
    @SerializedName("icon") val icon: String?,
    @SerializedName("pickUpByApp") val pickUpByApp: Boolean?
) 
