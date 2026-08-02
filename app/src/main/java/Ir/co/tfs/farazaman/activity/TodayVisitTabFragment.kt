package Ir.co.tfs.farazaman.activity



import android.os.Bundle

import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.TextView

import androidx.fragment.app.Fragment

import androidx.recyclerview.widget.LinearLayoutManager

import androidx.recyclerview.widget.RecyclerView

import Ir.co.tfs.farazaman.R

import Ir.co.tfs.farazaman.data.db.TrackContentProvider

import Ir.co.tfs.farazaman.supervisor.DailyPlanItem

import Ir.co.tfs.farazaman.supervisor.DailyPlanItemViolationHelper

import Ir.co.tfs.farazaman.supervisor.SupervisorViolationHistoryStore

import Ir.co.tfs.farazaman.supervisor.SupervisorTrackController

import Ir.co.tfs.farazaman.supervisor.TodayVisitMissionActions

import org.json.JSONArray

import org.json.JSONObject



class TodayVisitTabFragment : Fragment() {



    companion object {

        private const val ARG_TAB = "tab_type"
        private const val TAG_COMMITMENTS_FORM = "commitments_violation_form"



        fun newInstance(tabType: String, initialTab: Int = 0): TodayVisitTabFragment {

            return TodayVisitTabFragment().apply {

                arguments = Bundle().apply {

                    putString(ARG_TAB, tabType)

                    putInt("initial_tab", initialTab)

                }

            }

        }

    }



    private var tabType: String = "planning"

    private var trackId: Long = SupervisorTrackController.TRACK_ID_NONE



    override fun onCreateView(

        inflater: LayoutInflater,

        container: ViewGroup?,

        savedInstanceState: Bundle?,

    ): View = inflater.inflate(R.layout.fragment_today_visit_tab, container, false)



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        tabType = arguments?.getString(ARG_TAB) ?: "planning"

        bindContent(view)

    }



    override fun onResume() {

        super.onResume()

        view?.let { bindContent(it) }

    }



    fun reloadContent() {

        view?.let { bindContent(it) }

    }



    private fun bindContent(view: View) {

        trackId = requireActivity().intent.getLongExtra(

            TrackContentProvider.Schema.COL_TRACK_ID,

            SupervisorTrackController.TRACK_ID_NONE,

        )



        val recycler = view.findViewById<RecyclerView>(R.id.itemsRecycler)

        val commitmentsFormContainer = view.findViewById<View>(R.id.commitmentsFormContainer)

        val emptyText = view.findViewById<TextView>(R.id.emptyText)



        val warningBanner = view.findViewById<View>(R.id.visitWarningBanner)
        val isListTab = tabType == "planning" || tabType == "system"
        if (isListTab) {
            val tracking = (activity as? NewSupervisorActivity)?.controller?.isTracking() == true
            warningBanner.visibility = if (tracking) View.GONE else View.VISIBLE
            warningBanner.findViewById<View>(R.id.btnStartVisit)?.setOnClickListener {
                (activity as? NewSupervisorActivity)?.controller?.startMission()
            }
            warningBanner.findViewById<View>(R.id.btnShowOnMap)?.setOnClickListener {
                (activity as? NewSupervisorActivity)?.controller?.openMissionMap()
            }
        } else {
            warningBanner.visibility = View.GONE
        }

        if (tabType == "commitments") {
            recycler.visibility = View.GONE
            emptyText.visibility = View.GONE
            commitmentsFormContainer.visibility = View.VISIBLE
            if (childFragmentManager.findFragmentByTag(TAG_COMMITMENTS_FORM) == null) {
                childFragmentManager.beginTransaction()
                    .replace(
                        R.id.commitmentsFormContainer,
                        CommitmentsViolationFormFragment.newInstance(),
                        TAG_COMMITMENTS_FORM,
                    )
                    .commit()
            } else {
                (childFragmentManager.findFragmentByTag(TAG_COMMITMENTS_FORM) as? CommitmentsViolationFormFragment)
                    ?.reloadForm()
            }
            return
        }

        commitmentsFormContainer.visibility = View.GONE

        val violationCounts = SupervisorViolationHistoryStore.countByItemTitle(requireContext(), trackId)

        val items = loadItems(tabType, violationCounts)

        if (items.isEmpty()) {

            recycler.visibility = View.GONE

            emptyText.visibility = View.VISIBLE

            return

        }

        emptyText.visibility = View.GONE

        recycler.visibility = View.VISIBLE

        recycler.layoutManager = LinearLayoutManager(requireContext())

        recycler.adapter = DailyPlanItemsAdapter(

            items = items,

            onMapClick = { pos ->

                TodayVisitMissionActions.openMap(

                    requireContext(),

                    trackId,

                    items.getOrNull(pos)?.encryption,

                )

            },

            onViolationClick = { pos ->

                val title = items.getOrNull(pos)?.title ?: return@DailyPlanItemsAdapter

                TodayVisitMissionActions.openViolation(requireContext(), trackId, tabType, title)

            },

        )

    }



    private fun loadItems(type: String, violationCounts: Map<String, Int>): List<DailyPlanItem> {

        if (trackId == SupervisorTrackController.TRACK_ID_NONE) return emptyList()

        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())

        val json = prefs.getString("mission_data_$trackId", null) ?: return emptyList()

        return try {

            val obj = JSONObject(json)

            val key = if (type == "planning") "planningItems" else "systemItems"

            val arr = obj.optJSONArray(key) ?: JSONArray()

            val result = mutableListOf<DailyPlanItem>()

            for (i in 0 until arr.length()) {

                val item = arr.getJSONObject(i)

                val title = item.optString("billCleaningItemName", "")

                if (title.isEmpty()) continue

                result.add(
                    DailyPlanItem(
                        title = title,
                        subtitle = resolveSubtitle(item),
                        encryption = item.optString("aencryption", ""),
                        violationCount = violationCounts[title] ?: 0,
                        canRegisterViolation = DailyPlanItemViolationHelper.canRegisterViolation(item),
                    ),
                )

            }

            result

        } catch (_: Exception) {

            emptyList()

        }

    }



    private fun resolveSubtitle(item: JSONObject): String {

        val groupName = item.optString("billCleaningItemGroupName", "").trim()

        if (groupName.isNotEmpty()) return groupName



        val width = item.optJSONObject("billOriginCleaningItem")

            ?.optJSONObject("billCleaningItem")

            ?.optString("widthLabel", "")

            ?.trim().orEmpty()

        if (width.isNotEmpty()) return width



        return item.optString("description", "").trim()
    }
}

