package Ir.co.tfs.farazaman.supervisor

import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import Ir.co.tfs.farazaman.R

enum class SupervisorNavTab { HOME, TODAY, HISTORY }

object SupervisorBottomNavHelper {

    fun bind(
        activity: AppCompatActivity,
        root: View,
        initialTab: SupervisorNavTab,
        onTabSelected: (SupervisorNavTab) -> Unit,
    ) {
        val navHistoryIcon = root.findViewById<ImageButton>(R.id.navHistoryIcon)
        val navHistoryPill = root.findViewById<MaterialButton>(R.id.navHistoryPill)
        val navTodayIcon = root.findViewById<ImageButton>(R.id.navTodayIcon)
        val navTodayPill = root.findViewById<MaterialButton>(R.id.navTodayPill)
        val navHomeIcon = root.findViewById<ImageButton>(R.id.navHomeIcon)
        val navHomePill = root.findViewById<MaterialButton>(R.id.navHomePill)

        var selectedTab = initialTab

        val renderTab: (SupervisorNavTab) -> Unit = { tab ->
            selectedTab = tab
            val homeActive = tab == SupervisorNavTab.HOME
            val todayActive = tab == SupervisorNavTab.TODAY
            val historyActive = tab == SupervisorNavTab.HISTORY

            navHomePill.visibility = if (homeActive) View.VISIBLE else View.GONE
            navHomeIcon.visibility = if (homeActive) View.GONE else View.VISIBLE
            navTodayPill.visibility = if (todayActive) View.VISIBLE else View.GONE
            navTodayIcon.visibility = if (todayActive) View.GONE else View.VISIBLE
            navHistoryPill.visibility = if (historyActive) View.VISIBLE else View.GONE
            navHistoryIcon.visibility = if (historyActive) View.GONE else View.VISIBLE
        }

        renderTab(initialTab)
        root.tag = renderTab

        navHomeIcon.setOnClickListener {
            if (selectedTab != SupervisorNavTab.HOME) onTabSelected(SupervisorNavTab.HOME)
        }
        navHomePill.setOnClickListener { navHomeIcon.performClick() }

        navTodayIcon.setOnClickListener {
            if (selectedTab != SupervisorNavTab.TODAY) onTabSelected(SupervisorNavTab.TODAY)
        }
        navTodayPill.setOnClickListener { navTodayIcon.performClick() }

        navHistoryIcon.setOnClickListener {
            if (selectedTab != SupervisorNavTab.HISTORY) onTabSelected(SupervisorNavTab.HISTORY)
        }
        navHistoryPill.setOnClickListener { navHistoryIcon.performClick() }
    }

    @Suppress("UNCHECKED_CAST")
    fun updateSelection(root: View, tab: SupervisorNavTab) {
        (root.tag as? (SupervisorNavTab) -> Unit)?.invoke(tab)
    }
}
