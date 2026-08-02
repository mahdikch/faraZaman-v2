package Ir.co.tfs.farazaman.activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.databinding.ActivityNewSupervisorBinding
import Ir.co.tfs.farazaman.supervisor.SupervisorBottomNavHelper
import Ir.co.tfs.farazaman.supervisor.SupervisorMissionHelper
import Ir.co.tfs.farazaman.supervisor.SupervisorMissionHost
import Ir.co.tfs.farazaman.supervisor.SupervisorNavTab
import Ir.co.tfs.farazaman.supervisor.SupervisorVisitTab
import Ir.co.tfs.farazaman.supervisor.SupervisorTrackController
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class NewSupervisorActivity : AppCompatActivity(), SupervisorMissionHost {

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var userManager: Ir.co.tfs.farazaman.util.UserManager

    lateinit var controller: SupervisorTrackController
        private set

    private lateinit var binding: ActivityNewSupervisorBinding
    private var currentTab: SupervisorNavTab = SupervisorNavTab.HOME

    private var homeFragment: SupervisorHomeFragment? = null
    private var todayFragment: SupervisorTodayFragment? = null
    private var historyFragment: SupervisorHistoryFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SupervisorMissionHelper.hasExistingMission(this)) {
            SupervisorMissionHelper.openWorkAreaSelection(this)
            finish()
            return
        }

        binding = ActivityNewSupervisorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = SupervisorTrackController(this, okHttpClient, userManager) {
            onMissionUpdated()
        }

        if (savedInstanceState == null) {
            homeFragment = SupervisorHomeFragment()
            todayFragment = SupervisorTodayFragment()
            historyFragment = SupervisorHistoryFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.supervisorFragmentContainer, homeFragment!!, TAG_HOME)
                .add(R.id.supervisorFragmentContainer, todayFragment!!, TAG_TODAY)
                .hide(todayFragment!!)
                .add(R.id.supervisorFragmentContainer, historyFragment!!, TAG_HISTORY)
                .hide(historyFragment!!)
                .commit()
        } else {
            homeFragment = supportFragmentManager.findFragmentByTag(TAG_HOME) as? SupervisorHomeFragment
            todayFragment = supportFragmentManager.findFragmentByTag(TAG_TODAY) as? SupervisorTodayFragment
            historyFragment = supportFragmentManager.findFragmentByTag(TAG_HISTORY) as? SupervisorHistoryFragment
        }

        SupervisorBottomNavHelper.bind(
            activity = this,
            root = binding.supervisorBottomNav.root,
            initialTab = SupervisorNavTab.HOME,
            onTabSelected = { tab -> switchTab(tab) },
        )

    }

    override fun onResume() {
        super.onResume()
        controller.loadState()
        syncTrackIdToIntent()
        homeFragment?.refreshUi()
    }

    override fun onMissionUpdated() {
        controller.loadState()
        syncTrackIdToIntent()
        homeFragment?.refreshUi()
        todayFragment?.refreshContent()
        historyFragment?.refreshUi()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        controller.onRequestPermissionsResult(requestCode, grantResults)
        homeFragment?.refreshUi()
    }

    fun openViolationForm() {
        controller.loadState()
        if (controller.currentTrackId == SupervisorTrackController.TRACK_ID_NONE) {
            Toast.makeText(this, "ابتدا برنامه را دریافت کنید", Toast.LENGTH_SHORT).show()
            return
        }
        controller.openViolationForm()
    }

    fun syncTrackIdToIntent() {
        if (controller.currentTrackId != SupervisorTrackController.TRACK_ID_NONE) {
            intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, controller.currentTrackId)
        } else {
            intent.removeExtra(TrackContentProvider.Schema.COL_TRACK_ID)
        }
    }

    fun navigateToTab(tab: SupervisorNavTab) {
        switchTab(tab)
    }

    fun navigateToTodayTab(visitTab: SupervisorVisitTab) {
        pendingVisitTabIndex = visitTab.index
        if (currentTab != SupervisorNavTab.TODAY) {
            switchTab(SupervisorNavTab.TODAY)
        } else {
            SupervisorBottomNavHelper.updateSelection(binding.supervisorBottomNav.root, SupervisorNavTab.TODAY)
        }
        binding.root.post { applyPendingVisitTab() }
    }

    private fun applyPendingVisitTab() {
        val index = pendingVisitTabIndex ?: return
        if (todayFragment?.selectVisitTab(index) == true) {
            pendingVisitTabIndex = null
        }
    }

    private var pendingVisitTabIndex: Int? = null

    private fun switchTab(tab: SupervisorNavTab) {
        if (currentTab == tab) return
        currentTab = tab

        val home = homeFragment ?: return
        val today = todayFragment ?: return
        val history = historyFragment ?: return

        val transaction = supportFragmentManager.beginTransaction()
        transaction.hide(home)
        transaction.hide(today)
        transaction.hide(history)
        when (tab) {
            SupervisorNavTab.HOME -> transaction.show(home)
            SupervisorNavTab.TODAY -> {
                syncTrackIdToIntent()
                transaction.show(today)
            }
            SupervisorNavTab.HISTORY -> transaction.show(history)
        }
        transaction.commit()

        SupervisorBottomNavHelper.updateSelection(binding.supervisorBottomNav.root, tab)
        if (tab == SupervisorNavTab.TODAY) {
            binding.root.post { applyPendingVisitTab() }
        }
    }

    companion object {
        private const val TAG_HOME = "supervisor_home"
        private const val TAG_TODAY = "supervisor_today"
        private const val TAG_HISTORY = "supervisor_history"
    }
}
