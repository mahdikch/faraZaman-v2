package Ir.co.tfs.farazaman.activity

import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import saman.zamani.persiandate.PersianDate
import Ir.co.tfs.farazaman.R
import android.widget.Toast
import ir.hamsaa.persiandatepicker.PersianDatePickerDialog
import ir.hamsaa.persiandatepicker.api.PersianPickerDate
import ir.hamsaa.persiandatepicker.api.PersianPickerListener
import ir.hamsaa.persiandatepicker.util.PersianCalendarUtils
import Ir.co.tfs.farazaman.layout.URLValidatorTask.TAG
import Ir.co.tfs.farazaman.util.LoadingDialog
import okhttp3.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.Date
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.data.api.FormDataApiService
import Ir.co.tfs.farazaman.data.model.FormDataApiRequest
import Ir.co.tfs.farazaman.data.model.FormDataRequest
import Ir.co.tfs.farazaman.data.model.MissionIndexResponse
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.google.gson.Gson

@AndroidEntryPoint
class NewMissionBottomSheetFragment : BottomSheetDialogFragment() {
    
    @Inject
    lateinit var formDataApiService: FormDataApiService
    
    private val gson = Gson()
    
    // Helper function to create form data requests
    private fun createFormDataRequest(
        organIds: List<Int> = emptyList(),
        contractIds: List<Int> = emptyList(),
        tenantId: Int = 1
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
                organId = organIds.firstOrNull() ?: 0
            )
        )
    }
    
    // Class-level variables for storing selected values
    private var selectedOrganId: Int? = null
    private var selectedContractId: Int? = null
    private var selectedOrganTitle: String? = null
    private var selectedContractTitle: String? = null
    private var organOptions: List<MissionSelectionOption> = emptyList()
    private var contractOptions: List<MissionSelectionOption> = emptyList()
    private var selectedDate: Date? = null
    private var loadingDialog: LoadingDialog? = null

    private lateinit var organSelector: View
    private lateinit var contractSelector: View
    private lateinit var organSelectedText: TextView
    private lateinit var contractSelectedText: TextView
    private lateinit var organProgress: ProgressBar
    private lateinit var contractProgress: ProgressBar
    private lateinit var submitButton: MaterialButton
    private fun selectedBaseUrl(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val base = prefs.getString("BASE_URL", "https://app.tfs.co.ir") ?: "https://app.tfs.co.ir"
        return if (base.endsWith("/")) base else "$base/"
    }
    private fun buildUrl(path: String): String {
        val base = selectedBaseUrl()
        return if (path.startsWith("/")) base + path.substring(1) else base + path
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        organSelector = view.findViewById(R.id.organSelector)
        contractSelector = view.findViewById(R.id.contractSelector)
        organSelectedText = view.findViewById(R.id.organSelectedText)
        contractSelectedText = view.findViewById(R.id.contractSelectedText)
        organProgress = view.findViewById(R.id.organProgress)
        contractProgress = view.findViewById(R.id.contractProgress)
        submitButton = view.findViewById(R.id.submit_mission_button)

        submitButton.isEnabled = false
        setOrganLoading(true)
        setContractEnabled(false)

        organSelector.setOnClickListener { openOrganSelection() }
        contractSelector.setOnClickListener { openContractSelection() }

        submitButton.setOnClickListener {
            val organId = selectedOrganId
            val contractId = selectedContractId
            if (organId == null || contractId == null) return@setOnClickListener
            submitButton.isEnabled = false
            submitButton.text = getString(R.string.mission_submitting)
            sendCreateMissionRequest(organId, contractId, submitButton)
        }

        dialog?.setOnShowListener { fetchOrgans() }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun updateButtonState() {
        submitButton.isEnabled = selectedOrganId != null && selectedContractId != null
    }

    private fun setOrganLoading(loading: Boolean) {
        organProgress.visibility = if (loading) View.VISIBLE else View.GONE
        organSelectedText.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        organSelector.isClickable = !loading
    }

    private fun setContractLoading(loading: Boolean) {
        contractProgress.visibility = if (loading) View.VISIBLE else View.GONE
        contractSelectedText.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        contractSelector.isClickable = !loading && selectedOrganId != null
    }

    private fun setContractEnabled(enabled: Boolean) {
        contractSelector.isClickable = enabled
        contractSelector.isFocusable = enabled
        contractSelector.alpha = if (enabled) 1f else 0.6f
    }

    private fun resetContractSelection() {
        selectedContractId = null
        selectedContractTitle = null
        contractOptions = emptyList()
        contractSelectedText.text = null
        contractSelectedText.setHint(R.string.mission_select_contract_hint)
        setContractEnabled(false)
        updateButtonState()
    }

    private fun openOrganSelection() {
        if (organProgress.visibility == View.VISIBLE) {
            Toast.makeText(requireContext(), R.string.mission_organs_loading, Toast.LENGTH_SHORT).show()
            return
        }
        if (organOptions.isEmpty()) {
            Toast.makeText(requireContext(), R.string.mission_organs_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        MissionItemSelectionBottomSheet.newInstance(
            getString(R.string.mission_select_organ_title),
            organOptions,
        ).apply {
            onItemSelected = { option -> onOrganSelected(option) }
        }.show(childFragmentManager, "mission_organ_selection")
    }

    private fun openContractSelection() {
        if (selectedOrganId == null) {
            Toast.makeText(requireContext(), R.string.mission_contracts_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (contractProgress.visibility == View.VISIBLE) return
        if (contractOptions.isEmpty()) {
            Toast.makeText(requireContext(), R.string.mission_contracts_empty, Toast.LENGTH_SHORT).show()
            return
        }
        MissionItemSelectionBottomSheet.newInstance(
            getString(R.string.mission_select_contract_title),
            contractOptions,
        ).apply {
            onItemSelected = { option -> onContractSelected(option) }
        }.show(childFragmentManager, "mission_contract_selection")
    }

    private fun onOrganSelected(option: MissionSelectionOption) {
        selectedOrganId = option.id
        selectedOrganTitle = option.title
        organSelectedText.text = option.title
        resetContractSelection()
        fetchContracts(option.id)
    }

    private fun onContractSelected(option: MissionSelectionOption) {
        selectedContractId = option.id
        selectedContractTitle = option.title
        contractSelectedText.text = option.title
        updateButtonState()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_new_mission_bottom_sheet, container, false)
        val dateTextView = view.findViewById<TextView>(R.id.persian_date_text)
       //        submitButton.visibility = View.GONE
        val dateEditText = view.findViewById<TextView>(R.id.persian_date_text)
        selectedDate = PersianDate().toDate()

        dateEditText.text =
            PersianDate().shYear.toString() + "/" + PersianDate().shMonth.toString() + "/" + PersianDate().shDay.toString()
//        dateEditText.setOnClickListener {
//            PersianDatePickerDialog(context)
//                .setPositiveButtonString("باشه")
//                .setNegativeButton("انصراف")
//                .setTodayButton("امروز")
//                .setTodayButtonVisible(true)
//                .setMinYear(1300)
//                .setMaxYear(PersianDatePickerDialog.THIS_YEAR)
//                .setMaxMonth(PersianDatePickerDialog.THIS_MONTH)
//                .setMaxDay(PersianDatePickerDialog.THIS_DAY)
//                .setInitDate(1370, 3, 13)
//                .setActionTextColor(Color.GRAY)
//                .setTitleType(PersianDatePickerDialog.WEEKDAY_DAY_MONTH_YEAR)
//                .setShowInBottomSheet(true)
//                .setListener(object : PersianPickerListener {
//                    override fun onDateSelected(persianPickerDate: PersianPickerDate) {
//                        Log.d(TAG, "onDateSelected: " + persianPickerDate.timestamp)
//                        Log.d(TAG, "onDateSelected: " + persianPickerDate.gregorianDate)
//                        Log.d(TAG, "onDateSelected: " + persianPickerDate.persianLongDate)
//                        Log.d(TAG, "onDateSelected: " + persianPickerDate.persianMonthName)
//                        Log.d(TAG, "onDateSelected: " + PersianCalendarUtils.isPersianLeapYear(persianPickerDate.persianYear))
//                        dateEditText.setText(persianPickerDate.persianYear.toString() + "/" + persianPickerDate.persianMonth + "/" + persianPickerDate.persianDay)
//                        selectedDate = persianPickerDate.gregorianDate
//                    }
//
//                    override fun onDismissed() {
//                    }
//                }).show()
//        }

        // Helper to update button state


        val today = PersianDate()
        dateTextView.text = getString(
            R.string.mission_today_date,
            today.shYear,
            today.shMonth,
            today.shDay,
        )

        // Note: This fragment should be refactored to use Hilt dependency injection
        // and proper API services instead of manual HTTP calls

        // Set loading state


        return view
    }
    private fun fetchOrgans() {
        setOrganLoading(true)
        Log.d("NewMissionBottomSheet", "=== fetchOrgans - Using FormDataApiService with AuthInterceptor ===")

        lifecycleScope.launch {
            try {
                val request = createFormDataRequest()
                val response = formDataApiService.getFormData(request)
                Log.d("NewMissionBottomSheet", "RESPONSE fetchOrgans -> code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val options = response.body()!!.response.organs.map(MissionSelectionOption::fromDropdown)
                    withContext(Dispatchers.Main) {
                        organOptions = options
                        setOrganLoading(false)
                        organSelector.isClickable = true
                    }
                } else {
                    Log.e("NewMissionBottomSheet", "ناموفق بودن درخواست کارفرما: ${response.code()}")
                    withContext(Dispatchers.Main) {
                        setOrganLoading(false)
                        organOptions = emptyList()
                        Toast.makeText(
                            requireContext(),
                            "خطا در دریافت لیست کارفرما: ${response.code()}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("NewMissionBottomSheet", "خطا در دریافت لیست کارفرما: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    setOrganLoading(false)
                    organOptions = emptyList()
                    Ir.co.tfs.farazaman.util.ErrorHandler.handleNetworkError(
                        e,
                        requireContext(),
                        "خطا در دریافت لیست کارفرما",
                    )
                }
            }
        }
    }

    private fun fetchContracts(organId: Int) {
        setContractEnabled(true)
        setContractLoading(true)
        Log.d("NewMissionBottomSheet", "=== fetchContracts - Using FormDataApiService with AuthInterceptor ===")

        lifecycleScope.launch {
            try {
                val request = createFormDataRequest(organIds = listOf(organId))
                val response = formDataApiService.getFormData(request)
                Log.d("NewMissionBottomSheet", "RESPONSE fetchContracts -> code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val options = response.body()!!.response.contracts.map(MissionSelectionOption::fromDropdown)
                    withContext(Dispatchers.Main) {
                        contractOptions = options
                        setContractLoading(false)
                        setContractEnabled(true)
                        if (options.isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                R.string.mission_contracts_empty,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                } else {
                    Log.e("NewMissionBottomSheet", "ناموفق بودن درخواست قراردادها: ${response.code()}")
                    withContext(Dispatchers.Main) {
                        setContractLoading(false)
                        contractOptions = emptyList()
                        Toast.makeText(
                            requireContext(),
                            "خطا در دریافت لیست قراردادها: ${response.code()}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("NewMissionBottomSheet", "خطا در دریافت لیست قراردادها: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    setContractLoading(false)
                    contractOptions = emptyList()
                    Ir.co.tfs.farazaman.util.ErrorHandler.handleNetworkError(
                        e,
                        requireContext(),
                        "خطا در دریافت لیست قراردادها",
                    )
                }
            }
        }
    }

    private fun sendCreateMissionRequest(organId: Int, contractId: Int, submitButton: com.google.android.material.button.MaterialButton) {
        // Show loading dialog
        loadingDialog = LoadingDialog.showWithTimeout(requireContext(), "در حال ثبت برنامه کاری جدید...", 180000) // 3 minutes timeout
        
        Log.d("NewMissionBottomSheet", "=== createMission - Using FormDataApiService with AuthInterceptor ===")
        
        lifecycleScope.launch {
            try {
        // Get today date in yyyy-MM-dd
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                
                val requestBody = mapOf(
                    "request" to mapOf(
                        "organIds" to listOf(organId),
                        "contractIds" to listOf(contractId),
                        "visitdate" to today,
                        "TenantId" to 1,
                        "OrganId" to organId,
                        "contractId" to contractId
                    )
                )
                
                Log.d("NewMissionBottomSheet", "REQUEST createMission -> Using injected FormDataApiService")
                Log.d("NewMissionBottomSheet", "REQUEST createMission -> Body: $requestBody")
                
                val response = formDataApiService.createMission(requestBody)
                
                Log.d("NewMissionBottomSheet", "RESPONSE createMission -> code: ${response.code()}")
                
                loadingDialog?.dismiss()
                loadingDialog = null
                submitButton.isEnabled = true
                submitButton.text = getString(R.string.mission_receive_submit)
                
                if (response.isSuccessful) {
                    Toast.makeText(context, "برنامه کاری جدید با موفقیت ثبت شد!", Toast.LENGTH_LONG).show()
                    // After successful creation, call the Index API to get mission details
                    fetchMissionDetailsWithService(organId, contractId, today)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("NewMissionBottomSheet", "createMission failed: ${response.code()} - $errorBody")
                    Ir.co.tfs.farazaman.util.ErrorHandler.handleRetrofitHttpError(response, requireContext(), "خطا در ثبت ماموریت جدید")
                }
            } catch (e: Exception) {
                Log.e("NewMissionBottomSheet", "Exception in createMission", e)
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    submitButton.isEnabled = true
                    submitButton.text = getString(R.string.mission_receive_submit)
                    Ir.co.tfs.farazaman.util.ErrorHandler.handleNetworkError(e, requireContext(), "خطا در ثبت برنامه کاری جدید")
                }
            }
    }
    
    /**
     * Fetch mission details using the injected FormDataApiService
     */
    private fun fetchMissionDetailsWithService(organId: Int, contractId: Int, visitDate: String) {
        // Show loading dialog
        loadingDialog = LoadingDialog.showWithTimeout(requireContext(), "در حال دریافت جزئیات برنامه کاری...", 180000) // 3 minutes timeout
        
        lifecycleScope.launch {
            try {
                val requestBody = mapOf(
                    "request" to mapOf(
                        "organIds" to listOf(organId),
                        "contractIds" to listOf(contractId),
                        "visitdate" to visitDate,
                        "TenantId" to 1,
                        "OrganId" to organId,
                        "contractId" to contractId
                    ),
                    "Pagination" to mapOf(
                        "page" to mapOf("pageSize" to 100)
                    )
                )
                
                Log.d("NewMissionBottomSheet", "REQUEST fetchMissionDetailsWithService -> Using injected FormDataApiService")
                Log.d("NewMissionBottomSheet", "REQUEST fetchMissionDetailsWithService -> Body: $requestBody")
                
                val response = formDataApiService.getMissionIndex(requestBody)
                
                Log.d("NewMissionBottomSheet", "RESPONSE fetchMissionDetailsWithService -> code: ${response.code()}")
                
                if (response.isSuccessful && response.body() != null) {
                    val missionIndexResponse = response.body()!!
                    Log.d("NewMissionBottomSheet", "RESPONSE fetchMissionDetailsWithService -> body: $missionIndexResponse")
                    
                    // Process the response using the new method
                    processMissionDataFromResponse(missionIndexResponse, organId, contractId, visitDate)
                    
                    withContext(Dispatchers.Main) {
                        loadingDialog?.dismiss()
                        loadingDialog = null
                        dismiss()
                    }
                } else {
                    Log.e("NewMissionBottomSheet", "fetchMissionDetailsWithService failed: ${response.code()}")
                    withContext(Dispatchers.Main) {
                        loadingDialog?.dismiss()
                        loadingDialog = null
                        Ir.co.tfs.farazaman.util.ErrorHandler.handleRetrofitHttpError(response, requireContext(), "خطا در دریافت جزئیات برنامه کاری")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("NewMissionBottomSheet", "Exception in fetchMissionDetailsWithService", e)
                withContext(Dispatchers.Main) {
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    Ir.co.tfs.farazaman.util.ErrorHandler.handleNetworkError(e, requireContext(), "خطا در دریافت جزئیات برنامه کاری")
                }
            }
        }
    }
    
    /**
     * Process mission data from the Index API response
     */
    private fun processMissionDataFromResponse(missionIndexResponse: MissionIndexResponse, organId: Int, contractId: Int, visitDate: String) {
        try {
            // Convert the entire MissionIndexResponse object to a JSON string using Gson
            val jsonString = gson.toJson(missionIndexResponse)
            // Parse the JSON string into an org.json.JSONObject
            val fullJsonObject = JSONObject(jsonString)

            // Extract the 'data' array from the full JSON object
            val dataArray = fullJsonObject.optJSONArray("data") ?: JSONArray()

            // Extract encryptions.details map
            val encryptionsMap = missionIndexResponse.encryptions?.details ?: emptyMap()
            
            // Process the data and create mission
            val missionData = processMissionData(dataArray, encryptionsMap, organId, contractId, visitDate)
            
            // Create mission in TrackManager
            lifecycleScope.launch(Dispatchers.Main) {
                createMissionFromData(missionData)
            }
            
        } catch (e: Exception) {
            Log.e("NewMissionBottomSheet", "Error processing mission data from response", e)
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "خطا در پردازش داده‌های برنامه کاری", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchMissionDetails(organId: Int, contractId: Int, visitDate: String, client: OkHttpClient) {
        // Show loading dialog
        loadingDialog = LoadingDialog.showWithTimeout(requireContext(), "در حال دریافت جزئیات برنامه کاری...", 180000) // 3 minutes timeout
        
        val json = """{"request":{"organIds":[${organId}],"contractIds":[${contractId}],"visitdate":"${visitDate}","TenantId":1,"OrganId":${organId},"contractId":${contractId}},"Pagination":{"page":{"pageSize":100}}}"""
        val body = json.toRequestBody("application/json".toMediaType())
        val indexUrl = buildUrl("/api/BillOriginCleaningItemDailyItem/Index")
        val request = Request.Builder()
            .url(indexUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            // TODO: Add AuthInterceptor support or use injected API service
            .post(body)
            .build()
        // Request logging
        runCatching {
            val selectedBase = PreferenceManager.getDefaultSharedPreferences(context).getString("BASE_URL", "not_set")
            Log.d("NewMissionBottomSheet", "REQUEST fetchMissionDetails -> prefs BASE_URL: $selectedBase")
            Log.d("NewMissionBottomSheet", "REQUEST fetchMissionDetails -> URL: ${request.url}")
            Log.d("NewMissionBottomSheet", "REQUEST fetchMissionDetails -> Method: ${request.method}")
        }
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NewMissionBottomSheet", "FAIL fetchMissionDetails -> URL: ${call.request().url}, error: ${e.message}")
                activity?.runOnUiThread {
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    Ir.co.tfs.farazaman.util.ErrorHandler.handleNetworkError(e, requireContext(), "خطا در دریافت جزئیات برنامه کاری")
                    dismiss()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d("NewMissionBottomSheet", "RESPONSE fetchMissionDetails -> URL: ${call.request().url}, code: ${response.code}")
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val dataArray = jsonResponse.getJSONArray("data")
                        
                        // Extract encryptions.details map
                        val encryptionsMap = mutableMapOf<String, String>()
                        if (jsonResponse.has("encryptions")) {
                            val encryptions = jsonResponse.getJSONObject("encryptions")
                            if (encryptions.has("details")) {
                                val details = encryptions.getJSONObject("details")
                                val keys = details.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val value = details.getString(key)
                                    encryptionsMap[key] = value
                                }
                            }
                        }
                        
                        // Process the data and create mission
                        val missionData = processMissionData(dataArray, encryptionsMap, organId, contractId, visitDate)
                        
                        // Create mission in TrackManager
                        activity?.runOnUiThread {
                            loadingDialog?.dismiss()
                            loadingDialog = null
                            createMissionFromData(missionData)
                            dismiss()
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            loadingDialog?.dismiss()
                            loadingDialog = null
                            Toast.makeText(context, "خطا در پردازش داده‌های برنامه کاری", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        loadingDialog?.dismiss()
                        loadingDialog = null
                        Ir.co.tfs.farazaman.util.ErrorHandler.handleHttpError(response, requireContext(), "خطا در دریافت جزئیات برنامه کاری")
                        dismiss()
                    }
                }
            }
        })
    }

    private fun processMissionData(dataArray: org.json.JSONArray, encryptionsMap: Map<String, String>, organId: Int, contractId: Int, visitDate: String): Map<String, Any> {
        android.util.Log.d("NewMissionBottomSheet", "=== processMissionData called ===")
        android.util.Log.d("NewMissionBottomSheet", "organId: $organId, contractId: $contractId")
        android.util.Log.d("NewMissionBottomSheet", "selectedOrganTitle: $selectedOrganTitle")
        android.util.Log.d("NewMissionBottomSheet", "selectedContractTitle: $selectedContractTitle")
        
        val planningItems = mutableListOf<Map<String, Any>>()
        val systemItems = mutableListOf<Map<String, Any>>()
        
        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            
            // Extract billCleaningItemName from nested structure
            val billCleaningItemName = item.optJSONObject("billOriginCleaningItem")
                ?.optJSONObject("billCleaningItem")
                ?.optString("billCleaningItemName", "") ?: ""
            
            // Extract appDataProviderId from nested structure
            val appDataProviderID = item.optJSONObject("billOriginCleaningItem")
                ?.optInt("appDataProviderId", 0) ?: 0
            
            // Extract billOriginCleaningItemRealID
            val billOriginCleaningItemRealID = item.optString("billOriginCleaningItemRealID", "")
            
            // Get aencryption from encryptionsMap using billOriginCleaningItemRealID as key
            val aencryption = encryptionsMap[billOriginCleaningItemRealID] ?: ""
            
            // Extract billCleaningItemGroupID and billCleaningItemID from nested structure
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
                "rawData" to item.toString()
            )
            
            // Classification logic: appDataProviderID == 6 goes to planning, others to system
            if (appDataProviderID == 6) {
                planningItems.add(itemData)
            } else {
                systemItems.add(itemData)
            }
        }
        
        // Get organ and contract titles from the current selections
        val organTitle = getOrganTitleById(organId)
        val contractTitle = getContractTitleById(contractId)
        
        android.util.Log.d("NewMissionBottomSheet", "Final organTitle: $organTitle")
        android.util.Log.d("NewMissionBottomSheet", "Final contractTitle: $contractTitle")
        
        return mapOf(
            "organId" to organId,
            "organTitle" to organTitle,
            "contractId" to contractId,
            "contractTitle" to contractTitle,
            "visitDate" to visitDate,
            "planningItems" to planningItems,
            "systemItems" to systemItems,
            "aencryptionsMap" to encryptionsMap,
            "totalItems" to dataArray.length()
        )
    }
    
    private fun getOrganTitleById(organId: Int): String {
        // Get the organ title from the current selection
        android.util.Log.d("NewMissionBottomSheet", "getOrganTitleById called with organId: $organId")
        android.util.Log.d("NewMissionBottomSheet", "selectedOrganTitle: $selectedOrganTitle")
        val result = selectedOrganTitle ?: "ارگان $organId"
        android.util.Log.d("NewMissionBottomSheet", "getOrganTitleById returning: $result")
        return result
    }
    
    private fun getContractTitleById(contractId: Int): String {
        // Get the contract title from the current selection
        android.util.Log.d("NewMissionBottomSheet", "getContractTitleById called with contractId: $contractId")
        android.util.Log.d("NewMissionBottomSheet", "selectedContractTitle: $selectedContractTitle")
        val result = selectedContractTitle ?: "قرارداد $contractId"
        android.util.Log.d("NewMissionBottomSheet", "getContractTitleById returning: $result")
        return result
    }

    private fun createMissionFromData(missionData: Map<String, Any>) {
        // Create a new track/mission in the database
        val values = android.content.ContentValues()
        val missionName = "ماموریت ${missionData["organId"]} - ${missionData["contractId"]} - ${missionData["visitDate"]}"
        values.put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_NAME, missionName)
        values.put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_START_DATE, java.util.Date().time)
        values.put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_ACTIVE, Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.VAL_TRACK_INACTIVE)
        values.put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_ROLE, "supervisor")
        values.putNull(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_EXPORT_DATE)
        
        // Store mission data as description (you might want to create a separate table for this)
        val description = buildString {
            append("Organ ID: ${missionData["organId"]}\n")
            append("Contract ID: ${missionData["contractId"]}\n")
            append("Visit Date: ${missionData["visitDate"]}\n")
            append("Planning Items: ${(missionData["planningItems"] as List<*>).size}\n")
            append("System Items: ${(missionData["systemItems"] as List<*>).size}\n")
            append("Total Items: ${missionData["totalItems"]}")
        }
        values.put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_DESCRIPTION, description)
        
        val trackUri = activity?.contentResolver?.insert(Ir.co.tfs.farazaman.data.db.TrackContentProvider.CONTENT_URI_TRACK, values)
        val trackId = android.content.ContentUris.parseId(trackUri!!)
        
        // Store detailed mission data in SharedPreferences for now (you might want to create a proper database table)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val missionDataKey = "mission_data_$trackId"
        val missionDataJson = org.json.JSONObject(missionData).toString()
        prefs.edit().putString(missionDataKey, missionDataJson).apply()
        
        android.util.Log.d("NewMissionBottomSheet", "=== Stored mission data in SharedPreferences ===")
        android.util.Log.d("NewMissionBottomSheet", "trackId: $trackId")
        android.util.Log.d("NewMissionBottomSheet", "missionDataJson: $missionDataJson")
        
        Toast.makeText(context, "برنامه کاری جدید ایجاد شد!", Toast.LENGTH_LONG).show()
        
        // Refresh the TrackManager to show the new mission
        activity?.let { act ->
            when (act) {
                is Ir.co.tfs.farazaman.activity.TrackManager -> act.refreshTrackList()
                is Ir.co.tfs.farazaman.activity.NewSupervisorActivity -> act.onMissionUpdated()
            }
            dismiss()
        }
    }
} 
