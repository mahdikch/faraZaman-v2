package Ir.co.tfs.farazaman.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.core.content.ContextCompat
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.databinding.FragmentSupervisorTodayBinding
import Ir.co.tfs.farazaman.supervisor.bindSupervisorProfileButton

class SupervisorTodayFragment : Fragment() {

    private var _binding: FragmentSupervisorTodayBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSupervisorTodayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.bindSupervisorProfileButton()
        val tabTitles = listOf(
            getString(R.string.supervisor_tab_planning),
            getString(R.string.supervisor_tab_continuous),
            getString(R.string.supervisor_tab_commitments),
        )
        binding.visitPager.adapter = VisitPagerAdapter(this)
        TabLayoutMediator(binding.visitTabs, binding.visitPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
        binding.visitTabs.post { styleVisitTabs() }
        binding.visitTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = styleVisitTabs()
            override fun onTabUnselected(tab: TabLayout.Tab) = styleVisitTabs()
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        (activity as? NewSupervisorActivity)?.syncTrackIdToIntent()
        refreshContent()
    }

    fun refreshContent() {
        refreshHeaderState()
        childFragmentManager.fragments.forEach { fragment ->
            (fragment as? TodayVisitTabFragment)?.reloadContent()
        }
    }

    private fun refreshHeaderState() {
        val tracking = (activity as? NewSupervisorActivity)?.controller?.isTracking() == true
        binding.activeVisitBanner.visibility = if (tracking) View.VISIBLE else View.GONE
    }

    fun selectVisitTab(index: Int): Boolean {
        val binding = _binding ?: return false
        if (index !in 0 until binding.visitPager.adapter?.itemCount.orZero()) return false
        binding.visitPager.setCurrentItem(index, false)
        binding.visitTabs.getTabAt(index)?.select()
        styleVisitTabs()
        return true
    }

    private fun Int?.orZero(): Int = this ?: 0

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun styleVisitTabs() {
        val selectedBg = ContextCompat.getDrawable(requireContext(), R.drawable.supervisor_tab_selected)
        val unselectedBg = ContextCompat.getDrawable(requireContext(), R.drawable.supervisor_tab_unselected)
        val selectedColor = requireContext().getColor(R.color.login_label)
        val unselectedColor = requireContext().getColor(R.color.text_secondary_dark)
        val padH = resources.getDimensionPixelSize(R.dimen.supervisor_tab_padding_h)
        val padV = resources.getDimensionPixelSize(R.dimen.supervisor_tab_padding_v)
        val tabGap = resources.getDimensionPixelSize(R.dimen.supervisor_tab_spacing)
        val halfGap = tabGap / 2

        for (i in 0 until binding.visitTabs.tabCount) {
            val tab = binding.visitTabs.getTabAt(i) ?: continue
            val selected = tab.position == binding.visitTabs.selectedTabPosition
            val tabView = tab.view
            tabView.setPadding(padH, padV, padH, padV)
            tabView.background = if (selected) selectedBg else unselectedBg
            tabView.minimumWidth = 0
            (tabView.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                lp.width = 0
                lp.weight = 1f
                lp.marginStart = halfGap
                lp.marginEnd = halfGap
                tabView.layoutParams = lp
            }
            findTabTextView(tabView)?.apply {
                setTextColor(if (selected) selectedColor else unselectedColor)
                textSize = 12f
                includeFontPadding = false
                setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun findTabTextView(view: View): TextView? {
        if (view is TextView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findTabTextView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private class VisitPagerAdapter(fragment: SupervisorTodayFragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> TodayVisitTabFragment.newInstance("planning")
            1 -> TodayVisitTabFragment.newInstance("system")
            else -> TodayVisitTabFragment.newInstance("commitments")
        }
    }
}
