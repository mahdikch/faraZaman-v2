package Ir.co.tfs.farazaman.activity

import android.Manifest
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.activity.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import Ir.co.tfs.farazaman.supervisor.DailyPlanItemViolationHelper
import Ir.co.tfs.farazaman.supervisor.SupervisorViolationHistoryStore
import Ir.co.tfs.farazaman.util.GpsAccuracyHelper
import dagger.hilt.android.AndroidEntryPoint
import ir.hamsaa.persiandatepicker.PersianDatePickerDialog
import ir.hamsaa.persiandatepicker.api.PersianPickerDate
import ir.hamsaa.persiandatepicker.api.PersianPickerListener
import ir.hamsaa.persiandatepicker.util.PersianCalendarUtils
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.data.db.DatabaseHelper
import Ir.co.tfs.farazaman.data.model.DropdownItem
import Ir.co.tfs.farazaman.layout.URLValidatorTask.TAG
import Ir.co.tfs.farazaman.presentation.viewmodel.FormDataViewModel
import Ir.co.tfs.farazaman.presentation.viewmodel.FormDataViewModel.SubmissionState
import Ir.co.tfs.farazaman.util.LoadingDialog
import Ir.co.tfs.farazaman.presentation.base.BaseActivity
import saman.zamani.persiandate.PersianDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
open class SubmitViolationFormActivity : BaseActivity() {

    companion object {
        private const val TAG = "SubmitViolationForm"
    }

    protected val formDataViewModel: FormDataViewModel by viewModels()
    private var currentTrackId: Long = -1
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var address: String = ""
    private lateinit var btnPickImage: Button
    private lateinit var imagesRecyclerView: androidx.recyclerview.widget.RecyclerView
    private var cameraImageUri: Uri? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var selectedDate: Date? = null
    protected val selectedImages = mutableListOf<Uri>()
    private lateinit var imagesAdapter: SelectedImagesAdapter
    // UI Components
    private lateinit var organsTextView: AutoCompleteTextView
    private lateinit var contractNumberTextView: AutoCompleteTextView
    private lateinit var violationGroupTextView: AutoCompleteTextView
    private lateinit var defectTextView: AutoCompleteTextView
    private lateinit var seasonOfPriceTextView: AutoCompleteTextView
    private lateinit var price_listTextView: AutoCompleteTextView
    private lateinit var addressEditText: TextInputEditText
    private lateinit var gpsWarningTextView: TextView
    private lateinit var countUnitLabel: TextView
    private lateinit var resultUnitLabel: TextView
    
    // Loading dialog
    private var loadingDialog: LoadingDialog? = null
    private var dropdownListenersAttached = false
    
    // Pre-selected values from mission data
    private var preselectedItemName: String? = null
    private var preselectedAppDataProviderId: Int = 0
    private var preselectedViolationId: Int = 0
    private var preselectedViolationGroupId: Int = 0
    private var organTitle: String? = null
    private var contractTitle: String? = null
    private var missionVisitDate: String? = null
    private var billCleaningItemGroupID: Int = 0
    private var billCleaningItemGroupName: String? = null
    private var billCleaningItemID: Int = 0
    protected var billCleaningItemName: String? = null
    protected var isFromDailyPlanItem: Boolean = false
    protected var dailyPlanActiveViolations: List<DailyPlanItemViolationHelper.ItemViolation> = emptyList()

    protected data class SelectorFieldViews(
        val container: View,
        val text: TextView,
        val progress: ProgressBar,
        val hintRes: Int,
    )

    protected var violationGroupSelector: SelectorFieldViews? = null
    protected var defectSelector: SelectorFieldViews? = null
    protected var seasonSelector: SelectorFieldViews? = null
    protected var priceRowSelector: SelectorFieldViews? = null

    protected open fun usesModernSelectorUi(): Boolean = false

    protected open fun shouldShowSeasonAndPriceSelectors(): Boolean = true

    protected open fun shouldFinishOnSubmissionSuccess(): Boolean = true

    protected open fun onSubmissionSuccessCompleted() {}

    protected fun resolveDailyPlanItemSubtitle(): String {
        billCleaningItemGroupName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val itemData = intent.getStringExtra("item_data") ?: return ""
        return try {
            val item = org.json.JSONObject(itemData)
            val width = item.optJSONObject("billOriginCleaningItem")
                ?.optJSONObject("billCleaningItem")
                ?.optString("widthLabel", "")
                ?.trim().orEmpty()
            if (width.isNotEmpty()) width else item.optString("description", "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraImageUri != null) {
                selectedImages.add(cameraImageUri!!)
                updateImageDisplay()
            } else {
                Toast.makeText(this, "خطا در گرفتن تصویر", Toast.LENGTH_SHORT).show()
            }
        }

    @Inject
    lateinit var roadService: Ir.co.tfs.farazaman.service.remote.RoadService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== SubmitViolationFormActivity onCreate started ===")
        enableEdgeToEdge()
        setContentView(layoutId())

        findViewById<Toolbar?>(R.id.my_toolbar)?.let { myToolbar ->
            setSupportActionBar(myToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                setHomeAsUpIndicator(R.drawable.ic_arrow_back)
                setDisplayShowTitleEnabled(false)
            }
            myToolbar.layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
        }
        findViewById<View?>(R.id.btnBack)?.setOnClickListener { finish() }

        initializeViews()
        setupObservers()
        setupDatePicker()
        setupSaveButton()
        setupImagePicker()
        setupCalculationListener()
        // In onCreate, before fetchFormData/fetchFormDataWithPreselectedValues, set loading text for dropdowns
        organsTextView.setText("در حال دریافت...", false)
        contractNumberTextView.setText("در حال دریافت...", false)
        violationGroupTextView.setText("در حال دریافت...", false)
        defectTextView.setText("در حال دریافت...", false)
        seasonOfPriceTextView.setText("در حال دریافت...", false)
        price_listTextView.setText("در حال دریافت...", false)
        syncModernSelectorDisplays()

        // For address, in fetchAddressFromRoadService, set loading text before API call
        latitude = intent.getDoubleExtra(Ir.co.tfs.farazaman.activity.DisplayTrackMap.EXTRA_LATITUDE, 0.0)
        longitude = intent.getDoubleExtra(Ir.co.tfs.farazaman.activity.DisplayTrackMap.EXTRA_LONGITUDE, 0.0)
        address = intent.getStringExtra("extra_address") ?: ""
        currentTrackId = intent.getLongExtra(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_TRACK_ID, -1)
        
        // Check if we have mission data from icon_violation click
        val organId = intent.getIntExtra("organ_id", -1)
        val contractId = intent.getIntExtra("contract_id", -1)
        organTitle = intent.getStringExtra("organ_title")
        contractTitle = intent.getStringExtra("contract_title")
        missionVisitDate = intent.getStringExtra("visit_date")
        val visitDate = missionVisitDate
        val itemData = intent.getStringExtra("item_data")
        
        // Check the source of the click to determine which views to hide
        val isFromIconViolation = intent.getBooleanExtra("from_icon_violation", false)
        val isFromSubmitViolationCard = intent.getBooleanExtra("from_submit_violation_card", false)
        
        // Debug logs
        Log.d(TAG, "=== DEBUG: Intent Data ===")
        Log.d(TAG, "organId: $organId")
        Log.d(TAG, "contractId: $contractId")
        Log.d(TAG, "organTitle: $organTitle")
        Log.d(TAG, "contractTitle: $contractTitle")
        Log.d(TAG, "visitDate: $visitDate")
        Log.d(TAG, "itemData: $itemData")
        Log.d(TAG, "========================")
        
        // Parse item data to extract additional values
        if (itemData != null) {
            try {
                val itemJson = org.json.JSONObject(itemData)
                this.billCleaningItemGroupID = itemJson.optInt("billCleaningItemGroupID", 0)
                this.billCleaningItemGroupName = itemJson.optString("billCleaningItemGroupName", "")
                this.billCleaningItemID = itemJson.optInt("billCleaningItemID", 0)
                this.billCleaningItemName = itemJson.optString("billCleaningItemName", "")
                if (isFromIconViolation) {
                    isFromDailyPlanItem = true
                    dailyPlanActiveViolations = DailyPlanItemViolationHelper.parseActiveViolations(itemJson)
                }
                
                Log.d(TAG, "=== DEBUG: Parsed Item Data ===")
                Log.d(TAG, "billCleaningItemGroupID: $billCleaningItemGroupID")
                Log.d(TAG, "billCleaningItemGroupName: $billCleaningItemGroupName")
                Log.d(TAG, "billCleaningItemID: $billCleaningItemID")
                Log.d(TAG, "billCleaningItemName: $billCleaningItemName")
                Log.d(TAG, "===============================")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing item data: ${e.message}")
            }
        }
        
        // Only pre-fill address if it was sent from DisplayTrackMap (not TrackManager)
        if (address.isNotEmpty()) {
            addressEditText.setText(address)
        } else if (latitude != 0.0 && longitude != 0.0) {
            // Fetch address from RoadService in background
            fetchAddressFromRoadService(latitude, longitude)
        }
        
        // بررسی اینکه آیا موقعیت معتبری دریافت شده است یا خیر
        if (latitude != 0.0 && longitude != 0.0) {
            // برای اطمینان از دریافت، آن را در لاگ و با یک Toast نمایش می‌دهیم
            val locationMessage = "موقعیت دریافت شد: $latitude, $longitude"
            Log.d(TAG, locationMessage)
//            Toast.makeText(this, locationMessage, Toast.LENGTH_LONG).show()
        }
        
        // If we have mission data, call form data service with pre-selected values
        if (organId != -1 && contractId != -1 && visitDate != null) {
            Log.d(TAG, "Loading form data with pre-selected values: organId=$organId, contractId=$contractId, visitDate=$visitDate")
            fetchFormDataWithPreselectedValues(organId, contractId, visitDate, itemData)
        } else {
            // Regular form data loading
            Log.d(TAG, "Loading regular form data (no pre-selected values)")
            fetchFormData()
        }
        onFormUiReady()
    }

