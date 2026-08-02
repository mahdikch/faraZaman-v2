package Ir.co.tfs.farazaman.data.model

import com.google.gson.annotations.SerializedName

data class MissionIndexResponse(
    @SerializedName("request") val request: Map<String, Any>? = null,
    @SerializedName("response") val response: Map<String, Any>? = null,
    @SerializedName("pagination") val pagination: Map<String, Any>? = null,
    @SerializedName("data") val data: List<Map<String, Any>>? = null,
    @SerializedName("encryption") val encryption: Any? = null,
    @SerializedName("encryptions") val encryptions: Encryptions? = null
)

data class Encryptions(
    @SerializedName("update") val update: Map<String, String>? = null,
    @SerializedName("details") val details: Map<String, String>? = null
)
