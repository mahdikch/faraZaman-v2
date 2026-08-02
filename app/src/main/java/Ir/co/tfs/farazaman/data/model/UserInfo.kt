package Ir.co.tfs.farazaman.data.model

import com.google.gson.annotations.SerializedName

data class UserInfoResponse(
    @SerializedName("user")
    val user: UserInfo
)

data class UserInfo(
    @SerializedName("userName")
    val userName: String,
    
    @SerializedName("displayName")
    val displayName: String,
    
    @SerializedName("defaultSubsystem")
    val defaultSubsystem: Int,
    
    @SerializedName("defaultRoleName")
    val defaultRoleName: String
)

