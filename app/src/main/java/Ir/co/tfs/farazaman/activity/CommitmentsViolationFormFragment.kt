package Ir.co.tfs.farazaman.activity

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.data.model.DropdownItem
import Ir.co.tfs.farazaman.data.model.FormDataRequest
import Ir.co.tfs.farazaman.data.model.FormDataResponse
import Ir.co.tfs.farazaman.presentation.viewmodel.FormDataViewModel
import Ir.co.tfs.farazaman.presentation.viewmodel.FormDataViewModel.SubmissionState
import Ir.co.tfs.farazaman.supervisor.SupervisorTrackController
import Ir.co.tfs.farazaman.supervisor.SupervisorViolationHistoryStore
import Ir.co.tfs.farazaman.util.GpsAccuracyHelper
import Ir.co.tfs.farazaman.util.LoadingDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class CommitmentsViolationFormFragment : Fragment() {

    private lateinit var formDataViewModel: FormDataViewModel

    @Inject lateinit var roadService: Ir.co.tfs.farazaman.service.remote.RoadService

    private lateinit var organsTextView: AutoCompleteTextView
    private lateinit var contractNumberTextView: AutoCompleteTextView
    private lateinit var violationGroupTextView: AutoCompleteTextView
    private lateinit var defectTextView: AutoCompleteTextView
    private lateinit var seasonOfPriceTextView: AutoCompleteTextView
    private lateinit var priceListTextView: AutoCompleteTextView
    private lateinit var addressEditText: TextInputEditText
    private lateinit var gpsWarningTextView: TextView
    private lateinit var btnPickImage: Button
    private lateinit var imagesPanel: View
    private lateinit var imagesPreviewGrid: RecyclerView
    private lateinit var txtImagesCount: TextView
    private lateinit var txtImageFileName: TextView

    private var violationGroupSelector: SelectorFieldViews? = null
    private var defectSelector: SelectorFieldViews? = null

    private var currentTrackId: Long = SupervisorTrackController.TRACK_ID_NONE
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var organTitle: String? = null
    private var contractTitle: String? = null
    private var visitDate: String? = null
    private var organId: Int = 0
    private var contractId: Int = 0

    private val selectedImages = mutableListOf<Uri>()
    private var cameraImageUri: Uri? = null
    private var loadingDialog: LoadingDialog? = null
    private var gridAdapter: SelectedImagesAdapter? = null
    private var calculationListenerAttached = false
    private var initialFormDataApplied = false

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraImageUri != null) {
                selectedImages.add(cameraImageUri!!)
                updateImageDisplay()
            } else {
                Toast.makeText(requireContext(), "خطا در گرفتن تصویر", Toast.LENGTH_SHORT).show()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.CAMERA] == true) {
                openCamera()
            } else {
                Toast.makeText(requireContext(), "برای استفاده از دوربین، به دسترسی نیاز است.", Toast.LENGTH_SHORT).show()
            }
        }

    private data class SelectorFieldViews(
        val container: View,
        val text: TextView,
        val progress: ProgressBar,
        val hintRes: Int,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_commitments_violation_form, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        formDataViewModel = ViewModelProvider(this)[FormDataViewModel::class.java]
        bindViews(view)
        setupObservers()
        setupSaveButton(view)
        setupImagePicker()
        setupModernSelectorListeners()
        view.findViewById<MaterialButton>(R.id.btnShowAllImages).setOnClickListener { showAllImagesBottomSheet() }
        reloadForm()
    }

    override fun onResume() {
        super.onResume()
        updateGpsWarningVisibility()
    }

    fun reloadForm() {
        if (!isAdded || view == null) return
        initialFormDataApplied = false
        currentTrackId = requireActivity().intent.getLongExtra(
            TrackContentProvider.Schema.COL_TRACK_ID,
            SupervisorTrackController.TRACK_ID_NONE,
        )
        if (currentTrackId == SupervisorTrackController.TRACK_ID_NONE) return

        val lastLocation = getLastLocationOfTrack(currentTrackId)
        latitude = lastLocation?.latitude ?: 0.0
        longitude = lastLocation?.longitude ?: 0.0

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val missionJson = prefs.getString("mission_data_$currentTrackId", null)
        if (missionJson != null) {
            try {
                val mission = org.json.JSONObject(missionJson)
                organId = mission.optInt("organId", 0)
                contractId = mission.optInt("contractId", 0)
                organTitle = mission.optString("organTitle", "")
                contractTitle = mission.optString("contractTitle", "")
                visitDate = mission.optString("visitDate", "")
            } catch (_: Exception) {
            }
        }

        setLoadingState()
        if (organId > 0 && contractId > 0 && !visitDate.isNullOrEmpty()) {
            val request = FormDataRequest(
                contractIds = listOf(contractId),
                organIds = listOf(organId),
                billCleaningViolationGroupIds = emptyList(),
                billCleaningViolationIds = emptyList(),
                billCleaningItemGroupIds = emptyList(),
                billCleaningItemIds = emptyList(),
                visitDate = visitDate!!,
                maxVisitDate = "",
                minVisitDate = "",
                isDeleted = false,
                tenantId = 1,
                billOriginCleaningItemIds = emptyList(),
                billCleaningViolationId = 0,
                contractId = contractId,
                organId = organId,
            )
            formDataViewModel.fetchFormDataWithRequest(request)
        } else {
            formDataViewModel.fetchFormData()
        }

        if (addressEditText.text.isNullOrBlank()) {
            if (latitude != 0.0 && longitude != 0.0) {
                fetchAddressFromRoadService(latitude, longitude)
            }
        }
    }

    private fun bindViews(view: View) {
        organsTextView = view.findViewById(R.id.organs)
        contractNumberTextView = view.findViewById(R.id.contracts)
        violationGroupTextView = view.findViewById(R.id.billCleaningViolationGroups)
        defectTextView = view.findViewById(R.id.billCleaningViolations)
        seasonOfPriceTextView = view.findViewById(R.id.billCleaningItemGroups)
        priceListTextView = view.findViewById(R.id.billOriginCleaningItems)
        addressEditText = view.findViewById(R.id.adress)
        gpsWarningTextView = view.findViewById(R.id.gps_warning)
        btnPickImage = view.findViewById(R.id.image_picker)
        imagesPanel = view.findViewById(R.id.imagesPanel)
        imagesPreviewGrid = view.findViewById(R.id.images_preview_grid)
        txtImagesCount = view.findViewById(R.id.txtImagesCount)
        txtImageFileName = view.findViewById(R.id.txtImageFileName)
        imagesPreviewGrid.layoutManager = GridLayoutManager(requireContext(), 3)
        view.findViewById<View>(R.id.hiddenFormFields)?.visibility = View.GONE

        violationGroupSelector = bindSelectorBlock(
            view,
            R.id.violationGroupSelectorBlock,
            R.string.violation_select_group_hint,
            R.string.violation_group_label,
        )
        defectSelector = bindSelectorBlock(
            view,
            R.id.defectSelectorBlock,
            R.string.violation_select_defect_hint,
            R.string.violation_defect_title_label,
        )
    }

    private fun bindSelectorBlock(
        root: View,
        blockId: Int,
        hintRes: Int,
        labelRes: Int,
    ): SelectorFieldViews? {
        val block = root.findViewById<View>(blockId) ?: return null
        block.findViewById<TextView>(R.id.selectorLabel).setText(labelRes)
        return SelectorFieldViews(
            container = block.findViewById(R.id.selectorContainer),
            text = block.findViewById(R.id.selectorText),
            progress = block.findViewById(R.id.selectorProgress),
            hintRes = hintRes,
        )
    }

    private fun setupModernSelectorListeners() {
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
                syncModernSelectorDisplays()
            }
        }
    }

    private fun openDropdownSelector(
        title: String,
        items: List<DropdownItem>,
        onSelected: (DropdownItem) -> Unit,
    ) {
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), R.string.violation_options_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        MissionItemSelectionBottomSheet.newInstance(
            title,
            items.map(MissionSelectionOption::fromDropdown),
        ).apply {
            onItemSelected = { option ->
                items.find { it.value == option.id }?.let(onSelected)
            }
        }.show(parentFragmentManager, "commitments_violation_item_selection")
    }

    private fun setupObservers() {
        formDataViewModel.formData.observe(viewLifecycleOwner) { formData ->
            populateFormData(formData)
            syncModernSelectorDisplays()
        }
        formDataViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                loadingDialog?.dismiss()
                loadingDialog = LoadingDialog.showWithTimeout(
                    requireContext(),
                    "در حال بارگذاری اطلاعات...",
                    180000,
                ) { formDataViewModel.cancelFormDataLoading() }
            } else if (formDataViewModel.submissionState.value !is SubmissionState.Loading) {
                loadingDialog?.dismiss()
                loadingDialog = null
            }
        }
        formDataViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                if (defectTextView.text?.toString() == "در حال دریافت...") {
                    defectTextView.setText("", false)
                }
                syncModernSelectorDisplays()
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                formDataViewModel.clearError()
            }
        }
        formDataViewModel.submissionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                null -> {
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    view?.findViewById<Button>(R.id.save)?.isEnabled = true
                }
                is SubmissionState.Loading -> {
                    loadingDialog?.dismiss()
                    loadingDialog = LoadingDialog.showWithTimeout(
                        requireContext(),
                        "در حال ارسال...",
                        180000,
                    ) { formDataViewModel.cancelSubmission() }
                    view?.findViewById<Button>(R.id.save)?.isEnabled = false
                }
                is SubmissionState.Success -> {
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    view?.findViewById<Button>(R.id.save)?.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    if (currentTrackId != SupervisorTrackController.TRACK_ID_NONE) {
                        SupervisorViolationHistoryStore.add(
                            context = requireContext(),
                            title = historyTitleFromViolationGroup(),
                            subtitle = historySubtitleFromDefect(),
                            trackId = currentTrackId,
                            violationId = state.violationId,
                        )
                    }
                    if (currentTrackId != SupervisorTrackController.TRACK_ID_NONE && latitude != 0.0) {
                        formDataViewModel.saveViolationAsWaypoint(currentTrackId, latitude, longitude, state.violationId)
                    }
                    resetFormAfterSuccess()
                    formDataViewModel.clearSubmissionState()
                    (parentFragment as? SupervisorTodayFragment)?.refreshContent()
                    (requireActivity() as? NewSupervisorActivity)?.onMissionUpdated()
                }
                is SubmissionState.Error -> {
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    view?.findViewById<Button>(R.id.save)?.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    formDataViewModel.clearSubmissionState()
                }
                null -> Unit
            }
        }
    }

    private fun populateFormData(formData: FormDataResponse) {
        organsTextView.setText(organTitle ?: "", false)
        contractNumberTextView.setText(contractTitle ?: "", false)
        formDataViewModel.onOrganSelected(organId)
        formDataViewModel.onContractSelected(contractId)

        val violationGroups = formData.response.billCleaningViolationGroups
        val currentGroupText = violationGroupTextView.text?.toString()?.trim().orEmpty()
        if (violationGroups.isNotEmpty()) {
            val selectedGroup = when {
                currentGroupText.isNotEmpty() &&
                    currentGroupText != "در حال دریافت..." &&
                    violationGroups.any { it.text == currentGroupText } ->
                    violationGroups.first { it.text == currentGroupText }
                !initialFormDataApplied -> violationGroups[0]
                else -> null
            }
            selectedGroup?.let { group ->
                violationGroupTextView.setText(group.text, false)
                formDataViewModel.onViolationGroupIdSelected(group.value)
            }
        }

        val violations = formData.response.billCleaningViolations
        if (violations.isNotEmpty()) {
            val currentViolationText = defectTextView.text?.toString()?.trim().orEmpty()
            val selectedViolation = when {
                currentViolationText.isNotEmpty() &&
                    currentViolationText != "در حال دریافت..." &&
                    violations.any { it.text == currentViolationText } ->
                    violations.first { it.text == currentViolationText }
                else -> violations[0]
            }
            defectTextView.setText(selectedViolation.text, false)
            formDataViewModel.onViolationIdSelected(selectedViolation.value)
        } else if (defectTextView.text?.toString() == "در حال دریافت...") {
            defectTextView.setText("", false)
        }

        if (!initialFormDataApplied) {
            initialFormDataApplied = true

            val itemGroups = formData.response.billCleaningItemGroups
            if (itemGroups.isNotEmpty()) {
                seasonOfPriceTextView.setText(itemGroups[0].text, false)
                formDataViewModel.onItemGroupIdSelected(itemGroups[0].value)
            }

            val originItems = formData.response.billOriginCleaningItems
            if (originItems.isNotEmpty()) {
                priceListTextView.setText(originItems[0].text, false)
                formDataViewModel.onOriginItemIdSelected(originItems[0].value)
            }

            setupCalculationListener()
        }

        syncModernSelectorDisplays()
    }

    private fun setupSaveButton(view: View) {
        view.findViewById<Button>(R.id.save).setOnClickListener {
            val number = view.findViewById<EditText>(R.id.count).text.toString().toIntOrNull() ?: 0
            val address = addressEditText.text.toString()
            val description = view.findViewById<TextInputEditText>(R.id.description).text.toString()

            if (number <= 0) {
                Toast.makeText(requireContext(), "لطفا تعداد را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (address.isBlank()) {
                Toast.makeText(requireContext(), "لطفا آدرس را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (description.isBlank()) {
                Toast.makeText(requireContext(), "لطفا توضیحات را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (violationGroupTextView.text.isBlank()) {
                Toast.makeText(requireContext(), "لطفا گروه نقص را انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (defectTextView.text.isBlank()) {
                Toast.makeText(requireContext(), "لطفا عنوان نقص را انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!hasTrackPoints(currentTrackId)) {
                updateGpsWarningVisibility()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("خطا در ثبت تخلف")
                    .setMessage("برای ثبت تخلف، ابتدا باید ردیابی GPS را شروع کنید. لطفاً به صفحه نقشه برگردید و ردیابی را فعال کنید.")
                    .setPositiveButton("باشه", null)
                    .show()
                return@setOnClickListener
            }

            val formattedDate = formatMissionVisitDateForSubmission(visitDate)

            view.findViewById<Button>(R.id.save).isEnabled = false
            formDataViewModel.submitViolation(
                formattedDate,
                number,
                address,
                description,
                selectedImages,
                latitude,
                longitude,
                requireItemSelection = false,
            )
        }
    }

    private fun setupCalculationListener() {
        if (calculationListenerAttached) return
        val countEditText = view?.findViewById<EditText>(R.id.count) ?: return
        val resultEditText = view?.findViewById<EditText>(R.id.result) ?: return
        calculationListenerAttached = true
        countEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toDoubleOrNull() ?: 0.0
                val calculatedPrice = calculatePrice(input)
                resultEditText.setText(String.format("%.2f", calculatedPrice))
            }
        })
    }

    private fun calculatePrice(number: Double): Double {
        val currentFormData = formDataViewModel.formData.value ?: return 0.0
        val dto = currentFormData.dto
        val finePrice = dto.finePrice.toDoubleOrNull() ?: 0.0
        val finePercent = dto.finePercent.toDoubleOrNull() ?: 0.0
        val baseValue = dto.baseValue.toDoubleOrNull() ?: 1.0
        val daysOfMonth = dto.daysOfMonth.toDoubleOrNull() ?: 30.0
        val itemType = dto.itemType
        val costType = dto.costType
        val fixedAmount = dto.fixedAmount?.toIntOrNull() ?: 0
        val violationGroupOverheadRatio = dto.violationGroupOverheadRatio.toDoubleOrNull() ?: 1.0
        val contractFactor = dto.contractFactor.toDoubleOrNull() ?: 1.0
        val hasItemGroup = itemType == "boq"
        val baseCalculation = when {
            hasItemGroup -> when (costType) {
                "Amount", "DailyAmount" -> finePrice * (number / baseValue) + fixedAmount
                "Percentage", "DailyPercentage" -> (finePercent / 100) * finePrice * (number / baseValue) + fixedAmount
                else -> 0.0
            }
            else -> when (costType) {
                "Amount" -> finePercent * finePrice * (number / baseValue) + fixedAmount
                "DailyAmount" -> (finePercent / daysOfMonth) * finePrice * (number / baseValue) + fixedAmount
                "Percentage", "DailyPercentage" -> (finePercent / 100) * finePrice * (number / baseValue) + fixedAmount
                else -> 0.0
            }
        }
        return baseCalculation * violationGroupOverheadRatio * contractFactor
    }

    private fun setupImagePicker() {
        btnPickImage.setOnClickListener { checkAndRequestPermissions() }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Picture")
            put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
        }
        cameraImageUri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        cameraImageUri?.let { takePictureLauncher.launch(it) }
    }

    private fun updateImageDisplay() {
        if (selectedImages.isEmpty()) {
            imagesPanel.visibility = View.GONE
            txtImageFileName.text = ""
            gridAdapter = null
            imagesPreviewGrid.adapter = null
            return
        }
        imagesPanel.visibility = View.VISIBLE
        txtImagesCount.text = getString(R.string.violation_images_count, selectedImages.size)
        txtImageFileName.text = selectedImages.lastOrNull()?.lastPathSegment ?: "image"
        gridAdapter = SelectedImagesAdapter(
            selectedImages.take(3),
            onImageClick = { },
            onImageRemove = { position ->
                selectedImages.removeAt(position)
                updateImageDisplay()
            },
        )
        imagesPreviewGrid.adapter = gridAdapter
        view?.findViewById<MaterialButton>(R.id.btnShowAllImages)?.visibility =
            if (selectedImages.size > 3) View.VISIBLE else View.GONE
    }

    private fun showAllImagesBottomSheet() {
        if (selectedImages.isEmpty()) return
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_violation_all_images, null)
        sheetView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        sheet.setContentView(sheetView)
        sheetView.findViewById<TextView>(R.id.txtAllImagesCount).text =
            getString(R.string.violation_images_count, selectedImages.size)
        val grid = sheetView.findViewById<RecyclerView>(R.id.allImagesGrid)
        grid.layoutManager = GridLayoutManager(requireContext(), 3)
        grid.adapter = SelectedImagesAdapter(
            selectedImages,
            onImageClick = { },
            onImageRemove = { position ->
                selectedImages.removeAt(position)
                updateImageDisplay()
                sheetView.findViewById<TextView>(R.id.txtAllImagesCount).text =
                    getString(R.string.violation_images_count, selectedImages.size)
                if (selectedImages.isEmpty()) sheet.dismiss()
            },
        )
        sheetView.findViewById<MaterialButton>(R.id.btnDeleteAllImages).setOnClickListener {
            selectedImages.clear()
            updateImageDisplay()
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun resetFormAfterSuccess() {
        view?.findViewById<EditText>(R.id.count)?.text?.clear()
        view?.findViewById<TextInputEditText>(R.id.description)?.text?.clear()
        addressEditText.text?.clear()
        selectedImages.clear()
        updateImageDisplay()
        reloadForm()
    }

    private fun setLoadingState() {
        violationGroupTextView.setText("در حال دریافت...", false)
        defectTextView.setText("در حال دریافت...", false)
        seasonOfPriceTextView.setText("در حال دریافت...", false)
        priceListTextView.setText("در حال دریافت...", false)
        syncModernSelectorDisplays()
    }

    private fun syncModernSelectorDisplays() {
        violationGroupSelector?.let { syncOneSelector(it, violationGroupTextView) }
        defectSelector?.let { syncOneSelector(it, defectTextView) }
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

    private fun fetchAddressFromRoadService(lat: Double, lon: Double) {
        addressEditText.setText("در حال دریافت...")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val buffer = GpsAccuracyHelper.resolveBuffer(requireContext(), currentTrackId)
                val roadDataList = roadService.getRoadData(latitude = lat, longitude = lon, buffer = buffer)
                val fetchedAddress = roadDataList.firstOrNull()?.name ?: ""
                if (addressEditText.text.toString() == "در حال دریافت..." && fetchedAddress.isNotEmpty()) {
                    addressEditText.setText(fetchedAddress)
                } else if (addressEditText.text.toString() == "در حال دریافت...") {
                    addressEditText.setText("")
                }
            } catch (_: Exception) {
                if (addressEditText.text.toString() == "در حال دریافت...") {
                    addressEditText.setText("")
                }
            }
        }
    }

    private fun hasTrackPoints(trackId: Long): Boolean {
        if (trackId == SupervisorTrackController.TRACK_ID_NONE) return false
        if (!isTrackActive(trackId)) return false
        val cursor = requireContext().contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            arrayOf(TrackContentProvider.Schema.COL_ID),
            null,
            null,
            null,
        )
        cursor?.use { return it.count > 0 }
        return false
    }

    private fun isTrackActive(trackId: Long): Boolean {
        val dbHelper = Ir.co.tfs.farazaman.data.db.DatabaseHelper(requireContext())
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TrackContentProvider.Schema.TBL_TRACK,
            arrayOf(TrackContentProvider.Schema.COL_ACTIVE),
            "${TrackContentProvider.Schema.COL_ID} = ?",
            arrayOf(trackId.toString()),
            null,
            null,
            null,
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getInt(it.getColumnIndex(TrackContentProvider.Schema.COL_ACTIVE)) ==
                    TrackContentProvider.Schema.VAL_TRACK_ACTIVE
            }
        }
        return false
    }

    private fun getLastLocationOfTrack(trackId: Long): android.location.Location? {
        val cursor = requireContext().contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            arrayOf(
                TrackContentProvider.Schema.COL_LATITUDE,
                TrackContentProvider.Schema.COL_LONGITUDE,
                TrackContentProvider.Schema.COL_TIMESTAMP,
            ),
            null,
            null,
            "${TrackContentProvider.Schema.COL_TIMESTAMP} DESC",
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val latIdx = it.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)
                val lonIdx = it.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE)
                if (latIdx >= 0 && lonIdx >= 0) {
                    return android.location.Location("track_last_location").apply {
                        latitude = it.getDouble(latIdx)
                        longitude = it.getDouble(lonIdx)
                    }
                }
            }
        }
        return null
    }

    private fun updateGpsWarningVisibility() {
        if (!::gpsWarningTextView.isInitialized) return
        val hasPoints = hasTrackPoints(currentTrackId)
        if (hasPoints) {
            gpsWarningTextView.visibility = View.GONE
        } else {
            gpsWarningTextView.visibility = View.VISIBLE
            gpsWarningTextView.text = if (isTrackActive(currentTrackId)) {
                "⚠️ منتظر دریافت موقعیت GPS... لطفاً کمی صبر کنید"
            } else {
                "⚠️ برای ثبت تخلف، ابتدا باید ردیابی GPS را شروع کنید"
            }
        }
    }

    private fun historyTitleFromViolationGroup(): String {
        val groupTitle = violationGroupTextView.text?.toString()?.trim().orEmpty()
        if (groupTitle.isNotEmpty() && groupTitle != "در حال دریافت...") {
            return groupTitle
        }
        return getString(R.string.supervisor_row_commitments)
    }

    private fun historySubtitleFromDefect(): String {
        val defectTitle = defectTextView.text?.toString()?.trim().orEmpty()
        return if (defectTitle.isNotEmpty() && defectTitle != "در حال دریافت...") {
            defectTitle
        } else {
            ""
        }
    }

    companion object {
        fun newInstance(): CommitmentsViolationFormFragment = CommitmentsViolationFormFragment()

        fun formatMissionVisitDateForSubmission(missionVisitDate: String?): String {
            val trimmed = missionVisitDate?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
            }
            if (trimmed.contains("T")) {
                return trimmed
            }
            if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                return "${trimmed}T00:00:00"
            }
            return trimmed
        }
    }
}
