package Ir.co.tfs.farazaman.activity

import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.R

@AndroidEntryPoint
class CommitmentsSubmitViolationFormActivity : NewSubmitViolationFormActivity() {

    override fun layoutId(): Int = R.layout.activity_commitments_violation_form

    override fun usesModernSelectorUi(): Boolean = true

    override fun shouldShowSummaryCard(): Boolean = false

    override fun shouldShowSeasonAndPriceSelectors(): Boolean = false

    override fun shouldFinishOnSubmissionSuccess(): Boolean = false

    override fun onFormUiReady() {
        super.onFormUiReady()
        setupModernSelectorListeners()
    }

    override fun onSubmissionSuccessCompleted() {
        resetFormAfterSubmissionSuccess()
        formDataViewModel.clearSubmissionState()
    }

    override fun violationTitleForHistory(): String {
        val groupTitle = findViewById<TextView>(R.id.billCleaningViolationGroups)
            ?.text
            ?.toString()
            ?.trim()
            .orEmpty()
        if (groupTitle.isNotEmpty() && groupTitle != "در حال دریافت...") {
            return groupTitle
        }
        return getString(R.string.supervisor_row_commitments)
    }

    override fun violationSubtitleForHistory(): String {
        val defectTitle = findViewById<TextView>(R.id.billCleaningViolations)
            ?.text
            ?.toString()
            ?.trim()
            .orEmpty()
        return if (defectTitle.isNotEmpty() && defectTitle != "در حال دریافت...") {
            defectTitle
        } else {
            ""
        }
    }
}