    protected open fun layoutId(): Int = R.layout.activity_submit_violation_form

    protected open fun onFormUiReady() {}

    override fun onResume() {
        super.onResume()
        // Update GPS warning visibility when activity resumes
        updateGpsWarningVisibility()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Navigate back to DisplayTrackMap
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeViews() {
        organsTextView = findViewById(R.id.organs)
        contractNumberTextView = findViewById(R.id.contracts)
        violationGroupTextView = findViewById(R.id.billCleaningViolationGroups)
        defectTextView = findViewById(R.id.billCleaningViolations)
        seasonOfPriceTextView = findViewById(R.id.billCleaningItemGroups)
        price_listTextView = findViewById(R.id.billOriginCleaningItems)
        btnPickImage = findViewById(R.id.image_picker)
        imagesRecyclerView = findViewById(R.id.images_recycler_view)
        addressEditText = findViewById(R.id.adress)
        gpsWarningTextView = findViewById(R.id.gps_warning)
        countUnitLabel = findViewById(R.id.count_unit_label)
        resultUnitLabel = findViewById(R.id.result_unit_label)
        
        // Setup RecyclerView
        imagesRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        setupImagesAdapter()
        
        // Update GPS warning visibility
        updateGpsWarningVisibility()
        
        bindModernSelectorViews()
        // Hide views based on the source of the click
        hideViewsBasedOnSource()
    }

    protected open fun bindModernSelectorViews() {
        if (!usesModernSelectorUi()) return
        if (isFromDailyPlanItem) {
            defectSelector = bindSelectorBlock(
                R.id.defectSelectorBlock,
                R.string.violation_select_defect_hint,
                R.string.violation_defect_title_label,
            )
            return
        }
        violationGroupSelector = bindSelectorBlock(
            R.id.violationGroupSelectorBlock,
            R.string.violation_select_group_hint,
            R.string.violation_group_label,
        )
        defectSelector = bindSelectorBlock(
            R.id.defectSelectorBlock,
            R.string.violation_select_defect_hint,
            R.string.violation_defect_title_label,
        )
    }

    private fun bindSelectorBlock(blockId: Int, hintRes: Int, labelRes: Int): SelectorFieldViews? {
        val block = findViewById<View>(blockId) ?: return null
        block.findViewById<TextView>(R.id.selectorLabel).setText(labelRes)
        return SelectorFieldViews(
            container = block.findViewById(R.id.selectorContainer),
            text = block.findViewById(R.id.selectorText),
            progress = block.findViewById(R.id.selectorProgress),
            hintRes = hintRes,
        )
    }

    protected fun setupModernSelectorListeners() {
        if (!usesModernSelectorUi()) return
        violationGroupSelector?.container?.setOnClickListener {
            openDropdownSelector(
                title = getString(R.string.violation_select_group_title),
                items = formDataViewModel.getViolationGroups(),
            ) { item ->
                violationGroupTextView.setText(item.text, false)
                formDataViewModel.onViolationGroupIdSelected(item.value)
                defectTextView.setText("در حال دریافت...", false)
                syncModernSelectorDisplays()
                formDataViewModel.updateViolationGroupSelection(item.value)
            }
        }
        defectSelector?.container?.setOnClickListener {
            openDropdownSelector(
                title = getString(R.string.violation_select_defect_title),
                items = formDataViewModel.getViolations(),
            ) { item ->
                defectTextView.setText(item.text, false)
                formDataViewModel.onViolationIdSelected(item.value)
                formDataViewModel.updateViolationSelection(item.value)
            }
        }
        seasonSelector?.container?.setOnClickListener {
            openDropdownSelector(
                title = getString(R.string.violation_select_season_title),
                items = formDataViewModel.getItemGroups(),
            ) { item ->
                seasonOfPriceTextView.setText(item.text, false)
                formDataViewModel.onItemGroupIdSelected(item.value)
                formDataViewModel.updateItemGroupSelection(item.value)
            }
        }
        priceRowSelector?.container?.setOnClickListener {
            openDropdownSelector(
                title = getString(R.string.violation_select_price_row_title),
                items = formDataViewModel.getOriginItems(),
            ) { item ->
                price_listTextView.setText(item.text, false)
                formDataViewModel.onOriginItemIdSelected(item.value)
                formDataViewModel.updateOriginItemSelection(item.value)
            }
        }
    }

    protected fun openDropdownSelector(
        title: String,
        items: List<DropdownItem>,
        onSelected: (DropdownItem) -> Unit,
    ) {
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.violation_options_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        MissionItemSelectionBottomSheet.newInstance(
            title,
            items.map(MissionSelectionOption::fromDropdown),
        ).apply {
            onItemSelected = { option ->
                items.find { it.value == option.id }?.let(onSelected)
            }
        }.show(supportFragmentManager, "violation_item_selection")
    }

    protected fun syncModernSelectorDisplays() {
        if (!usesModernSelectorUi()) return
        violationGroupSelector?.let { syncOneSelector(it, violationGroupTextView) }
        defectSelector?.let { syncOneSelector(it, defectTextView) }
        seasonSelector?.let { syncOneSelector(it, seasonOfPriceTextView) }
        priceRowSelector?.let { syncOneSelector(it, price_listTextView) }
    }

    private fun syncOneSelector(field: SelectorFieldViews, source: AutoCompleteTextView) {
        val value = source.text?.toString()?.trim().orEmpty()
        when {
            value == "در حال دریافت..." -> {
                field.progress.visibility = View.VISIBLE
                field.text.visibility = View.INVISIBLE
                field.container.isClickable = false
            }
            value.isEmpty() -> {
                field.progress.visibility = View.GONE
                field.text.visibility = View.VISIBLE
                field.text.text = null
                field.text.setHint(field.hintRes)
                field.container.isClickable = formDataViewModel.formData.value != null
            }
            else -> {
                field.progress.visibility = View.GONE
                field.text.visibility = View.VISIBLE
                field.text.text = value
                field.container.isClickable = true
            }
        }
    }

    protected open fun onDropdownsUpdated(formData: Ir.co.tfs.farazaman.data.model.FormDataResponse) {}

    protected open fun setupDailyPlanDefectSelector() {
        if (!isFromDailyPlanItem) return

        findViewById<View>(R.id.dailyPlanDefectSection)?.visibility = View.VISIBLE
        val saveButton = findViewById<Button>(R.id.save)

        if (dailyPlanActiveViolations.isEmpty()) {
            defectTextView.setText("", false)
            saveButton.isEnabled = false
            defectSelector?.container?.isClickable = false
            syncModernSelectorDisplays()
            return
        }

        saveButton.isEnabled = true
        val dropdownItems = DailyPlanItemViolationHelper.toDropdownItems(dailyPlanActiveViolations)
        val first = dailyPlanActiveViolations.first()
        defectTextView.setText(first.title, false)
        formDataViewModel.onViolationIdSelected(first.id)
        if (first.groupId > 0) {
            formDataViewModel.onViolationGroupIdSelected(first.groupId)
        }

        defectSelector?.container?.setOnClickListener {
            openDropdownSelector(
                title = getString(R.string.violation_select_defect_title),
                items = dropdownItems,
            ) { item ->
                defectTextView.setText(item.text, false)
                formDataViewModel.onViolationIdSelected(item.value)
                dailyPlanActiveViolations.find { it.id == item.value }?.let { violation ->
                    if (violation.groupId > 0) {
                        formDataViewModel.onViolationGroupIdSelected(violation.groupId)
                    }
                }
                onDailyPlanDefectSelected()
            }
        }
        syncModernSelectorDisplays()
        onDailyPlanDefectSelected()
    }

    protected open fun onDailyPlanDefectSelected() {}

    private fun clearDependentFieldLoading() {
        if (::defectTextView.isInitialized &&
            defectTextView.text?.toString() == "در حال دریافت..."
        ) {
            defectTextView.setText("", false)
        }
        syncModernSelectorDisplays()
    }

    private fun setupObservers() {
        formDataViewModel.formData.observe(this) { formData ->
            onFormDataLoaded(formData)
            Log.d(TAG, "=== DEBUG: FormData received ===")
            Log.d(TAG, "organs count: ${formData.response.organs.size}")
            Log.d(TAG, "contracts count: ${formData.response.contracts.size}")
            Log.d(TAG, "organTitle: $organTitle")
            Log.d(TAG, "contractTitle: $contractTitle")
            Log.d(TAG, "preselectedItemName: $preselectedItemName")
            
            if (preselectedItemName != null) {
                Log.d(TAG, "Calling updateDropdownsWithPreselectedValues")
                updateDropdownsWithPreselectedValues(formData)
            } else {
                Log.d(TAG, "Calling updateDropdownsWithPreservedSelections")
                updateDropdownsWithPreservedSelections(formData)
            }
        }

        formDataViewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                loadingDialog?.dismiss()
                loadingDialog = LoadingDialog.showWithTimeout(
                    this,
                    "در حال بارگذاری اطلاعات...",
                    180000,
                ) { formDataViewModel.cancelFormDataLoading() }
            } else {
                loadingDialog?.dismiss()
                loadingDialog = null
            }
        }

