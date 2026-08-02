package Ir.co.tfs.farazaman.supervisor

import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.widget.Toast
import Ir.co.tfs.farazaman.activity.DisplayTrackMap
import Ir.co.tfs.farazaman.activity.NewSubmitViolationFormActivity
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import org.json.JSONObject

object TodayVisitMissionActions {

    fun openMap(context: Context, trackId: Long, encryption: String?) {
        val intent = Intent(context, DisplayTrackMap::class.java).apply {
            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
            putExtra("playback_mode", false)
            putExtra("disable_gps_service", true)
            putExtra(DisplayTrackMap.EXTRA_FROM_NEW_SUPERVISOR, true)
            if (!encryption.isNullOrEmpty()) {
                putExtra("zone_encryption", encryption)
            }
        }
        context.startActivity(intent)
    }

    fun openViolation(context: Context, trackId: Long, sectionType: String, itemTitle: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val missionDataJson = prefs.getString("mission_data_$trackId", null) ?: run {
            Toast.makeText(context, "اطلاعات ماموریت یافت نشد", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val missionData = JSONObject(missionDataJson)
            val itemsKey = if (sectionType == "planning") "planningItems" else "systemItems"
            val itemsArray = missionData.getJSONArray(itemsKey)
            var itemData: JSONObject? = null
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                if (item.optString("billCleaningItemName", "") == itemTitle) {
                    itemData = item
                    break
                }
            }
            val intent = Intent(context, NewSubmitViolationFormActivity::class.java).apply {
                putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
                putExtra("organ_id", missionData.optInt("organId", 0))
                putExtra("contract_id", missionData.optInt("contractId", 0))
                putExtra("organ_title", missionData.optString("organTitle", ""))
                putExtra("contract_title", missionData.optString("contractTitle", ""))
                putExtra("visit_date", missionData.optString("visitDate", ""))
                putExtra("from_icon_violation", true)
                itemData?.let { putExtra("item_data", it.toString()) }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در باز کردن فرم", Toast.LENGTH_SHORT).show()
        }
    }
}
