package Ir.co.tfs.farazaman.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.supervisor.SupervisorMissionHelper
import Ir.co.tfs.farazaman.util.RolesManager
import javax.inject.Inject

@AndroidEntryPoint
class RoleSelectionActivity : AppCompatActivity() {

    @Inject lateinit var rolesManager: RolesManager

    private enum class SelectedRole {
        SUPERVISOR,
        DRIVER,
    }

    private var selectedRole = SelectedRole.SUPERVISOR

    private lateinit var supervisorCard: MaterialCardView
    private lateinit var driverCard: MaterialCardView
    private lateinit var supervisorRadio: ImageView
    private lateinit var driverRadio: ImageView

    private val fromProfile: Boolean
        get() = intent.getBooleanExtra(EXTRA_FROM_PROFILE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        supervisorCard = findViewById(R.id.supervisorCard)
        driverCard = findViewById(R.id.driverCard)
        supervisorRadio = findViewById(R.id.supervisorRadio)
        driverRadio = findViewById(R.id.driverRadio)

        applyRoleVisibility()

        selectedRole = when (intent.getStringExtra(EXTRA_CURRENT_ROLE)) {
            ROLE_DRIVER -> SelectedRole.DRIVER
            else -> when {
                supervisorCard.visibility == View.VISIBLE -> SelectedRole.SUPERVISOR
                driverCard.visibility == View.VISIBLE -> SelectedRole.DRIVER
                else -> SelectedRole.SUPERVISOR
            }
        }

        findViewById<ImageButton>(R.id.btnBack).apply {
            visibility = if (fromProfile) View.VISIBLE else View.GONE
            setOnClickListener { finish() }
        }

        findViewById<View>(R.id.btnProfile)?.setOnClickListener {
            SupervisorProfileActivity.open(this)
        }

        supervisorCard.setOnClickListener {
            selectedRole = SelectedRole.SUPERVISOR
            updateSelectionUi()
        }

        driverCard.setOnClickListener {
            selectedRole = SelectedRole.DRIVER
            updateSelectionUi()
        }

        findViewById<MaterialButton>(R.id.btnEnter).setOnClickListener {
            navigateToSelectedRole()
        }

        updateSelectionUi()
    }

    private fun applyRoleVisibility() {
        val storedRoles = rolesManager.getUserRoles()?.roles
        val showSupervisor = rolesManager.hasSupervisorRole()
        val showDriver = rolesManager.hasDriverRole()

        when {
            storedRoles.isNullOrEmpty() -> {
                supervisorCard.visibility = View.VISIBLE
                driverCard.visibility = View.VISIBLE
            }
            showSupervisor && showDriver -> {
                supervisorCard.visibility = View.VISIBLE
                driverCard.visibility = View.VISIBLE
            }
            showSupervisor -> {
                supervisorCard.visibility = View.VISIBLE
                driverCard.visibility = View.GONE
            }
            showDriver -> {
                supervisorCard.visibility = View.GONE
                driverCard.visibility = View.VISIBLE
            }
            storedRoles.size > 1 -> {
                supervisorCard.visibility = View.VISIBLE
                driverCard.visibility = View.VISIBLE
            }
            else -> {
                supervisorCard.visibility = View.GONE
                driverCard.visibility = View.GONE
            }
        }
    }

    private fun updateSelectionUi() {
        val selectedColor = ContextCompat.getColor(this, R.color.intro_primary)
        val unselectedColor = ContextCompat.getColor(this, R.color.input_stroke)

        val supervisorSelected = selectedRole == SelectedRole.SUPERVISOR
        supervisorCard.strokeWidth = if (supervisorSelected) 2 else 1
        supervisorCard.strokeColor = if (supervisorSelected) selectedColor else unselectedColor
        supervisorRadio.setImageResource(
            if (supervisorSelected) R.drawable.role_selection_radio_checked
            else R.drawable.role_selection_radio_unchecked,
        )

        val driverSelected = selectedRole == SelectedRole.DRIVER
        driverCard.strokeWidth = if (driverSelected) 2 else 1
        driverCard.strokeColor = if (driverSelected) selectedColor else unselectedColor
        driverRadio.setImageResource(
            if (driverSelected) R.drawable.role_selection_radio_checked
            else R.drawable.role_selection_radio_unchecked,
        )
    }

    private fun navigateToSelectedRole() {
        val intent = when (selectedRole) {
            SelectedRole.SUPERVISOR -> createSupervisorIntent()
            SelectedRole.DRIVER -> Intent(this, DriverActivity::class.java)
        }
        if (fromProfile) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun createSupervisorIntent(): Intent {
        val target = if (SupervisorMissionHelper.hasExistingMission(this)) {
            Ir.co.tfs.farazaman.activity.NewSupervisorActivity::class.java
        } else {
            SupervisorWorkAreaSelectionActivity::class.java
        }
        return Intent(this, target)
    }

    companion object {
        const val EXTRA_FROM_PROFILE = "from_profile"
        const val EXTRA_CURRENT_ROLE = "current_role"
        const val ROLE_SUPERVISOR = "supervisor"
        const val ROLE_DRIVER = "driver"

        fun createIntent(
            context: Context,
            fromProfile: Boolean = false,
            currentRole: String = ROLE_SUPERVISOR,
        ): Intent = Intent(context, RoleSelectionActivity::class.java).apply {
            putExtra(EXTRA_FROM_PROFILE, fromProfile)
            putExtra(EXTRA_CURRENT_ROLE, currentRole)
        }
    }
}