        formDataViewModel.error.observe(this) { error ->
            if (error != null) {
                clearDependentFieldLoading()
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error fetching form data: $error")
                formDataViewModel.clearError()
            }
        }

        // این بخش را به متد setupObservers اضافه کنید
        formDataViewModel.submissionState.observe(this) { state ->
            when (state) {
                null -> {
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    findViewById<Button>(R.id.save).isEnabled = true
                }
                is SubmissionState.Loading -> {
                    loadingDialog?.dismiss()
                    loadingDialog = LoadingDialog.showWithTimeout(
                        this,
                        "در حال ارسال...",
                        180000,
                    ) { formDataViewModel.cancelSubmission() }
                    findViewById<Button>(R.id.save).isEnabled = false
                }
                is SubmissionState.Success -> {
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    findViewById<Button>(R.id.save).isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    if (currentTrackId != -1L) {
                        SupervisorViolationHistoryStore.add(
                            context = this,
                            title = violationTitleForHistory(),
                            subtitle = violationSubtitleForHistory(),
                            trackId = currentTrackId,
                            violationId = state.violationId,
                        )
                    }
                    if (currentTrackId != -1L && latitude != 0.0) {
                        formDataViewModel.saveViolationAsWaypoint(currentTrackId, latitude, longitude, state.violationId)
                    }
                    if (shouldFinishOnSubmissionSuccess()) {
                        finish()
                    } else {
                        onSubmissionSuccessCompleted()
                    }
                }
                is SubmissionState.Error -> {
                    // نمایش خطا
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    findViewById<Button>(R.id.save).isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun saveViolationAsWaypoint(violationId: String) {
        val values = ContentValues().apply {
            put(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
            put(TrackContentProvider.Schema.COL_LATITUDE, latitude)
            put(TrackContentProvider.Schema.COL_LONGITUDE, longitude)
            // می‌توانید از شناسه تخلف به عنوان نام یا لینک استفاده کنید
            put(TrackContentProvider.Schema.COL_NAME, "تخلف ثبت شده")
            put(TrackContentProvider.Schema.COL_LINK, violationId)
            put(TrackContentProvider.Schema.COL_TIMESTAMP, System.currentTimeMillis())
        }

        // ذخیره در دیتابیس از طریق ContentResolver
        val waypointUri = contentResolver.insert(TrackContentProvider.CONTENT_URI_WAYPOINT, values)

        if (waypointUri != null) {
            Log.d("SubmitViolationForm", "Waypoint saved successfully with ID from server: $violationId")
        } else {
            Log.e("SubmitViolationForm", "Failed to save waypoint.")
        }
    }
    private fun updateDropdownsWithPreservedSelections(formData: Ir.co.tfs.farazaman.data.model.FormDataResponse) {
        // Store current selections
        val currentOrganText = organsTextView.text.toString()
        val currentContractText = contractNumberTextView.text.toString()
        val currentViolationGroupText = violationGroupTextView.text.toString()
        val currentViolationText = defectTextView.text.toString()
        val currentItemGroupText = seasonOfPriceTextView.text.toString()
        val currentOriginItemText = price_listTextView.text.toString()
        
        // If we have pre-selected values from mission data, use them
        if (preselectedItemName != null) {
            updateDropdownsWithPreselectedValues(formData)
            return
        }

        // Check if we have organ/contract IDs from intent (from submit_violation_card)
        val intentOrganId = intent.getIntExtra("organ_id", -1)
        val intentContractId = intent.getIntExtra("contract_id", -1)
        val hasIntentMissionData = intentOrganId > 0 && intentContractId > 0
        
        Log.d(TAG, "=== updateDropdownsWithPreservedSelections ===")
        Log.d(TAG, "intentOrganId: $intentOrganId, intentContractId: $intentContractId")
        Log.d(TAG, "organTitle: $organTitle, contractTitle: $contractTitle")
        Log.d(TAG, "hasIntentMissionData: $hasIntentMissionData")

        // Update organs dropdown
        val organs = formData.response.organs
        val organsAdapter = ArrayAdapter(this, R.layout.dropdown_item, organs.map { it.text })
        organsTextView.setAdapter(organsAdapter)
        if (organs.isNotEmpty()) {
            // Priority: 1) Intent organ ID, 2) Current text, 3) First item
            if (hasIntentMissionData && organTitle != null) {
                // Use organ from intent (submit_violation_card case)
                organsTextView.setText(organTitle, false)
                formDataViewModel.onOrganSelected(intentOrganId)
                Log.d(TAG, "Using organ from intent: '$organTitle' (ID: $intentOrganId)")
            } else if (currentOrganText.isNotEmpty() && organs.any { it.text == currentOrganText }) {
                // Try to preserve current selection
                val selectedOrgan = organs.firstOrNull { it.text == currentOrganText }
                organsTextView.setText(currentOrganText, false)
                selectedOrgan?.value?.let {
                    formDataViewModel.onOrganSelected(it)
                    Log.d(TAG, "Preserving current organ: '$currentOrganText' (ID: $it)")
                }
            } else {
                // Use first item as fallback
                organsTextView.setText(organs[0].text, false)
                formDataViewModel.onOrganSelected(organs[0].value)
                Log.d(TAG, "Using first organ: '${organs[0].text}' (ID: ${organs[0].value})")
            }
        }

        // Update contracts dropdown
        val contracts = formData.response.contracts
        val contractsAdapter = ArrayAdapter(this, R.layout.dropdown_item, contracts.map { it.text })
        contractNumberTextView.setAdapter(contractsAdapter)
        if (contracts.isNotEmpty()) {
            // Priority: 1) Intent contract ID, 2) Current text, 3) First item
            if (hasIntentMissionData && contractTitle != null) {
                // Use contract from intent (submit_violation_card case)
                contractNumberTextView.setText(contractTitle, false)
                formDataViewModel.onContractSelected(intentContractId)
                Log.d(TAG, "Using contract from intent: '$contractTitle' (ID: $intentContractId)")
            } else if (currentContractText.isNotEmpty() && contracts.any { it.text == currentContractText }) {
                // Try to preserve current selection
                val selectedContract = contracts.firstOrNull { it.text == currentContractText }
                contractNumberTextView.setText(currentContractText, false)
                selectedContract?.value?.let {
                    formDataViewModel.onContractSelected(it)
                    Log.d(TAG, "Preserving current contract: '$currentContractText' (ID: $it)")
                }
            } else {
                // Use first item as fallback
                contractNumberTextView.setText(contracts[0].text, false)
                formDataViewModel.onContractSelected(contracts[0].value)
                Log.d(TAG, "Using first contract: '${contracts[0].text}' (ID: ${contracts[0].value})")
            }
        }

        // Update violation groups dropdown
        val violationGroups = formData.response.billCleaningViolationGroups
        val violationGroupsAdapter = ArrayAdapter(this, R.layout.dropdown_item, violationGroups.map { it.text })
        violationGroupTextView.setAdapter(violationGroupsAdapter)
        if (violationGroups.isNotEmpty()) {
            val violationGroupToSelect = if (currentViolationGroupText.isNotEmpty() && violationGroups.any { it.text == currentViolationGroupText }) {
                currentViolationGroupText
            } else {
                violationGroups[0].text
            }
            violationGroupTextView.setText(violationGroupToSelect, false)
            val selectedViolationGroup = violationGroups.firstOrNull { it.text == violationGroupToSelect }
            selectedViolationGroup?.value?.let {
                formDataViewModel.onViolationGroupIdSelected(it)
            }
        }

        // Update violations dropdown
        val violations = formData.response.billCleaningViolations
        val violationsAdapter = ArrayAdapter(this, R.layout.dropdown_item, violations.map { it.text })
        defectTextView.setAdapter(violationsAdapter)
        if (violations.isNotEmpty()) {
            val violationToSelect = if (currentViolationText.isNotEmpty() && violations.any { it.text == currentViolationText }) {
                currentViolationText
            } else {
                violations[0].text
            }
            defectTextView.setText(violationToSelect, false)
            val selectedViolation = violations.firstOrNull { it.text == violationToSelect }
            selectedViolation?.value?.let {
                formDataViewModel.onViolationIdSelected(it)
            }
        }

        // Update season of price dropdown
        val itemGroups = formData.response.billCleaningItemGroups
        val itemGroupsAdapter = ArrayAdapter(this, R.layout.dropdown_item, itemGroups.map { it.text })
        seasonOfPriceTextView.setAdapter(itemGroupsAdapter)
        if (itemGroups.isNotEmpty()) {
            val itemGroupToSelect = if (currentItemGroupText.isNotEmpty() && itemGroups.any { it.text == currentItemGroupText }) {
                currentItemGroupText
            } else {
                itemGroups[0].text
            }
            seasonOfPriceTextView.setText(itemGroupToSelect, false)
            val selectedItemGroup = itemGroups.firstOrNull { it.text == itemGroupToSelect }
            selectedItemGroup?.value?.let {
                formDataViewModel.onItemGroupIdSelected(it)
            }
        }

        // Update price list dropdown
        val originItems = formData.response.billOriginCleaningItems
        val originItemsAdapter = ArrayAdapter(this, R.layout.dropdown_item, originItems.map { it.text })
        price_listTextView.setAdapter(originItemsAdapter)
        if (originItems.isNotEmpty()) {
            val originItemToSelect = if (currentOriginItemText.isNotEmpty() && originItems.any { it.text == currentOriginItemText }) {
                currentOriginItemText
            } else {
                originItems[0].text
            }
            price_listTextView.setText(originItemToSelect, false)
            val selectedOrigin = originItems.firstOrNull { it.text == originItemToSelect }
            selectedOrigin?.value?.let {
                formDataViewModel.onOriginItemIdSelected(it)
            }
        }

        // Set up dropdown listeners for dynamic updates
        setupDropdownListeners(formData)
        
        // Update unit labels
        updateUnitLabels(formData)
        syncModernSelectorDisplays()
        onDropdownsUpdated(formData)
    }

    private fun setupDropdownListeners(formData: Ir.co.tfs.farazaman.data.model.FormDataResponse) {
        if (dropdownListenersAttached) return
        dropdownListenersAttached = true

        organsTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedOrgan = formData.response.organs[position]
            Log.d(TAG, "Selected organ: ${selectedOrgan.text} (${selectedOrgan.value})")
            // Update form data with selected organ
            selectedOrgan.value.let { organId ->
                formDataViewModel.onOrganSelected(organId)
            }
            formDataViewModel.updateOrganSelection(selectedOrgan.value)
        }

        contractNumberTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedContract = formData.response.contracts[position]
            Log.d(TAG, "Selected contract: ${selectedContract.text} (${selectedContract.value})")
            // Update form data with selected contract
            formDataViewModel.updateContractSelection(selectedContract.value)

            selectedContract.value.let { contractId ->
                formDataViewModel.onContractSelected(contractId)
            }
        }

        violationGroupTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedViolationGroup = formData.response.billCleaningViolationGroups[position]
            Log.d(TAG, "Selected violation group: ${selectedViolationGroup.text} (${selectedViolationGroup.value})")
            // Update form data with selected violation group
            formDataViewModel.updateViolationGroupSelection(selectedViolationGroup.value)
            selectedViolationGroup.value.let { contractId ->
                formDataViewModel.onViolationGroupIdSelected(contractId)
            }
        }

        defectTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedViolation = formData.response.billCleaningViolations[position]
            Log.d(TAG, "Selected violation: ${selectedViolation.text} (${selectedViolation.value})")
            // Update form data with selected violation
            formDataViewModel.updateViolationSelection(selectedViolation.value)
            selectedViolation.value.let { groupId ->
                formDataViewModel.onViolationIdSelected(groupId)
            }
        }

        seasonOfPriceTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedItemGroup = formData.response.billCleaningItemGroups[position]
            Log.d(TAG, "Selected item group: ${selectedItemGroup.text} (${selectedItemGroup.value})")
            // Update form data with selected item group
            formDataViewModel.updateItemGroupSelection(selectedItemGroup.value)
            selectedItemGroup.value.let { groupId ->
                formDataViewModel.onItemGroupIdSelected(groupId)
            }
        }
        
