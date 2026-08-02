package Ir.co.tfs.farazaman.supervisor

import Ir.co.tfs.farazaman.data.model.DropdownItem
import org.json.JSONArray
import org.json.JSONObject

object DailyPlanItemViolationHelper {

    data class ItemViolation(
        val id: Int,
        val groupId: Int,
        val title: String,
    )

    fun parseActiveViolations(item: JSONObject): List<ItemViolation> {
        val violationsArray = extractViolationsArray(item) ?: return emptyList()
        val result = mutableListOf<ItemViolation>()
        for (i in 0 until violationsArray.length()) {
            val violation = violationsArray.getJSONObject(i)
            if (violation.optBoolean("isDeleted", false)) continue
            val id = violation.optInt("billCleaningViolationID", 0)
            if (id == 0) continue
            val title = violation.optString("violationType", "").trim()
            if (title.isEmpty()) continue
            val groupId = violation.optInt("billCleaningViolationGroupId", 0).takeIf { it > 0 }
                ?: violation.optJSONObject("billCleaningViolationGroup")
                    ?.optInt("billCleaningViolationGroupID", 0)
                ?: 0
            result.add(ItemViolation(id = id, groupId = groupId, title = title))
        }
        return result
    }

    fun canRegisterViolation(item: JSONObject): Boolean =
        parseActiveViolations(item).isNotEmpty()

    fun toDropdownItems(violations: List<ItemViolation>): List<DropdownItem> =
        violations.map { DropdownItem(text = it.title, value = it.id, selected = false) }

    private fun extractViolationsArray(item: JSONObject): JSONArray? {
        val rawData = item.optString("rawData", "").trim()
        if (rawData.isNotEmpty()) {
            try {
                val raw = JSONObject(rawData)
                val fromRaw = raw.optJSONObject("billOriginCleaningItem")
                    ?.optJSONObject("billCleaningItem")
                    ?.optJSONArray("billCleaningViolation")
                if (fromRaw != null && fromRaw.length() > 0) return fromRaw
            } catch (_: Exception) {
            }
        }

        item.optJSONArray("billCleaningViolations")?.takeIf { it.length() > 0 }?.let { return it }

        return null
    }
}
