package Ir.co.tfs.farazaman.util

import android.content.SharedPreferences
import Ir.co.tfs.farazaman.data.model.UserAccess
import Ir.co.tfs.farazaman.data.model.UserRolesResponse
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RolesManager @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson
) {
    companion object {
        private const val KEY_USER_ROLES = "user_roles"

        private const val ROLE_ASSET_DRIVER = "AssetDriver"
        private const val ROLE_MUNICIPAL_CONTRACT_SUPERVISOR = "MunicipalContractSupervisor"
        private const val ROLE_SUPER_ADMINISTRATOR = "superAdministrator"
    }

    fun saveUserRoles(rolesResponse: UserRolesResponse) {
        val rolesJson = gson.toJson(rolesResponse)
        sharedPreferences.edit().putString(KEY_USER_ROLES, rolesJson).apply()
    }

    fun getUserRoles(): UserRolesResponse? {
        val rolesJson = sharedPreferences.getString(KEY_USER_ROLES, null)
        return rolesJson?.let {
            gson.fromJson(it, UserRolesResponse::class.java)
        }
    }

    fun getUserAccess(): UserAccess {
        val roles = getUserRoles()?.roles ?: emptyMap()

        return UserAccess(
            hasDriverAccess = hasDriverRoleInMap(roles),
            hasSupervisorAccess = hasSupervisorRoleInMap(roles),
            isSuperAdmin = hasRoleInMap(roles, ROLE_SUPER_ADMINISTRATOR),
        )
    }

    fun hasDriverRole(): Boolean {
        val access = getUserAccess()
        return access.hasDriverAccess || access.isSuperAdmin
    }

    fun hasSupervisorRole(): Boolean {
        val access = getUserAccess()
        return access.hasSupervisorAccess || access.isSuperAdmin
    }

    fun canSwitchRoles(): Boolean {
        val access = getUserAccess()
        if (access.isSuperAdmin) return true
        return access.hasDriverAccess && access.hasSupervisorAccess
    }

    fun clearUserRoles() {
        sharedPreferences.edit().remove(KEY_USER_ROLES).apply()
    }

    fun hasAnyRole(): Boolean {
        val access = getUserAccess()
        return access.hasDriverAccess || access.hasSupervisorAccess || access.isSuperAdmin
    }

    private fun hasDriverRoleInMap(roles: Map<String, String>): Boolean =
        hasRoleInMap(roles, ROLE_ASSET_DRIVER)

    private fun hasSupervisorRoleInMap(roles: Map<String, String>): Boolean =
        hasRoleInMap(roles, ROLE_MUNICIPAL_CONTRACT_SUPERVISOR)

    private fun hasRoleInMap(roles: Map<String, String>, roleId: String): Boolean {
        return roles.any { (key, value) ->
            key.equals(roleId, ignoreCase = true) || value.equals(roleId, ignoreCase = true)
        }
    }
}
