package Ir.co.tfs.farazaman.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.api.FormDataApiService
import Ir.co.tfs.farazaman.supervisor.SupervisorMissionHelper
import Ir.co.tfs.farazaman.supervisor.SupervisorMissionSetup
import Ir.co.tfs.farazaman.util.ErrorHandler
import Ir.co.tfs.farazaman.util.LoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SupervisorWorkAreaSelectionActivity : AppCompatActivity() {

    @Inject lateinit var formDataApiService: FormDataApiService

    private lateinit var organSelector: View
    private lateinit var contractSelector: View
    private lateinit var organSelectedText: TextView
    private lateinit var contractSelectedText: TextView
    private lateinit var organProgress: ProgressBar
    private lateinit var contractProgress: ProgressBar
    private lateinit var confirmButton: MaterialButton

    private var selectedOrganId: Int? = null
    private var selectedContractId: Int? = null
    private var selectedOrganTitle: String? = null
    private var selectedContractTitle: String? = null
    private var organOptions: List<MissionSelectionOption> = emptyList()
    private var contractOptions: List<MissionSelectionOption> = emptyList()
    private var loadingDialog: LoadingDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SupervisorMissionHelper.hasExistingMission(this)) {
            navigateToHome()
            return
        }

        setContentView(R.layout.activity_supervisor_work_area_selection)
        findViewById<View>(R.id.btnProfile)?.setOnClickListener {
            SupervisorProfileActivity.open(this)
        }

        organSelector = findViewById(R.id.organSelector)
        contractSelector = findViewById(R.id.contractSelector)
        organSelectedText = findViewById(R.id.organSelectedText)
        contractSelectedText = findViewById(R.id.contractSelectedText)
        organProgress = findViewById(R.id.organProgress)
        contractProgress = findViewById(R.id.contractProgress)
        confirmButton = findViewById(R.id.btnConfirm)

        confirmButton.isEnabled = false
        setOrganLoading(true)
        setContractEnabled(false)

        organSelector.setOnClickListener { openOrganSelection() }
        contractSelector.setOnClickListener { openContractSelection() }
        confirmButton.setOnClickListener { submitSelection() }

        fetchOrgans()
    }

    private fun fetchOrgans() {
        setOrganLoading(true)
        lifecycleScope.launch {
            try {
                val options = withContext(Dispatchers.IO) {
                    SupervisorMissionSetup.loadOrgans(formDataApiService)
                }
                organOptions = options
                setOrganLoading(false)
            } catch (e: Exception) {
                setOrganLoading(false)
                organOptions = emptyList()
                if (e is SupervisorMissionSetup.MissionSetupException) {
                    Toast.makeText(this@SupervisorWorkAreaSelectionActivity, e.message, Toast.LENGTH_LONG).show()
                } else {
                    ErrorHandler.handleNetworkError(e, this@SupervisorWorkAreaSelectionActivity, "خطا در دریافت لیست کارفرما")
                }
            }
        }
    }

    private fun fetchContracts(organId: Int) {
        setContractEnabled(true)
        setContractLoading(true)
        lifecycleScope.launch {
            try {
                val options = withContext(Dispatchers.IO) {
                    SupervisorMissionSetup.loadContracts(formDataApiService, organId)
                }
                contractOptions = options
                setContractLoading(false)
                setContractEnabled(true)
                if (options.isEmpty()) {
                    Toast.makeText(
                        this@SupervisorWorkAreaSelectionActivity,
                        R.string.mission_contracts_empty,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                setContractLoading(false)
                contractOptions = emptyList()
                if (e is SupervisorMissionSetup.MissionSetupException) {
                    Toast.makeText(this@SupervisorWorkAreaSelectionActivity, e.message, Toast.LENGTH_LONG).show()
                } else {
                    ErrorHandler.handleNetworkError(e, this@SupervisorWorkAreaSelectionActivity, "خطا در دریافت لیست قراردادها")
                }
            }
        }
    }

    private fun submitSelection() {
        val organId = selectedOrganId ?: return
        val contractId = selectedContractId ?: return
        val organTitle = selectedOrganTitle ?: return
        val contractTitle = selectedContractTitle ?: return

        confirmButton.isEnabled = false
        confirmButton.text = getString(R.string.mission_submitting)
        loadingDialog = LoadingDialog.showWithTimeout(this, "در حال ثبت برنامه کاری...", 180_000)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupervisorMissionSetup.createSupervisorMission(
                        context = this@SupervisorWorkAreaSelectionActivity,
                        service = formDataApiService,
                        organId = organId,
                        contractId = contractId,
                        organTitle = organTitle,
                        contractTitle = contractTitle,
                    )
                }
                loadingDialog?.dismiss()
                loadingDialog = null
                Toast.makeText(
                    this@SupervisorWorkAreaSelectionActivity,
                    "برنامه کاری با موفقیت ثبت شد",
                    Toast.LENGTH_LONG,
                ).show()
                navigateToHome()
            } catch (e: Exception) {
                loadingDialog?.dismiss()
                loadingDialog = null
                confirmButton.isEnabled = true
                confirmButton.text = getString(R.string.work_area_confirm_continue)
                if (e is SupervisorMissionSetup.MissionSetupException) {
                    Toast.makeText(this@SupervisorWorkAreaSelectionActivity, e.message, Toast.LENGTH_LONG).show()
                } else {
                    ErrorHandler.handleNetworkError(e, this@SupervisorWorkAreaSelectionActivity, "خطا در ثبت برنامه کاری")
                }
            }
        }
    }

    private fun navigateToHome() {
        startActivity(Intent(this, NewSupervisorActivity::class.java))
        finish()
    }

    private fun openOrganSelection() {
        if (organProgress.visibility == View.VISIBLE) {
            Toast.makeText(this, R.string.mission_organs_loading, Toast.LENGTH_SHORT).show()
            return
        }
        if (organOptions.isEmpty()) {
            Toast.makeText(this, R.string.mission_organs_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        MissionItemSelectionBottomSheet.newInstance(
            getString(R.string.mission_select_organ_title),
            organOptions,
        ).apply {
            onItemSelected = { option -> onOrganSelected(option) }
        }.show(supportFragmentManager, "work_area_organ_selection")
    }

    private fun openContractSelection() {
        if (selectedOrganId == null) {
            Toast.makeText(this, R.string.mission_contracts_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (contractProgress.visibility == View.VISIBLE) return
        if (contractOptions.isEmpty()) {
            Toast.makeText(this, R.string.mission_contracts_empty, Toast.LENGTH_SHORT).show()
            return
        }
        MissionItemSelectionBottomSheet.newInstance(
            getString(R.string.mission_select_contract_title),
            contractOptions,
        ).apply {
            onItemSelected = { option -> onContractSelected(option) }
        }.show(supportFragmentManager, "work_area_contract_selection")
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
        updateConfirmState()
    }

    private fun resetContractSelection() {
        selectedContractId = null
        selectedContractTitle = null
        contractOptions = emptyList()
        contractSelectedText.text = null
        contractSelectedText.setHint(R.string.mission_select_contract_hint)
        setContractEnabled(false)
        updateConfirmState()
    }

    private fun updateConfirmState() {
        confirmButton.isEnabled = selectedOrganId != null && selectedContractId != null
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
}
