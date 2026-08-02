package Ir.co.tfs.farazaman.activity

import android.content.Context
import android.database.Cursor
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.db.TracklistAdapter
import org.json.JSONObject
import saman.zamani.persiandate.PersianDate

// Top-level public interface for visibility
interface TrackListRecyclerViewAdapterListener {
    fun onClick(trackId: Long)
    fun deleteTrackItem(trackId: Long)
    fun stopTrack(trackId: Long, stopOrResume: Boolean)
    fun endMission(trackId: Long)
    fun formActivity(trackId: Long)
    fun formActivityWithMissionData(trackId: Long, organId: Int, contractId: Int, organTitle: String, contractTitle: String, visitDate: String, itemData: String?)
    fun onShowPopupMenu(popupMenu: PopupMenu, trackId: Long)
}

class TrackListRVAdapter(
    private val context: Context,
    cursor: Cursor?,
    private val mHandler: TrackListRecyclerViewAdapterListener
) : RecyclerView.Adapter<TrackListRVAdapter.TrackItemVH>() {

    val cursorAdapter: TracklistAdapter = TracklistAdapter(context, cursor)

    fun deleteTrack(position: Int, onCancel: (() -> Unit)?) {
        val cursor = cursorAdapter.cursor
        if (cursor.moveToPosition(position)) {
            val trackId = cursor.getLong(cursor.getColumnIndex("_id"))
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.trackmgr_contextmenu_delete)
                .setMessage(R.string.trackmgr_delete_confirm)
                .setPositiveButton("بله") { dialog, which -> mHandler.deleteTrackItem(trackId) }
                .setNegativeButton("خیر") { dialog, which ->
                    onCancel?.invoke()
                    notifyItemChanged(position)
                }
                .show()
        }
    }

    inner class TrackItemVH(view: View) : RecyclerView.ViewHolder(view) {
        private val vId: TextView = view.findViewById(R.id.trackmgr_item_id)
        private val date: TextView = view.findViewById(R.id.date)
        private val vOptions: ImageView = view.findViewById(R.id.trackmgr_item_options)
        private val stopOrResume: Button = view.findViewById(R.id.stop_or_resume)
        private val end: Button = view.findViewById(R.id.end_mission)
        private val firstCard: MaterialCardView = view.findViewById(R.id.first_card)
        private val secoundCard: MaterialCardView = view.findViewById(R.id.secound_card)

        private val headerPlanning: RelativeLayout = view.findViewById(R.id.header_planning)
        private val expandablePlanning: RecyclerView = view.findViewById(R.id.expandable_planning)
        private val arrowPlanning: ImageView = view.findViewById(R.id.arrow_planning)

        private val headerSystem: RelativeLayout = view.findViewById(R.id.header_system)
        private val expandableSystem: RecyclerView = view.findViewById(R.id.expandable_system)
        private val arrowSystem: ImageView = view.findViewById(R.id.arrow_system)
        private val submitViolationCard: View = view.findViewById(R.id.submit_violation_card)
        
        // Mission title fields
        private val organsTextView: TextView = view.findViewById(R.id.organs)
        private val contractsTextView: TextView = view.findViewById(R.id.contracts)

        init {
            date.text =
                PersianDate().shYear.toString() + "/" + PersianDate().shMonth.toString() + "/" + PersianDate().shDay.toString()

            // Debug: Check if views are found
            android.util.Log.d("TrackListRVAdapter", "headerPlanning: ${headerPlanning != null}")
            android.util.Log.d("TrackListRVAdapter", "expandablePlanning: ${expandablePlanning != null}")
            android.util.Log.d("TrackListRVAdapter", "arrowPlanning: ${arrowPlanning != null}")
            android.util.Log.d("TrackListRVAdapter", "headerSystem: ${headerSystem != null}")
            android.util.Log.d("TrackListRVAdapter", "expandableSystem: ${expandableSystem != null}")
            android.util.Log.d("TrackListRVAdapter", "arrowSystem: ${arrowSystem != null}")



            // تنظیم کلیک لیسنر برای هدر کارت برنامه‌ریزی
            headerPlanning.setOnClickListener {
                android.util.Log.d("TrackListRVAdapter", "Planning header clicked")
                toggleVisibility(expandablePlanning, arrowPlanning)
            }

            // تنظیم کلیک لیسنر برای هدر کارت سامانه
            headerSystem.setOnClickListener {
                android.util.Log.d("TrackListRVAdapter", "System header clicked")
                toggleVisibility(expandableSystem, arrowSystem)
            }

            end.setOnClickListener {
                try {
                    val trackId = vId.text.toString().toLong()
                    mHandler.endMission(trackId)
                } catch (e: NumberFormatException) {
                    // Handle error
                }
            }
            stopOrResume.setOnClickListener {
                try {
                    val trackId = vId.text.toString().toLong()
                    mHandler.stopTrack(trackId, stopOrResume.text.toString() == "توقف")
                } catch (e: NumberFormatException) {
                    // Handle error
                }
            }
            vOptions.setOnClickListener {
                try {
                    val trackId = vId.text.toString().toLong()
                    val popupMenu = PopupMenu(context, vOptions)
                    mHandler.onShowPopupMenu(popupMenu, trackId)
                    popupMenu.show()
                } catch (e: NumberFormatException) {
                    // Handle error
                }
            }
            
            submitViolationCard.setOnClickListener {
                android.util.Log.d("TrackListRVAdapter", "=== submit_violation_card CLICKED ===")
                try {
                    val trackIdText = vId.text.toString()
                    android.util.Log.d("TrackListRVAdapter", "vId.text = '$trackIdText'")
                    
                    if (trackIdText.isEmpty()) {
                        android.util.Log.e("TrackListRVAdapter", "Track ID text is empty!")
                        return@setOnClickListener
                    }
                    
                    val trackId = trackIdText.toLong()
                    android.util.Log.d("TrackListRVAdapter", "Parsed trackId = $trackId")
                    
                    // Get the last location of the track
                    val lastLocation = getLastLocationOfTrack(trackId)
                    android.util.Log.d("TrackListRVAdapter", "Last location: $lastLocation")
                    
                    // Get mission data from SharedPreferences
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    val missionDataKey = "mission_data_$trackId"
                    val missionDataJson = prefs.getString(missionDataKey, null)
                    android.util.Log.d("TrackListRVAdapter", "Mission data JSON for track $trackId: $missionDataJson")
                    
                    val intent = android.content.Intent(context, Ir.co.tfs.farazaman.activity.SubmitViolationFormActivity::class.java).apply {
                        putExtra(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_TRACK_ID, trackId)
                        android.util.Log.d("TrackListRVAdapter", "Added track_id to intent: $trackId")
                        
                        if (lastLocation != null) {
                            putExtra(Ir.co.tfs.farazaman.activity.DisplayTrackMap.EXTRA_LATITUDE, lastLocation.latitude)
                            putExtra(Ir.co.tfs.farazaman.activity.DisplayTrackMap.EXTRA_LONGITUDE, lastLocation.longitude)
                            android.util.Log.d("TrackListRVAdapter", "Added location to intent: ${lastLocation.latitude}, ${lastLocation.longitude}")
                        }
                        
                        // Add mission data if available (organId, contractId, etc.)
                        if (missionDataJson != null) {
                            try {
                                val missionData = org.json.JSONObject(missionDataJson)
                                val organId = missionData.optInt("organId", 0)
                                val contractId = missionData.optInt("contractId", 0)
                                val organTitle = missionData.optString("organTitle", "")
                                val contractTitle = missionData.optString("contractTitle", "")
                                val visitDate = missionData.optString("visitDate", "")
                                
                                android.util.Log.d("TrackListRVAdapter", "Extracted from mission data: organId=$organId, contractId=$contractId, organTitle='$organTitle', contractTitle='$contractTitle'")
                                
                                putExtra("organ_id", organId)
                                putExtra("contract_id", contractId)
                                putExtra("organ_title", organTitle)
                                putExtra("contract_title", contractTitle)
                                putExtra("visit_date", visitDate)
                                
                                android.util.Log.d("TrackListRVAdapter", "✓ submit_violation_card: Successfully added mission data to intent")
                            } catch (e: Exception) {
                                android.util.Log.e("TrackListRVAdapter", "✗ Error parsing mission data for submit_violation_card: ${e.message}", e)
                            }
                        } else {
                            android.util.Log.w("TrackListRVAdapter", "✗ No mission data found for track $trackId in submit_violation_card click")
                        }
                        
                        // Mark this as coming from submit_violation_card
                        putExtra("from_submit_violation_card", true)
                        android.util.Log.d("TrackListRVAdapter", "Added from_submit_violation_card flag")
                    }
                    
                    android.util.Log.d("TrackListRVAdapter", "Starting SubmitViolationFormActivity...")
                    context.startActivity(intent)
                } catch (e: NumberFormatException) {
                    android.util.Log.e("TrackListRVAdapter", "✗ NumberFormatException in submit_violation_card click: ${e.message}", e)
                } catch (e: Exception) {
                    android.util.Log.e("TrackListRVAdapter", "✗ Unexpected error in submit_violation_card click: ${e.message}", e)
                }
            }
//            view.setOnClickListener(this)
        }

        private fun toggleVisibility(section: RecyclerView, arrow: ImageView) {
            android.util.Log.d("TrackListRVAdapter", "toggleVisibility called for section: ${section.id}")
            val isVisible = section.visibility == View.VISIBLE
            android.util.Log.d("TrackListRVAdapter", "Current visibility: $isVisible")
            
            section.visibility = if (isVisible) View.GONE else View.VISIBLE
            arrow.animate().rotation(if (isVisible) 0f else 180f).setDuration(300).start()
            
            android.util.Log.d("TrackListRVAdapter", "New visibility: ${section.visibility}")
            
            // Save expanded state to SharedPreferences
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val trackId = vId.text.toString().toLong()
            val sectionKey = if (section.id == R.id.expandable_planning) "planning" else "system"
            val expandedKey = "section_expanded_${trackId}_$sectionKey"
            prefs.edit().putBoolean(expandedKey, !isVisible).apply()
        }

        fun loadMissionData(trackId: Long) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val missionDataKey = "mission_data_$trackId"
            val missionDataJson = prefs.getString(missionDataKey, null)
            
            if (missionDataJson != null) {
                try {
                    val missionData = JSONObject(missionDataJson)
                    val planningItems = missionData.getJSONArray("planningItems")
                    val systemItems = missionData.getJSONArray("systemItems")
                    
                    // Populate planning items using RecyclerView
                    populateItemsRecyclerView(planningItems, expandablePlanning, "planning")
                    
                    // Populate system items using RecyclerView
                    populateItemsRecyclerView(systemItems, expandableSystem, "system")
                    
                    // Restore expanded state
                    restoreExpandedState(trackId)
                    
                } catch (e: Exception) {
                    // If there's an error parsing the data, keep the default items
                    android.util.Log.e("TrackListRVAdapter", "Error loading mission data for track $trackId: ${e.message}")
                }
            }
        }
        
        fun setMissionTitles(trackId: Long) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val missionDataKey = "mission_data_$trackId"
            val missionDataJson = prefs.getString(missionDataKey, null)
            
            if (missionDataJson != null) {
                try {
                    val missionData = JSONObject(missionDataJson)
                    val organTitle = missionData.optString("organTitle", "")
                    val contractTitle = missionData.optString("contractTitle", "")
                    
                    // Set organization title
                    if (organTitle.isNotEmpty()) {
                        organsTextView.text = organTitle
                    } else {
                        organsTextView.text = "-"
                    }
                    
                    // Set contract title
                    if (contractTitle.isNotEmpty()) {
                        contractsTextView.text = contractTitle
                    } else {
                        contractsTextView.text = "-"
                    }
                    
                    android.util.Log.d("TrackListRVAdapter", "Set mission titles for track $trackId: organ='$organTitle', contract='$contractTitle'")
                    
                } catch (e: Exception) {
                    android.util.Log.e("TrackListRVAdapter", "Error setting mission titles for track $trackId: ${e.message}")
                    // Set default values on error
                    organsTextView.text = "-"
                    contractsTextView.text = "-"
                }
            } else {
                // No mission data found, set default values
                organsTextView.text = "-"
                contractsTextView.text = "-"
                android.util.Log.d("TrackListRVAdapter", "No mission data found for track $trackId, using default titles")
            }
        }
        
        private fun restoreExpandedState(trackId: Long) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            
            // Restore planning section state
            val planningExpandedKey = "section_expanded_${trackId}_planning"
            val isPlanningExpanded = prefs.getBoolean(planningExpandedKey, false)
            if (isPlanningExpanded) {
                expandablePlanning.visibility = View.VISIBLE
                arrowPlanning.rotation = 180f
            } else {
                expandablePlanning.visibility = View.GONE
                arrowPlanning.rotation = 0f
            }
            
            // Restore system section state
            val systemExpandedKey = "section_expanded_${trackId}_system"
            val isSystemExpanded = prefs.getBoolean(systemExpandedKey, false)
            if (isSystemExpanded) {
                expandableSystem.visibility = View.VISIBLE
                arrowSystem.rotation = 180f
            } else {
                expandableSystem.visibility = View.GONE
                arrowSystem.rotation = 0f
            }
        }
        
        private fun getLastLocationOfTrack(trackId: Long): android.location.Location? {
            // Query the last track point for this track
            val cursor = context.contentResolver.query(
                Ir.co.tfs.farazaman.data.db.TrackContentProvider.trackPointsUri(trackId),
                arrayOf(
                    Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_LATITUDE,
                    Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_LONGITUDE,
                    Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_TIMESTAMP
                ),
                null,
                null,
                "${Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_TIMESTAMP} DESC" // Get the most recent point
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val latitude = it.getDouble(it.getColumnIndex(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_LATITUDE))
                    val longitude = it.getDouble(it.getColumnIndex(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_LONGITUDE))
                    
                    // Create a Location object
                    val location = android.location.Location("track_last_location")
                    location.latitude = latitude
                    location.longitude = longitude
                    
                    return location
                }
            }
            
            return null
        }

        private fun populateItemsRecyclerView(itemsArray: org.json.JSONArray, recyclerView: RecyclerView, type: String) {
            val items = mutableListOf<String>()
            val encryptions = mutableListOf<String>()
            
            // Extract item titles and encryptions from the JSON array
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val title = item.optString("billCleaningItemName", "")
                val encryption = item.optString("aencryption", "")
                if (title.isNotEmpty()) {
                    items.add(title)
                    encryptions.add(encryption)
                }
            }
            
            // Set up RecyclerView with click handlers
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            recyclerView.adapter = MissionItemsAdapter(
                items = items,
                sectionType = type, // "planning" or "system"
                onMapClick = { position ->
                    try {
                        val trackId = vId.text.toString().toLong()
                        val encryption = encryptions.getOrNull(position)
                        if (encryption != null && encryption.isNotEmpty()) {
                            // Launch DisplayTrackMap with encryption for zone display
                            val intent = android.content.Intent(context, Ir.co.tfs.farazaman.activity.DisplayTrackMap::class.java)
                            intent.putExtra(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_TRACK_ID, trackId)
                            intent.putExtra("zone_encryption", encryption)
                            intent.putExtra("playback_mode", false)
                            intent.putExtra("disable_gps_service", true) // Prevent GPS tracking service from starting
                            context.startActivity(intent)
                        } else {
                            // Fallback to regular track display
                            mHandler.onClick(trackId)
                        }
                    } catch (e: NumberFormatException) {
                        // Handle error
                    }
                },
                onViolationClick = { position ->
                    android.util.Log.d("TrackListRVAdapter", "=== icon_violation clicked ===")
                    try {
                        val trackId = vId.text.toString().toLong()
                        val encryption = encryptions.getOrNull(position)
                        val itemTitle = items.getOrNull(position) ?: ""
                        
                        android.util.Log.d("TrackListRVAdapter", "trackId: $trackId, itemTitle: $itemTitle")
                        
                        // Get mission data from SharedPreferences
                        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                        val missionDataKey = "mission_data_$trackId"
                        val missionDataJson = prefs.getString(missionDataKey, null)
                        
                        android.util.Log.d("TrackListRVAdapter", "missionDataJson: $missionDataJson")
                        
                        if (missionDataJson != null) {
                            try {
                                val missionData = org.json.JSONObject(missionDataJson)
                                val organId = missionData.optInt("organId", 0)
                                val contractId = missionData.optInt("contractId", 0)
                                val organTitle = missionData.optString("organTitle", "")
                                val contractTitle = missionData.optString("contractTitle", "")
                                val visitDate = missionData.optString("visitDate", "")
                                
                                android.util.Log.d("TrackListRVAdapter", "Parsed mission data:")
                                android.util.Log.d("TrackListRVAdapter", "  organId: $organId, organTitle: $organTitle")
                                android.util.Log.d("TrackListRVAdapter", "  contractId: $contractId, contractTitle: $contractTitle")
                                android.util.Log.d("TrackListRVAdapter", "  visitDate: $visitDate")
                                
                                // Try to find the specific item data in the stored mission data
                                val itemsArray = if (type == "planning") {
                                    missionData.getJSONArray("planningItems")
                                } else {
                                    missionData.getJSONArray("systemItems")
                                }
                                
                                // Find the specific item data
                                var itemData: org.json.JSONObject? = null
                                for (i in 0 until itemsArray.length()) {
                                    val item = itemsArray.getJSONObject(i)
                                    val itemName = item.optString("billCleaningItemName", "")
                                    if (itemName == itemTitle) {
                                        itemData = item
                                        break
                                    }
                                }
                                
                                if (itemData != null) {
                                    android.util.Log.d("TrackListRVAdapter", "Found itemData: ${itemData.toString()}")
                                    
                                    // Extract violation data from the itemData
                                    var billCleaningViolationID = 0
                                    var billCleaningViolationGroupId = 0
                                    
                                    android.util.Log.d("TrackListRVAdapter", "=== EXTRACTING VIOLATION DATA ===")
                                    android.util.Log.d("TrackListRVAdapter", "itemData keys: ${itemData.keys().asSequence().toList()}")
                                    
                                    // Try to extract from billCleaningViolations array
                                    if (itemData.has("billCleaningViolations")) {
                                        val violationsArray = itemData.getJSONArray("billCleaningViolations")
                                        android.util.Log.d("TrackListRVAdapter", "Found billCleaningViolations array with ${violationsArray.length()} items")
                                        if (violationsArray.length() > 0) {
                                            val firstViolation = violationsArray.getJSONObject(0)
                                            android.util.Log.d("TrackListRVAdapter", "First violation object: ${firstViolation.toString()}")
                                            billCleaningViolationID = firstViolation.optInt("billCleaningViolationID", 0)
                                            android.util.Log.d("TrackListRVAdapter", "Extracted billCleaningViolationID: $billCleaningViolationID")
                                        }
                                    } else {
                                        android.util.Log.d("TrackListRVAdapter", "No billCleaningViolations array found in itemData")
                                    }
                                    
                                    // Try to extract from billCleaningViolationGroups array
                                    if (itemData.has("billCleaningViolationGroups")) {
                                        val violationGroupsArray = itemData.getJSONArray("billCleaningViolationGroups")
                                        android.util.Log.d("TrackListRVAdapter", "Found billCleaningViolationGroups array with ${violationGroupsArray.length()} items")
                                        if (violationGroupsArray.length() > 0) {
                                            val firstViolationGroup = violationGroupsArray.getJSONObject(0)
                                            android.util.Log.d("TrackListRVAdapter", "First violation group object: ${firstViolationGroup.toString()}")
                                            billCleaningViolationGroupId = firstViolationGroup.optInt("billCleaningViolationGroupId", 0)
                                            android.util.Log.d("TrackListRVAdapter", "Extracted billCleaningViolationGroupId: $billCleaningViolationGroupId")
                                        }
                                    } else {
                                        android.util.Log.d("TrackListRVAdapter", "No billCleaningViolationGroups array found in itemData")
                                    }
                                    
                                    // Fallback: Try to extract from direct fields if arrays are empty
                                    if (billCleaningViolationID == 0 && itemData.has("billCleaningViolationID")) {
                                        billCleaningViolationID = itemData.optInt("billCleaningViolationID", 0)
                                        android.util.Log.d("TrackListRVAdapter", "Fallback: Extracted billCleaningViolationID from direct field: $billCleaningViolationID")
                                    }
                                    
                                    if (billCleaningViolationGroupId == 0 && itemData.has("billCleaningViolationGroupId")) {
                                        billCleaningViolationGroupId = itemData.optInt("billCleaningViolationGroupId", 0)
                                        android.util.Log.d("TrackListRVAdapter", "Fallback: Extracted billCleaningViolationGroupId from direct field: $billCleaningViolationGroupId")
                                    }
                                    
                                    // Try to extract from rawData JSON string if still not found
                                    if ((billCleaningViolationID == 0 || billCleaningViolationGroupId == 0) && itemData.has("rawData")) {
                                        try {
                                            val rawDataString = itemData.optString("rawData", "{}")
                                            android.util.Log.d("TrackListRVAdapter", "Parsing rawData: $rawDataString")
                                            
                                            val rawData = org.json.JSONObject(rawDataString)
                                            android.util.Log.d("TrackListRVAdapter", "rawData keys: ${rawData.keys().asSequence().toList()}")
                                            
                                            // Navigate through the nested structure to find violation data
                                            if (rawData.has("billOriginCleaningItem")) {
                                                val billOriginCleaningItem = rawData.getJSONObject("billOriginCleaningItem")
                                                if (billOriginCleaningItem.has("billCleaningItem")) {
                                                    val billCleaningItem = billOriginCleaningItem.getJSONObject("billCleaningItem")
                                                    
                                                    // Extract violation group ID
                                                    if (billCleaningViolationGroupId == 0 && billCleaningItem.has("billCleaningViolation")) {
                                                        val violationsArray = billCleaningItem.getJSONArray("billCleaningViolation")
                                                        if (violationsArray.length() > 0) {
                                                            val firstViolation = violationsArray.getJSONObject(0)
                                                            if (firstViolation.has("billCleaningViolationGroup")) {
                                                                val violationGroup = firstViolation.getJSONObject("billCleaningViolationGroup")
                                                                billCleaningViolationGroupId = violationGroup.optInt("billCleaningViolationGroupID", 0)
                                                                android.util.Log.d("TrackListRVAdapter", "Extracted billCleaningViolationGroupId from rawData: $billCleaningViolationGroupId")
                                                            }
                                                        }
                                                    }
                                                    
                                                    // Extract violation ID
                                                    if (billCleaningViolationID == 0 && billCleaningItem.has("billCleaningViolation")) {
                                                        val violationsArray = billCleaningItem.getJSONArray("billCleaningViolation")
                                                        if (violationsArray.length() > 0) {
                                                            val firstViolation = violationsArray.getJSONObject(0)
                                                            billCleaningViolationID = firstViolation.optInt("billCleaningViolationID", 0)
                                                            android.util.Log.d("TrackListRVAdapter", "Extracted billCleaningViolationID from rawData: $billCleaningViolationID")
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("TrackListRVAdapter", "Error parsing rawData: ${e.message}", e)
                                        }
                                    }
                                    
                                    // Add violation IDs to itemData if they were found
                                    if (billCleaningViolationID > 0 || billCleaningViolationGroupId > 0) {
                                        val updatedItemData = org.json.JSONObject(itemData.toString())
                                        if (billCleaningViolationID > 0) {
                                            updatedItemData.put("billCleaningViolationID", billCleaningViolationID)
                                        }
                                        if (billCleaningViolationGroupId > 0) {
                                            updatedItemData.put("billCleaningViolationGroupId", billCleaningViolationGroupId)
                                        }
                                        itemData = updatedItemData
                                        android.util.Log.d("TrackListRVAdapter", "Updated itemData with violation IDs: ${itemData.toString()}")
                                    } else {
                                        android.util.Log.d("TrackListRVAdapter", "No violation IDs found to add to itemData")
                                    }
                                } else {
                                    // If not found, create basic itemData
                                    itemData = org.json.JSONObject(mapOf(
                                        "billCleaningItemName" to itemTitle,
                                        "appDataProviderID" to 5, // Default value
                                        "billOriginCleaningItemRealID" to "", // Will be filled by API
                                        "billCleaningItemGroupID" to 0, // Default value
                                        "billCleaningItemGroupName" to "", // Default value
                                        "billCleaningItemID" to 0, // Default value
                                        "aencryption" to "", // Will be filled by API
                                        "rawData" to "{}" // Will be filled by API
                                    ))
                                    android.util.Log.d("TrackListRVAdapter", "Created basic itemData for: $itemTitle")
                                }
                                
                                // Pass the data to formActivity
                                android.util.Log.d("TrackListRVAdapter", "Calling formActivityWithMissionData")
                                mHandler.formActivityWithMissionData(trackId, organId, contractId, organTitle, contractTitle, visitDate, itemData.toString())
                            } catch (e: Exception) {
                                android.util.Log.e("TrackListRVAdapter", "Error parsing mission data: ${e.message}")
                                // Fallback to regular formActivity
                                mHandler.formActivity(trackId)
                            }
                        } else {
                            android.util.Log.d("TrackListRVAdapter", "No mission data found, using fallback")
                            // Fallback to regular formActivity
                            mHandler.formActivity(trackId)
                        }
                    } catch (e: NumberFormatException) {
                        android.util.Log.e("TrackListRVAdapter", "NumberFormatException: ${e.message}")
                        // Handle error
                    }
                }
            )
            
            android.util.Log.d("TrackListRVAdapter", "Populated $type with ${items.size} items")
        }

//        override fun onClick(v: View?) {
//            try {
//                val trackId = vId.text.toString().toLong()
//                mHandler.onClick(trackId)
//            } catch (e: NumberFormatException) {
//                // Handle error
//            }
//        }
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackItemVH {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.tracklist_item, parent, false)
        return TrackItemVH(view)
    }

    override fun onBindViewHolder(holder: TrackItemVH, position: Int) {
        cursorAdapter.cursor.moveToPosition(position)
        cursorAdapter.bindView(holder.itemView, context, cursorAdapter.cursor)
        
        // Load and populate mission data if available
        val trackId = cursorAdapter.cursor.getLong(cursorAdapter.cursor.getColumnIndex("_id"))
        holder.loadMissionData(trackId)
        
        // Set organization and contract titles from mission data
        holder.setMissionTitles(trackId)
    }

    override fun getItemCount(): Int = cursorAdapter.count


} 
