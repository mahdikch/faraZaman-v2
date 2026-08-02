package Ir.co.tfs.farazaman.data.model

import com.google.gson.annotations.SerializedName

data class UserRolesResponse(
    @SerializedName("roles")
    val roles: Map<String, String> // Map of Persian name to English name
)

// Helper class to determine user access based on roles
data class UserAccess(
    val hasDriverAccess: Boolean,
    val hasSupervisorAccess: Boolean,
    val isSuperAdmin: Boolean
) {
    fun canAccessBoth(): Boolean = isSuperAdmin || (hasDriverAccess && hasSupervisorAccess)
    fun canAccessOnlyDriver(): Boolean = hasDriverAccess && !hasSupervisorAccess && !isSuperAdmin
    fun canAccessOnlySupervisor(): Boolean = hasSupervisorAccess && !hasDriverAccess && !isSuperAdmin
}

