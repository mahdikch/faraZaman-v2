package Ir.co.tfs.farazaman.supervisor

import android.content.Context
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SupervisorViolationHistoryStore {

    private const val PREFS_KEY = "supervisor_violation_history"

    data class Record(
        val id: String,
        val title: String,
        val subtitle: String,
        val trackId: Long,
        val violationId: String,
        val timestampMs: Long,
        val status: ViolationHistoryStatus,
    )

    enum class ViolationHistoryStatus(val key: String) {
        REGISTERED("registered"),
        IN_PROGRESS("in_progress"),
        CONFIRMED("confirmed"),
        PENDING("pending"),
        REJECTED("rejected"),
        ;

        companion object {
            fun fromKey(key: String): ViolationHistoryStatus =
                entries.firstOrNull { it.key == key } ?: REGISTERED
        }
    }

    fun add(
        context: Context,
        title: String,
        trackId: Long,
        violationId: String,
        subtitle: String = "",
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        val records = loadAll(context).toMutableList()
        records.add(
            Record(
                id = UUID.randomUUID().toString(),
                title = title.trim().ifBlank { "تخلف ثبت شده" },
                subtitle = subtitle.trim(),
                trackId = trackId,
                violationId = violationId,
                timestampMs = timestampMs,
                status = ViolationHistoryStatus.REGISTERED,
            ),
        )
        saveAll(context, records)
    }

    fun loadAll(context: Context): List<Record> {
        val json = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREFS_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        Record(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", ""),
                            subtitle = obj.optString("subtitle", ""),
                            trackId = obj.optLong("trackId", -1L),
                            violationId = obj.optString("violationId", ""),
                            timestampMs = obj.optLong("timestampMs", 0L),
                            status = ViolationHistoryStatus.fromKey(obj.optString("status", "registered")),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun countByItemTitle(context: Context, trackId: Long): Map<String, Int> {
        return loadAll(context)
            .filter { it.trackId == trackId }
            .groupingBy { it.title }
            .eachCount()
    }

    fun loadForPersianDay(context: Context, year: Int, month: Int, day: Int): List<Record> {
        return loadAll(context).filter { record ->
            val pd = saman.zamani.persiandate.PersianDate(record.timestampMs)
            pd.shYear == year && pd.shMonth == month && pd.shDay == day
        }.sortedByDescending { it.timestampMs }
    }

    fun syncStatusesFromMissionData(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val records = loadAll(context).toMutableList()
        if (records.isEmpty()) return

        var changed = false
        val updated = records.map { record ->
            val missionJson = prefs.getString("mission_data_${record.trackId}", null) ?: return@map record
            val status = resolveStatusFromMission(missionJson, record.title) ?: return@map record
            if (record.status != status) {
                changed = true
                record.copy(status = status)
            } else {
                record
            }
        }

        if (changed) {
            saveAll(context, updated)
        }
    }

    private fun resolveStatusFromMission(missionJson: String, itemTitle: String): ViolationHistoryStatus? {
        return try {
            val mission = JSONObject(missionJson)
            listOf("planningItems", "systemItems").forEach { key ->
                val arr = mission.optJSONArray(key) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    if (item.optString("billCleaningItemName", "") == itemTitle) {
                        return mapVisitStatus(item.optString("visitStatus", item.optString("status", "")))
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun mapVisitStatus(raw: String): ViolationHistoryStatus {
        val s = raw.lowercase()
        return when {
            s.contains("reject") || s.contains("رد") -> ViolationHistoryStatus.REJECTED
            s.contains("pending") || s.contains("معلق") -> ViolationHistoryStatus.PENDING
            s.contains("reviewed") ||
                (s.contains("تایید") || (s.contains("شده") && !s.contains("نشده"))) ->
                ViolationHistoryStatus.CONFIRMED
            s.contains("progress") || s.contains("حال") || s.contains("بررسی") ->
                ViolationHistoryStatus.IN_PROGRESS
            else -> ViolationHistoryStatus.REGISTERED
        }
    }

    fun removeForTrack(context: Context, trackId: Long) {
        saveAll(context, loadAll(context).filter { it.trackId != trackId })
    }

    private fun saveAll(context: Context, records: List<Record>) {
        val arr = JSONArray()
        records.forEach { record ->
            arr.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("title", record.title)
                    put("subtitle", record.subtitle)
                    put("trackId", record.trackId)
                    put("violationId", record.violationId)
                    put("timestampMs", record.timestampMs)
                    put("status", record.status.key)
                },
            )
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREFS_KEY, arr.toString())
            .apply()
    }
}