        price_listTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedOriginItem = formData.response.billOriginCleaningItems[position]
            Log.d(TAG, "Selected origin item: ${selectedOriginItem.text} (${selectedOriginItem.value})")
            // Update form data with selected origin item
            formDataViewModel.updateOriginItemSelection(selectedOriginItem.value)
            selectedOriginItem.value.let { originItemId ->
                formDataViewModel.onOriginItemIdSelected(originItemId)
            }
        }
    }

    private fun fetchFormData() {
        formDataViewModel.fetchFormData()
    }
    
    private fun fetchFormDataWithPreselectedValues(organId: Int, contractId: Int, visitDate: String, itemData: String?) {
        Log.d(TAG, "=== DEBUG: fetchFormDataWithPreselectedValues ===")
        Log.d(TAG, "organId: $organId")
        Log.d(TAG, "contractId: $contractId")
        Log.d(TAG, "visitDate: $visitDate")
        Log.d(TAG, "itemData: $itemData")
        
        // Create a FormDataRequest with the pre-selected values
        val request = Ir.co.tfs.farazaman.data.model.FormDataRequest(
            contractIds = listOf(contractId),
            organIds = listOf(organId),
            billCleaningViolationGroupIds = if (preselectedViolationGroupId > 0) listOf(preselectedViolationGroupId) else emptyList(),
            billCleaningViolationIds = if (preselectedViolationId > 0) listOf(preselectedViolationId) else emptyList(),
            billCleaningItemGroupIds = emptyList(),
            billCleaningItemIds = emptyList(),
            visitDate = visitDate,
            maxVisitDate = "",
            minVisitDate = "",
            isDeleted = false,
            tenantId = 1,
            billOriginCleaningItemIds = emptyList(),
            billCleaningViolationId = preselectedViolationId,
            contractId = contractId,
            organId = organId
        )
        
        Log.d(TAG, "Created FormDataRequest: $request")
        Log.d(TAG, "FormDataRequest Details:")
        Log.d(TAG, "  - billCleaningViolationGroupIds: ${request.billCleaningViolationGroupIds}")
        Log.d(TAG, "  - billCleaningViolationIds: ${request.billCleaningViolationIds}")
        Log.d(TAG, "  - billCleaningViolationId: ${request.billCleaningViolationId}")
        
        // Store item data for later use when populating dropdowns
        if (itemData != null) {
            try {
                val itemJson = org.json.JSONObject(itemData)
                preselectedItemName = itemJson.optString("billCleaningItemName", "")
                preselectedAppDataProviderId = itemJson.optInt("appDataProviderID", 0)
                preselectedViolationId = itemJson.optInt("billCleaningViolationID", 0)
                preselectedViolationGroupId = itemJson.optInt("billCleaningViolationGroupId", 0)
                
                Log.d(TAG, "Parsed item data:")
                Log.d(TAG, "  - itemName: $preselectedItemName")
                Log.d(TAG, "  - appDataProviderId: $preselectedAppDataProviderId")
                Log.d(TAG, "  - billCleaningViolationID: $preselectedViolationId")
                Log.d(TAG, "  - billCleaningViolationGroupId: $preselectedViolationGroupId")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing item data: ${e.message}")
            }
        }
        
        Log.d(TAG, "Calling formDataViewModel.fetchFormDataWithRequest...")
        formDataViewModel.fetchFormDataWithRequest(request)
    }
    
    private fun updateDropdownsWithPreselectedValues(formData: Ir.co.tfs.farazaman.data.model.FormDataResponse) {
        Log.d(TAG, "=== DEBUG: updateDropdownsWithPreselectedValues ===")
        Log.d(TAG, "organTitle: $organTitle")
        Log.d(TAG, "contractTitle: $contractTitle")
        Log.d(TAG, "itemName: $preselectedItemName, appDataProviderId: $preselectedAppDataProviderId")
        Log.d(TAG, "preselectedViolationId: $preselectedViolationId")
        Log.d(TAG, "preselectedViolationGroupId: $preselectedViolationGroupId")
        Log.d(TAG, "FormData Response Details:")
        Log.d(TAG, "  - billCleaningViolationGroups count: ${formData.response.billCleaningViolationGroups.size}")
        Log.d(TAG, "  - billCleaningViolations count: ${formData.response.billCleaningViolations.size}")
        Log.d(TAG, "  - Available violation groups: ${formData.response.billCleaningViolationGroups.map { "${it.text}(${it.value})" }}")
        Log.d(TAG, "  - Available violations: ${formData.response.billCleaningViolations.map { "${it.text}(${it.value})" }}")
        
        // Set organ name and ID directly
        val organId = intent.getIntExtra("organ_id", 0)
        organsTextView.setText(organTitle ?: "", false)
        formDataViewModel.onOrganSelected(organId)
        Log.d(TAG, "Set organ name: '$organTitle' with ID: $organId")
        
        // Set contract name and ID directly
        val contractId = intent.getIntExtra("contract_id", 0)
        contractNumberTextView.setText(contractTitle ?: "", false)
        formDataViewModel.onContractSelected(contractId)
        Log.d(TAG, "Set contract name: '$contractTitle' with ID: $contractId")
        
        // Set billCleaningItemGroups name and ID directly
        seasonOfPriceTextView.setText(billCleaningItemGroupName ?: "", false)
        formDataViewModel.onItemGroupIdSelected(billCleaningItemGroupID)
        Log.d(TAG, "Set billCleaningItemGroups name: '$billCleaningItemGroupName' with ID: $billCleaningItemGroupID")
        
        // Set billCleaningItemID and billCleaningItemName directly
        price_listTextView.setText(billCleaningItemName ?: "", false)
        formDataViewModel.onOriginItemIdSelected(billCleaningItemID)
        Log.d(TAG, "Set billCleaningItemName: '$billCleaningItemName' with ID: $billCleaningItemID")
        
        // Set up dropdown adapters with available options from FormData response
        val organs = formData.response.organs
        val organsAdapter = ArrayAdapter(this, R.layout.dropdown_item, organs.map { it.text })
        organsTextView.setAdapter(organsAdapter)
        
        val contracts = formData.response.contracts
        val contractsAdapter = ArrayAdapter(this, R.layout.dropdown_item, contracts.map { it.text })
        contractNumberTextView.setAdapter(contractsAdapter)
        
        // Update violation groups dropdown - try to select pre-selected value, otherwise first available
        if (isFromDailyPlanItem) {
            setupDailyPlanDefectSelector()
        } else {
        val violationGroups = formData.response.billCleaningViolationGroups
        val violationGroupsAdapter = ArrayAdapter(this, R.layout.dropdown_item, violationGroups.map { it.text })
        violationGroupTextView.setAdapter(violationGroupsAdapter)
        if (violationGroups.isNotEmpty()) {
            val selectedViolationGroup = violationGroups.find { it.value == preselectedViolationGroupId }
            if (selectedViolationGroup != null) {
                violationGroupTextView.setText(selectedViolationGroup.text, false)
                formDataViewModel.onViolationGroupIdSelected(selectedViolationGroup.value)
                Log.d(TAG, "Set pre-selected violation group: '${selectedViolationGroup.text}' with ID: ${selectedViolationGroup.value}")
            } else {
                violationGroupTextView.setText(violationGroups[0].text, false)
                formDataViewModel.onViolationGroupIdSelected(violationGroups[0].value)
                Log.d(TAG, "Set first available violation group: '${violationGroups[0].text}' with ID: ${violationGroups[0].value}")
            }
        }
        
        // Update violations dropdown - try to select pre-selected value, otherwise first available
        val violations = formData.response.billCleaningViolations
        val violationsAdapter = ArrayAdapter(this, R.layout.dropdown_item, violations.map { it.text })
        defectTextView.setAdapter(violationsAdapter)
        if (violations.isNotEmpty()) {
            val selectedViolation = violations.find { it.value == preselectedViolationId }
            if (selectedViolation != null) {
                defectTextView.setText(selectedViolation.text, false)
                formDataViewModel.onViolationIdSelected(selectedViolation.value)
                Log.d(TAG, "Set pre-selected violation: '${selectedViolation.text}' with ID: ${selectedViolation.value}")
            } else {
                defectTextView.setText(violations[0].text, false)
                formDataViewModel.onViolationIdSelected(violations[0].value)
                Log.d(TAG, "Set first available violation: '${violations[0].text}' with ID: ${violations[0].value}")
            }
        }
        }
        
        // Set up dropdown adapters with available options from FormData response
        val itemGroups = formData.response.billCleaningItemGroups
        val itemGroupsAdapter = ArrayAdapter(this, R.layout.dropdown_item, itemGroups.map { it.text })
        seasonOfPriceTextView.setAdapter(itemGroupsAdapter)
        
        val originItems = formData.response.billOriginCleaningItems
        val originItemsAdapter = ArrayAdapter(this, R.layout.dropdown_item, originItems.map { it.text })
        price_listTextView.setAdapter(originItemsAdapter)
        
        // Set up dropdown listeners for dynamic updates
        setupDropdownListeners(formData)
        
        // Update unit labels
        updateUnitLabels(formData)
        syncModernSelectorDisplays()
        onDropdownsUpdated(formData)
    }

    private fun populateDropdowns(formData: Ir.co.tfs.farazaman.data.model.FormDataResponse) {
        // Initial population of dropdowns (called only once)
        updateDropdownsWithPreservedSelections(formData)
    }

    private fun setupImagePicker() {
        // ** این بخش اصلاح شده است **
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // پس از اینکه کاربر به دیالوگ دسترسی پاسخ داد، این کد اجرا می‌شود
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

            // اگر دسترسی دوربین داده شده بود، دیالوگ را نمایش بده
            if (cameraGranted) {
                openCamera()
            } else {
                Toast.makeText(this, "برای استفاده از دوربین، به دسترسی نیاز است.", Toast.LENGTH_SHORT).show()
            }
        }

        btnPickImage.setOnClickListener {
            checkAndRequestPermissions()
        }
    }


    private fun setupDatePicker() {
        val dateEditText = findViewById<TextView>(R.id.Date_of_performance_registration)
        
        // Check if visit_date was passed from TrackManager
        val visitDate = intent.getStringExtra("visit_date")
        
        if (visitDate != null && visitDate.isNotEmpty()) {
            // Use the mission date passed from TrackManager
            Log.d(TAG, "Using mission date from TrackManager: $visitDate")
            
            // Try to parse the visit_date to set selectedDate
            try {
                // Check if the date is in Gregorian format (YYYY-MM-DD)
                if (visitDate.contains("-")) {
                    // Parse Gregorian date and convert to Persian
                    val parts = visitDate.split("-")
                    if (parts.size == 3) {
                        val year = parts[0].toInt()
                        val month = parts[1].toInt()
                        val day = parts[2].toInt()
                        
                        // Convert Gregorian date to Persian date
                        val gregorianDate = java.util.Calendar.getInstance()
                        gregorianDate.set(year, month - 1, day) // month is 0-based in Calendar
                        
                        val persianDate = PersianDate(gregorianDate.time)
                        selectedDate = gregorianDate.time
                        
                        // Format the Persian date properly for display
                        val formattedPersianDate = formatPersianDate(
                            persianDate.shYear,
                            persianDate.shMonth,
                            persianDate.shDay
                        )
                        dateEditText.text = formattedPersianDate
                        
                        Log.d(TAG, "Successfully converted Gregorian date to Persian: $formattedPersianDate")
                    } else {
                        Log.e(TAG, "Invalid Gregorian date format, expected YYYY-MM-DD but got: $visitDate")
                        setCurrentPersianDate(dateEditText)
                    }
                } else {
                    // Try to parse as Persian date format (YYYY/MM/DD)
                    val parts = visitDate.split("/")
                    if (parts.size == 3) {
                        val year = parts[0].toInt()
                        val month = parts[1].toInt()
                        val day = parts[2].toInt()
                        
                        // Convert Persian date to Gregorian date
                        val persianDate = PersianDate()
                        persianDate.setShYear(year)
                        persianDate.setShMonth(month)
                        persianDate.setShDay(day)
                        
                        selectedDate = persianDate.toDate()
                        
                        // Format the Persian date properly for display
                        val formattedPersianDate = formatPersianDate(year, month, day)
                        dateEditText.text = formattedPersianDate
                        
                        Log.d(TAG, "Successfully parsed Persian date: $formattedPersianDate")
                    } else {
                        Log.e(TAG, "Invalid date format, expected YYYY/MM/DD or YYYY-MM-DD but got: $visitDate")
                        setCurrentPersianDate(dateEditText)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing visit_date: ${e.message}")
                // Fallback to current date if parsing fails
                setCurrentPersianDate(dateEditText)
            }
        } else {
            // Use current date if no mission date was passed
            Log.d(TAG, "No mission date passed, using current date")
            setCurrentPersianDate(dateEditText)
        }
        
//        dateEditText.setOnClickListener {
//            PersianDatePickerDialog(this)
//                .setPositiveButtonString("باشه")
//                .setNegativeButton("انصراف")
//                .setTodayButton("امروز")
//                .setTodayButtonVisible(true)
//                .setMinYear(1300)
//                .setMaxYear(PersianDatePickerDialog.THIS_YEAR)
//                .setMaxMonth(PersianDatePickerDialog.THIS_MONTH)
//                .setMaxDay(PersianDatePickerDialog.THIS_DAY)
//                .setInitDate(1404, 3, 13)
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
    }
    
    private fun setCurrentPersianDate(dateEditText: TextView) {
        val currentPersianDate = PersianDate()
        selectedDate = currentPersianDate.toDate()
        
        val formattedPersianDate = formatPersianDate(
            currentPersianDate.shYear,
            currentPersianDate.shMonth,
            currentPersianDate.shDay
        )
        dateEditText.text = formattedPersianDate
        
        Log.d(TAG, "Set current Persian date: $formattedPersianDate")
    }
    
    private fun formatPersianDate(year: Int, month: Int, day: Int): String {
        // Format Persian date with proper zero-padding for single digits
        // Ensure all digits are in English format
        val yearStr = year.toString()
        val monthStr = String.format("%02d", month)
        val dayStr = String.format("%02d", day)
        
        // Convert any Persian digits to English digits
        val formattedDate = "$yearStr/$monthStr/$dayStr"
        return convertPersianDigitsToEnglish(formattedDate)
    }
    
    private fun convertPersianDigitsToEnglish(text: String): String {
        return text.replace('۰', '0')
                  .replace('۱', '1')
                  .replace('۲', '2')
                  .replace('۳', '3')
                  .replace('۴', '4')
                  .replace('۵', '5')
                  .replace('۶', '6')
                  .replace('۷', '7')
                  .replace('۸', '8')
                  .replace('۹', '9')
    }

    private fun setupSaveButton() {

        val saveButton = findViewById<Button>(R.id.save)
        saveButton.setOnClickListener {
            // جمع‌آوری اطلاعات از فیلدها
            val number = findViewById<EditText>(R.id.count).text.toString().toIntOrNull() ?: 0
            val address = addressEditText.text.toString()
            val description = findViewById<TextInputEditText>(R.id.description).text.toString()
            val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val formattedDate = selectedDate?.let { isoFormatter.format(it) }

            // Validation
            val isFromIconViolation = intent.getBooleanExtra("from_icon_violation", false)
            val isFromSubmitViolationCard = intent.getBooleanExtra("from_submit_violation_card", false)
            
            // Only validate date if the date field is visible
            if (!isFromIconViolation && !isFromSubmitViolationCard) {
                if (formattedDate == null) {
                    Toast.makeText(this, "لطفا تاریخ را انتخاب کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (number <= 0) {
                Toast.makeText(this, "لطفا تعداد را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (address.isBlank()) {
                Toast.makeText(this, "لطفا آدرس را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (description.isBlank()) {
                Toast.makeText(this, "لطفا توضیحات را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if dropdowns are selected (only validate visible fields)
            
            // Only validate organs and contracts if they are visible
            if (!isFromIconViolation && !isFromSubmitViolationCard) {
                if (organsTextView.text.isBlank()) {
                    Toast.makeText(this, "لطفا کارفرما را انتخاب کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (contractNumberTextView.text.isBlank()) {
                    Toast.makeText(this, "لطفا قرارداد را انتخاب کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            
            // Only validate violation groups and violations if they are visible
            if (!isFromIconViolation) {
                if (violationGroupTextView.text.isBlank()) {
                    Toast.makeText(this, "لطفا گروه نقص را انتخاب کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (defectTextView.text.isBlank()) {
                    Toast.makeText(this, "لطفا عنوان نقص را انتخاب کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (shouldShowSeasonAndPriceSelectors()) {
                    if (seasonOfPriceTextView.text.isBlank()) {
                        Toast.makeText(this, "لطفا گروه آیتم را انتخاب کنید", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (price_listTextView.text.isBlank()) {
                        Toast.makeText(this, "لطفا آیتم اصلی را انتخاب کنید", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
            } else if (isFromDailyPlanItem) {
                if (dailyPlanActiveViolations.isEmpty()) {
                    Toast.makeText(this, "عنوانی برای ثبت تخلف موجود نیست", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (defectTextView.text.isBlank()) {
                    Toast.makeText(this, "لطفا عنوان نقص را انتخاب کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // Check if track has GPS points before allowing submission
            if (!hasTrackPoints(currentTrackId)) {
                // Update warning visibility to show the warning
                updateGpsWarningVisibility()
                
                MaterialAlertDialogBuilder(this)
                    .setTitle("خطا در ثبت تخلف")
                    .setMessage("برای ثبت تخلف، ابتدا باید ردیابی GPS را شروع کنید. لطفاً به صفحه نقشه برگردید و ردیابی را فعال کنید.")
                    .setCancelable(true)
                    .setPositiveButton("باشه") { dialog, which -> dialog.dismiss() }
                    .show()
                return@setOnClickListener
            }

            // Log form data for debugging
            Log.d(TAG, "=== FORM SUBMISSION DATA ===")
            Log.d(TAG, "Form submission triggered from UI")
            Log.d(TAG, "Visit Date: $formattedDate")
            Log.d(TAG, "Number: $number")
            Log.d(TAG, "Address: $address")
            Log.d(TAG, "Description: $description")
            Log.d(TAG, "Images Count: ${selectedImages.size}")
            Log.d(TAG, "Selected Organ: ${organsTextView.text}")
            Log.d(TAG, "Selected Contract: ${contractNumberTextView.text}")
            Log.d(TAG, "Selected Violation Group: ${violationGroupTextView.text}")
            Log.d(TAG, "Selected Violation: ${defectTextView.text}")
            Log.d(TAG, "Selected Item Group: ${seasonOfPriceTextView.text}")
            Log.d(TAG, "Selected Origin Item: ${price_listTextView.text}")
            Log.d(TAG, "Current Track ID: $currentTrackId")
            Log.d(TAG, "GPS Coordinates: $latitude, $longitude")
            Log.d(TAG, "Image URIs: ${selectedImages.map { it.toString() }}")
            Log.d(TAG, "=============================")

            // فراخوانی متد ViewModel
            // Use mission visit date when the date field is hidden (e.g. commitments / daily plan)
            val finalFormattedDate = formattedDate
                ?: CommitmentsViolationFormFragment.formatMissionVisitDateForSubmission(missionVisitDate)
            saveButton.isEnabled = false
            formDataViewModel.submitViolation(
                finalFormattedDate,
                number,
                address,
                description,
                selectedImages,
                latitude,
                longitude,
                requireItemSelection = shouldShowSeasonAndPriceSelectors(),
            )
        }
    }

    private fun setupCalculationListener() {
        val countEditText = findViewById<EditText>(R.id.count)
        val resultEditText = findViewById<EditText>(R.id.result)

        countEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                val input = p0.toString().toDoubleOrNull() ?: 0.0
                val calculatedPrice = calculatePrice(input)
                // Format the result to show 2 decimal places
                val formattedResult = String.format("%.2f", calculatedPrice)
                resultEditText.setText(formattedResult)
            }
        })
    }
    
    /**
     * Calculate price based on the API response data and the formulas provided
     */
    private fun calculatePrice(number: Double): Double {
        // Get the current form data from ViewModel
        val currentFormData = formDataViewModel.formData.value
        if (currentFormData == null) {
            Log.d(TAG, "No form data available for calculation")
            return 0.0
        }
        
        val dto = currentFormData.dto
        
        // Parse values from API response
        val finePrice = dto.finePrice.toDoubleOrNull() ?: 0.0
        val finePercent = dto.finePercent.toDoubleOrNull() ?: 0.0
        val baseValue = dto.baseValue.toDoubleOrNull() ?: 1.0
        val daysOfMonth = dto.daysOfMonth.toDoubleOrNull() ?: 30.0
        val itemType = dto.itemType
        val costType = dto.costType
        val fixedAmount = dto.fixedAmount?.toIntOrNull() ?: 0
        val violationGroupOverheadRatio = dto.violationGroupOverheadRatio.toDoubleOrNull() ?: 1.0
        val contractFactor = dto.contractFactor.toDoubleOrNull() ?: 1.0
        
        Log.d(TAG, "=== CALCULATION PARAMETERS ===")
        Log.d(TAG, "number: $number")
        Log.d(TAG, "finePrice: $finePrice")
        Log.d(TAG, "finePercent: $finePercent")
        Log.d(TAG, "baseValue: $baseValue")
        Log.d(TAG, "daysOfMonth: $daysOfMonth")
        Log.d(TAG, "itemType: $itemType")
        Log.d(TAG, "costType: $costType")
        Log.d(TAG, "fixedAmount: $fixedAmount")
        Log.d(TAG, "violationGroupOverheadRatio: $violationGroupOverheadRatio")
        Log.d(TAG, "contractFactor: $contractFactor")
        
        // Check if billCleaningItemGroup is selected
        // In JavaScript: if ($("#<%= cmbBillCleaningItemGroup.ClientID %>").val() !== null)
        // This corresponds to itemType == "boq" (when item group is selected)
        val hasItemGroup = itemType == "boq"
        
        val baseCalculation = when {
            hasItemGroup -> {
                // If billCleaningItemGroup is selected (boq)
                when (costType) {
                    "Amount" -> finePrice * (number / baseValue) + fixedAmount
                    "DailyAmount" -> finePrice * (number / baseValue) + fixedAmount
                    "Percentage" -> (finePercent / 100) * finePrice * (number / baseValue) + fixedAmount
                    "DailyPercentage" -> (finePercent / 100) * finePrice * (number / baseValue) + fixedAmount
                    else -> 0.0
                }
            }
            else -> {
                // If billCleaningItemGroup is not selected (non-boq)
                when (costType) {
                    "Amount" -> finePercent * finePrice * (number / baseValue) + fixedAmount
                    "DailyAmount" -> (finePercent / daysOfMonth) * finePrice * (number / baseValue) + fixedAmount
                    "Percentage" -> (finePercent / 100) * finePrice * (number / baseValue) + fixedAmount
                    "DailyPercentage" -> (finePercent / 100) * finePrice * (number / baseValue) + fixedAmount
                    else -> 0.0
                }
            }
        }
        
        // Apply overhead ratios and contract factor
        val price = baseCalculation * violationGroupOverheadRatio * contractFactor
        
        Log.d(TAG, "Base calculation: $baseCalculation")
        Log.d(TAG, "Final calculated price: $price")
        return price
    }
    
    /**
     * Update unit labels based on the form data
     */
    private fun updateUnitLabels(formData: Ir.co.tfs.farazaman.data.model.FormDataResponse) {
        val dto = formData.dto
        
        // Update count unit label
        val countUnit = dto.unit
        if (countUnit.isNotEmpty()) {
            countUnitLabel.text = "($countUnit)"
            countUnitLabel.visibility = View.VISIBLE
        } else {
            countUnitLabel.visibility = View.GONE
        }
        
        // Update result unit label
        val resultUnit = dto.costTypeTitle
        if (resultUnit.isNotEmpty()) {
            resultUnitLabel.text = "($resultUnit)"
            resultUnitLabel.visibility = View.VISIBLE
        } else {
            resultUnitLabel.visibility = View.GONE
        }
        
        Log.d(TAG, "Updated unit labels - count: $countUnit, result: $resultUnit")
    }

    private fun setupImagesAdapter() {
        imagesAdapter = SelectedImagesAdapter(
            selectedImages,
            onImageClick = { uri ->
                Toast.makeText(this, "تصویر انتخاب شده", Toast.LENGTH_SHORT).show()
            },
            onImageRemove = { position ->
                selectedImages.removeAt(position)
                updateImageDisplay()
            }
        )
        imagesRecyclerView.adapter = imagesAdapter
    }
    
    private fun updateImagesAdapter() {
        if (::imagesAdapter.isInitialized) {
            imagesAdapter.notifyDataSetChanged()
        }
    }
    
    protected open fun onFormDataLoaded(formData: Ir.co.tfs.farazaman.data.model.FormDataResponse) {}

    protected open fun updateImageDisplay() {
        if (selectedImages.isNotEmpty()) {
            imagesRecyclerView.visibility = View.VISIBLE
            updateImagesAdapter()
            
            // Show count of selected images
            Toast.makeText(this, "${selectedImages.size} تصویر انتخاب شد", Toast.LENGTH_SHORT).show()
        } else {
            imagesRecyclerView.visibility = View.GONE
        }
    }

    private fun openCamera() {
        // Camera permission is already checked in checkAndRequestPermissions
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Picture")
            put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
        }
        cameraImageUri =
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        cameraImageUri?.let { takePictureLauncher.launch(it) }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // Only request camera permission for camera functionality
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        // Request write permission for older Android versions (needed to save camera images)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // This method is deprecated and not needed since we're using ActivityResultLauncher
        // The permission handling is now done in the permissionLauncher callback
    }

    private fun fetchAddressFromRoadService(lat: Double, lon: Double) {
        // Optionally show a loading indicator here
        addressEditText.setText("در حال دریافت...")
        lifecycleScope.launch {
            try {
                val buffer = GpsAccuracyHelper.resolveBuffer(this@SubmitViolationFormActivity, currentTrackId)
                val roadDataList = roadService.getRoadData(latitude = lat, longitude = lon, buffer = buffer)
                val fetchedAddress = roadDataList.firstOrNull()?.name ?: ""
                if (addressEditText.text.toString() == "در حال دریافت..." && fetchedAddress.isNotEmpty()) {
                    addressEditText.setText(fetchedAddress)
                } else if (addressEditText.text.toString() == "در حال دریافت...") {
                    addressEditText.setText("")
                }
            } catch (e: Exception) {
                if (addressEditText.text.toString() == "در حال دریافت...") {
                    addressEditText.setText("")
                }
            }
        }
    }

    /**
     * Check if the track has any GPS points
     * @param trackId The track ID to check
     * @return true if track has GPS points, false otherwise
     */
    private fun hasTrackPoints(trackId: Long): Boolean {
        if (trackId == -1L) {
            Log.d(TAG, "Track ID is -1, no track selected")
            return false
        }

        // First check if the track is active (GPS tracking is running)
        val isTrackActive = isTrackActive(trackId)
        if (!isTrackActive) {
            Log.d(TAG, "Track $trackId is not active (GPS tracking not started)")
            return false
        }

        val cursor = contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            arrayOf(TrackContentProvider.Schema.COL_ID),
            null,
            null,
            null
        )

        cursor?.use {
            val hasPoints = it.count > 0
            Log.d(TAG, "Track $trackId has ${it.count} GPS points")
            return hasPoints
        }

        Log.d(TAG, "No cursor returned for track $trackId")
        return false
    }

    /**
     * Check if the track is active (GPS tracking is running)
     * @param trackId The track ID to check
     * @return true if track is active, false otherwise
     */
    private fun isTrackActive(trackId: Long): Boolean {
        // Use a direct database query to avoid the JOIN issue
        val dbHelper = Ir.co.tfs.farazaman.data.db.DatabaseHelper(this)
        val db = dbHelper.readableDatabase
        
        val cursor = db.query(
            TrackContentProvider.Schema.TBL_TRACK,
            arrayOf(TrackContentProvider.Schema.COL_ACTIVE),
            "${TrackContentProvider.Schema.COL_ID} = ?",
            arrayOf(trackId.toString()),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val isActive = it.getInt(it.getColumnIndex(TrackContentProvider.Schema.COL_ACTIVE)) == TrackContentProvider.Schema.VAL_TRACK_ACTIVE
                Log.d(TAG, "Track $trackId active status: $isActive")
                return isActive
            }
        }

        Log.d(TAG, "No track found for ID: $trackId")
        return false
    }

    /**
     * Update GPS warning visibility based on track points
     */
    private fun updateGpsWarningVisibility() {
        if (::gpsWarningTextView.isInitialized) {
            val hasPoints = hasTrackPoints(currentTrackId)
            if (hasPoints) {
                gpsWarningTextView.visibility = View.GONE
            } else {
                gpsWarningTextView.visibility = View.VISIBLE
                val isActive = isTrackActive(currentTrackId)
                if (isActive) {
                    gpsWarningTextView.text = "⚠️ منتظر دریافت موقعیت GPS... لطفاً کمی صبر کنید"
                } else {
                    gpsWarningTextView.text = "⚠️ برای ثبت تخلف، ابتدا باید ردیابی GPS را شروع کنید"
                }
            }
        }
    }
    
    protected open fun violationTitleForHistory(): String {
        billCleaningItemName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (::defectTextView.isInitialized) {
            val defect = defectTextView.text?.toString()?.trim().orEmpty()
            if (defect.isNotEmpty() && defect != "در حال دریافت...") return defect
        }
        return getString(R.string.supervisor_violation_default_title)
    }

    protected open fun violationSubtitleForHistory(): String {
        if (::defectTextView.isInitialized) {
            val defect = defectTextView.text?.toString()?.trim().orEmpty()
            if (defect.isNotEmpty() && defect != "در حال دریافت...") {
                if (defect != violationTitleForHistory()) return defect
            }
        }
        return ""
    }

    /**
     * Hide views based on the source of the click
     */
    protected fun resetFormAfterSubmissionSuccess() {
        findViewById<EditText>(R.id.count)?.text?.clear()
        findViewById<TextInputEditText>(R.id.description)?.text?.clear()
        if (::addressEditText.isInitialized) {
            addressEditText.text?.clear()
        }
        selectedImages.clear()
        updateImageDisplay()
        val organId = intent.getIntExtra("organ_id", -1)
        val contractId = intent.getIntExtra("contract_id", -1)
        val visitDate = intent.getStringExtra("visit_date")
        if (organId != -1 && contractId != -1 && visitDate != null) {
            fetchFormDataWithPreselectedValues(organId, contractId, visitDate, intent.getStringExtra("item_data"))
        } else {
            fetchFormData()
        }
    }

    protected open fun hideViewsBasedOnSource() {
        val isFromIconViolation = intent.getBooleanExtra("from_icon_violation", false)
        val isFromSubmitViolationCard = intent.getBooleanExtra("from_submit_violation_card", false)
        
        Log.d(TAG, "=== HIDE VIEWS BASED ON SOURCE ===")
        Log.d(TAG, "isFromIconViolation: $isFromIconViolation")
        Log.d(TAG, "isFromSubmitViolationCard: $isFromSubmitViolationCard")
        
        if (isFromIconViolation) {
            // Hide all specified views for icon_violation click
            findViewById<View>(R.id.dateLay).visibility = View.GONE
            findViewById<View>(R.id.organsLay).visibility = View.GONE
            findViewById<View>(R.id.contractsLay).visibility = View.GONE
            findViewById<View>(R.id.billCleaningViolationGroupsLay).visibility = View.GONE
            findViewById<View>(R.id.billCleaningViolationsLay).visibility = View.GONE
            findViewById<View>(R.id.billCleaningItemGroupsLay).visibility = View.GONE
            findViewById<View>(R.id.price_list_lay_lay).visibility = View.GONE
            
            Log.d(TAG, "Hidden all specified views for icon_violation click")
        } else if (isFromSubmitViolationCard) {
            // Hide only specific views for submit_violation_card click
            findViewById<View>(R.id.dateLay).visibility = View.GONE
            findViewById<View>(R.id.organsLay).visibility = View.GONE
            findViewById<View>(R.id.contractsLay).visibility = View.GONE
            
            Log.d(TAG, "Hidden dateLay, organsLay, contractsLay for submit_violation_card click")
        } else {
            // No hiding for regular form access
            Log.d(TAG, "No views hidden - regular form access")
        }
    }
}
