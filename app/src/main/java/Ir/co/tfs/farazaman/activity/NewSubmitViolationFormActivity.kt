package Ir.co.tfs.farazaman.activity

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.R

@AndroidEntryPoint
open class NewSubmitViolationFormActivity : SubmitViolationFormActivity() {

    private lateinit var imagesPreviewGrid: RecyclerView
    private lateinit var imagesPanel: View
    private lateinit var txtImagesCount: TextView
    private lateinit var txtImageFileName: TextView
    private var gridAdapter: SelectedImagesAdapter? = null

    override fun layoutId(): Int = R.layout.activity_new_submit_violation_form

    override fun usesModernSelectorUi(): Boolean = isFromDailyPlanItem

    override fun shouldShowSeasonAndPriceSelectors(): Boolean = false

    protected open fun shouldShowSummaryCard(): Boolean = true

    override fun onFormUiReady() {
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        findViewById<View>(R.id.formScroll)?.layoutDirection = View.LAYOUT_DIRECTION_RTL

        imagesPreviewGrid = findViewById(R.id.images_preview_grid)
        imagesPreviewGrid.layoutDirection = View.LAYOUT_DIRECTION_RTL
        imagesPanel = findViewById(R.id.imagesPanel)
        txtImagesCount = findViewById(R.id.txtImagesCount)
        txtImageFileName = findViewById(R.id.txtImageFileName)

        imagesPreviewGrid.layoutManager = GridLayoutManager(this, 3)
        if (shouldShowSummaryCard()) {
            bindSummaryCard()
            findViewById<View>(R.id.btnViewDetails).setOnClickListener { showDetailsBottomSheet() }
        }
        findViewById<MaterialButton>(R.id.btnShowAllImages).setOnClickListener { showAllImagesBottomSheet() }
        if (isFromDailyPlanItem) {
            bindModernSelectorViews()
            setupDailyPlanDefectSelector()
        }
    }

    override fun hideViewsBasedOnSource() {
        findViewById<View>(R.id.hiddenFormFields)?.visibility = View.GONE
    }

    override fun onFormDataLoaded(formData: Ir.co.tfs.farazaman.data.model.FormDataResponse) {
        if (shouldShowSummaryCard()) {
            bindSummaryCard()
        }
    }

    override fun updateImageDisplay() {
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
        findViewById<MaterialButton>(R.id.btnShowAllImages).visibility =
            if (selectedImages.size > 3) View.VISIBLE else View.GONE
    }

    override fun onDailyPlanDefectSelected() {
        if (shouldShowSummaryCard()) {
            bindSummaryCard()
        }
    }

    protected open fun bindSummaryCard() {
        val summaryTitle = findViewById<TextView>(R.id.summaryDefectTitle)
        val summarySubtitle = findViewById<TextView>(R.id.summaryVisitDate)
        val isFromDailyPlanItem = intent.getBooleanExtra("from_icon_violation", false) &&
            !billCleaningItemName.isNullOrBlank()

        if (isFromDailyPlanItem) {
            summaryTitle.text = billCleaningItemName
            val subtitle = resolveDailyPlanItemSubtitle()
            summarySubtitle.text = subtitle
            summarySubtitle.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            return
        }

        val defectTitle = findViewById<TextView>(R.id.billCleaningViolations).text?.toString().orEmpty()
        val visitDate = findViewById<TextView>(R.id.Date_of_performance_registration).text?.toString().orEmpty()
        summaryTitle.text = defectTitle
            .takeIf { it.isNotBlank() && it != "در حال دریافت..." }
            ?: billCleaningItemName.orEmpty()
        summarySubtitle.visibility = View.VISIBLE
        summarySubtitle.text =
            if (visitDate.isNotBlank() && visitDate != "در حال دریافت...") {
                getString(R.string.violation_visit_date_label, visitDate)
            } else {
                ""
            }
    }

    private fun showDetailsBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_violation_details, null)
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        sheet.setContentView(view)

        val defect = findViewById<TextView>(R.id.billCleaningViolations).text?.toString().orEmpty()
        view.findViewById<TextView>(R.id.detailsTitle).text =
            getString(R.string.violation_details_title, defect.ifBlank { "تخلف" })
        view.findViewById<TextView>(R.id.detailsSubtitle).text = billCleaningItemName ?: ""

        val card = view.findViewById<LinearLayout>(R.id.detailsCard)
        val rows = listOf(
            getString(R.string.label_date) to findViewById<TextView>(R.id.Date_of_performance_registration).text.toString(),
            getString(R.string.label_employer) to findViewById<TextView>(R.id.organs).text.toString(),
            getString(R.string.label_contract_number) to findViewById<TextView>(R.id.contracts).text.toString(),
            getString(R.string.violation_group_label) to findViewById<TextView>(R.id.billCleaningViolationGroups).text.toString(),
            getString(R.string.violation_defect_title_label) to defect,
            getString(R.string.violation_season_label) to findViewById<TextView>(R.id.billCleaningItemGroups).text.toString(),
            getString(R.string.violation_price_row_label) to findViewById<TextView>(R.id.billOriginCleaningItems).text.toString(),
        )
        rows.forEachIndexed { index, (label, value) ->
            addDetailRow(card, label, value, showDivider = index < rows.lastIndex)
        }
        sheet.show()
    }

    private fun addDetailRow(parent: LinearLayout, label: String, value: String, showDivider: Boolean) {
        val row = layoutInflater.inflate(R.layout.item_violation_detail_row, parent, false)
        row.findViewById<TextView>(R.id.detailLabel).text = "$label :"
        row.findViewById<TextView>(R.id.detailValue).text = value.ifBlank { "-" }
        row.findViewById<View>(R.id.detailDivider).visibility =
            if (showDivider) View.VISIBLE else View.GONE
        parent.addView(row)
    }

    private fun showAllImagesBottomSheet() {
        if (selectedImages.isEmpty()) return
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_violation_all_images, null)
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        sheet.setContentView(view)
        view.findViewById<RecyclerView>(R.id.allImagesGrid).layoutDirection = View.LAYOUT_DIRECTION_RTL
        view.findViewById<TextView>(R.id.txtAllImagesCount).text =
            getString(R.string.violation_images_count, selectedImages.size)
        val grid = view.findViewById<RecyclerView>(R.id.allImagesGrid)
        grid.layoutManager = GridLayoutManager(this, 3)
        grid.adapter = SelectedImagesAdapter(
            selectedImages,
            onImageClick = { },
            onImageRemove = { position ->
                selectedImages.removeAt(position)
                updateImageDisplay()
                view.findViewById<TextView>(R.id.txtAllImagesCount).text =
                    getString(R.string.violation_images_count, selectedImages.size)
                if (selectedImages.isEmpty()) sheet.dismiss()
            },
        )
        view.findViewById<MaterialButton>(R.id.btnDeleteAllImages).setOnClickListener {
            selectedImages.clear()
            updateImageDisplay()
            sheet.dismiss()
        }
        sheet.show()
    }
}
