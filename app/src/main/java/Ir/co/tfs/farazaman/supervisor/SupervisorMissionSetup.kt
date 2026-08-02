package Ir.co.tfs.farazaman.supervisor

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import Ir.co.tfs.farazaman.activity.MissionSelectionOption
import Ir.co.tfs.farazaman.data.api.FormDataApiService
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.data.model.FormDataApiRequest
import Ir.co.tfs.farazaman.data.model.FormDataRequest
import Ir.co.tfs.farazaman.data.model.MissionIndexResponse
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SupervisorMissionSetup {

    private const val TAG = "SupervisorMissionSetup"

    suspend fun loadOrgans(service: FormDataApiService): List<MissionSelectionOption> {
        val response = service.getFormData(createFormDataRequest())
        if (!response.isSuccessful || response.body() == null) {
            throw MissionSetupException("خطا در دریافت لیست کارفرما: ${response.code()}")
        }
        return response.body()!!.response.organs.map(MissionSelectionOption::fromDropdown)
    }

    suspend fun loadContracts(service: FormDataApiService, organId: Int): List<MissionSelectionOption> {
        val response = service.getFormData(createFormDataRequest(organIds = listOf(organId)))
        if (!response.isSuccessful || response.body() == null) {
            throw MissionSetupException("خطا در دریافت لیست قراردادها: ${response.code()}")
        }
        return response.body()!!.response.contracts.map(MissionSelectionOption::fromDropdown)
    }

    suspend fun createSupervisorMission(
        context: Context,
        service: FormDataApiService,
        organId: Int,
        contractId: Int,
        organTitle: String,
        contractTitle: String,
    ): Long {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val requestBody = mapOf(
            "request" to mapOf(
                "organIds" to listOf(organId),
                "contractIds" to listOf(contractId),
                "visitdate" to today,
                "TenantId" to 1,
                "OrganId" to organId,
                "contractId" to contractId,
            ),
        )

        val createResponse = service.createMission(requestBody)
        if (!createResponse.isSuccessful) {
            throw MissionSetupException("خطا در ثبت برنامه کاری: ${createResponse.code()}")
        }

        val indexBody = mapOf(
            "request" to mapOf(
                "organIds" to listOf(organId),
                "contractIds" to listOf(contractId),
                "visitdate" to today,
                "TenantId" to 1,
                "OrganId" to organId,
                "contractId" to contractId,
            ),
            "Pagination" to mapOf("page" to mapOf("pageSize" to 100)),
        )
        val indexResponse = service.getMissionIndex(indexBody)
        if (!indexResponse.isSuccessful || indexResponse.body() == null) {
            throw MissionSetupException("خطا در دریافت جزئیات برنامه کاری: ${indexResponse.code()}")
        }

        val missionData = processMissionDataFromResponse(
            missionIndexResponse = indexResponse.body()!!,
            organId = organId,
            contractId = contractId,
            visitDate = today,
            organTitle = organTitle,
            contractTitle = contractTitle,
        )
        return persistMission(context, missionData)
    }

    private fun createFormDataRequest(
        organIds: List<Int> = emptyList(),
        contractIds: List<Int> = emptyList(),
        tenantId: Int = 1,
    ): FormDataApiRequest {
        return FormDataApiRequest(
            request = FormDataRequest(
                contractIds = contractIds,
                organIds = organIds,
                billCleaningViolationGroupIds = emptyList(),
                billCleaningViolationIds = emptyList(),
                billCleaningItemGroupIds = emptyList(),
                billCleaningItemIds = emptyList(),
                visitDate = "",
                maxVisitDate = "",
                minVisitDate = "",
                isDeleted = false,
                tenantId = tenantId,
                billOriginCleaningItemIds = emptyList(),
                billCleaningViolationId = 0,
                contractId = contractIds.firstOrNull() ?: 0,
                organId = organIds.firstOrNull() ?: 0,
            ),
        )
    }

    private fun processMissionDataFromResponse(
        missionIndexResponse: MissionIndexResponse,
        organId: Int,
        contractId: Int,
        visitDate: String,
        organTitle: String,
        contractTitle: String,
    ): Map<String, Any> {
        val gson = Gson()
        val jsonString = gson.toJson(missionIndexResponse)
        val fullJsonObject = JSONObject(jsonString)
        val dataArray = fullJsonObject.optJSONArray("data") ?: JSONArray()
        val encryptionsMap = missionIndexResponse.encryptions?.details ?: emptyMap()
        return processMissionData(
            dataArray = dataArray,
            encryptionsMap = encryptionsMap,
            organId = organId,
            contractId = contractId,
            visitDate = visitDate,
            organTitle = organTitle,
            contractTitle = contractTitle,
        )
    }

    private fun processMissionData(
        dataArray: JSONArray,
        encryptionsMap: Map<String, String>,
        organId: Int,
        contractId: Int,
        visitDate: String,
        organTitle: String,
        contractTitle: String,
    ): Map<String, Any> {
        val planningItems = mutableListOf<Map<String, Any>>()
        val systemItems = mutableListOf<Map<String, Any>>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val billCleaningItemName = item.optJSONObject("billOriginCleaningItem")
                ?.optJSONObject("billCleaningItem")
                ?.optString("billCleaningItemName", "") ?: ""
            val appDataProviderID = item.optJSONObject("billOriginCleaningItem")
                ?.optInt("appDataProviderId", 0) ?: 0
            val billOriginCleaningItemRealID = item.optString("billOriginCleaningItemRealID", "")
            val aencryption = encryptionsMap[billOriginCleaningItemRealID] ?: ""
            val billCleaningItemGroupID = item.optJSONObject("billOriginCleaningItem")
                ?.optJSONObject("billCleaningItem")
                ?.optJSONObject("billCleaningItemGroup")
                ?.optInt("billCleaningItemGroupID", 0) ?: 0
            val billCleaningItemGroupName = item.optJSONObject("billOriginCleaningItem")
                ?.optJSONObject("billCleaningItem")
                ?.optJSONObject("billCleaningItemGroup")
                ?.optString("billCleaningItemGroupName", "") ?: ""
            val billCleaningItemID = item.optJSONObject("billOriginCleaningItem")
                ?.optJSONObject("billCleaningItem")
                ?.optInt("billCleaningItemID", 0) ?: 0

            val itemData = mapOf(
                "billCleaningItemName" to billCleaningItemName,
                "appDataProviderID" to appDataProviderID,
                "billOriginCleaningItemRealID" to billOriginCleaningItemRealID,
                "billCleaningItemGroupID" to billCleaningItemGroupID,
                "billCleaningItemGroupName" to billCleaningItemGroupName,
                "billCleaningItemID" to billCleaningItemID,
                "aencryption" to aencryption,
                "rawData" to item.toString(),
            )

            if (appDataProviderID == 6) {
                planningItems.add(itemData)
            } else {
                systemItems.add(itemData)
            }
        }

        return mapOf(
            "organId" to organId,
            "organTitle" to organTitle,
            "contractId" to contractId,
            "contractTitle" to contractTitle,
            "visitDate" to visitDate,
            "planningItems" to planningItems,
            "systemItems" to systemItems,
            "aencryptionsMap" to encryptionsMap,
            "totalItems" to dataArray.length(),
        )
    }

    private fun persistMission(context: Context, missionData: Map<String, Any>): Long {
        val values = ContentValues()
        val missionName = "ماموریت ${missionData["organId"]} - ${missionData["contractId"]} - ${missionData["visitDate"]}"
        values.put(TrackContentProvider.Schema.COL_NAME, missionName)
        values.put(TrackContentProvider.Schema.COL_START_DATE, Date().time)
        values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_INACTIVE)
        values.put(TrackContentProvider.Schema.COL_ROLE, "supervisor")
        values.putNull(TrackContentProvider.Schema.COL_EXPORT_DATE)

        val description = buildString {
            append("Organ ID: ${missionData["organId"]}\n")
            append("Contract ID: ${missionData["contractId"]}\n")
            append("Visit Date: ${missionData["visitDate"]}\n")
            append("Planning Items: ${(missionData["planningItems"] as List<*>).size}\n")
            append("System Items: ${(missionData["systemItems"] as List<*>).size}\n")
            append("Total Items: ${missionData["totalItems"]}")
        }
        values.put(TrackContentProvider.Schema.COL_DESCRIPTION, description)

        val trackUri = context.contentResolver.insert(TrackContentProvider.CONTENT_URI_TRACK, values)
            ?: throw MissionSetupException("خطا در ایجاد برنامه کاری")
        val trackId = ContentUris.parseId(trackUri)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString("mission_data_$trackId", JSONObject(missionData).toString()).apply()
        Log.d(TAG, "Mission created with trackId=$trackId")
        return trackId
    }

    class MissionSetupException(message: String) : Exception(message)
}
