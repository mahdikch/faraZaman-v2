package Ir.co.tfs.farazaman.activity



import android.graphics.Typeface

import android.os.Bundle

import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.LinearLayout

import android.widget.TextView

import androidx.core.content.ContextCompat

import androidx.fragment.app.Fragment

import androidx.recyclerview.widget.LinearLayoutManager

import androidx.recyclerview.widget.RecyclerView

import Ir.co.tfs.farazaman.R

import Ir.co.tfs.farazaman.supervisor.bindSupervisorProfileButton

import Ir.co.tfs.farazaman.databinding.FragmentSupervisorHistoryBinding
import Ir.co.tfs.farazaman.databinding.ItemSupervisorHistoryBinding

import Ir.co.tfs.farazaman.supervisor.SupervisorViolationHistoryStore

import saman.zamani.persiandate.PersianDate

import java.text.SimpleDateFormat

import java.util.Calendar

import java.util.Date

import java.util.Locale



class SupervisorHistoryFragment : Fragment() {



    private var _binding: FragmentSupervisorHistoryBinding? = null

    private val binding get() = _binding!!



    private val dateOptions = mutableListOf<PersianDate>()

    private var selectedDate: PersianDate = PersianDate()



    override fun onCreateView(

        inflater: LayoutInflater,

        container: ViewGroup?,

        savedInstanceState: Bundle?,

    ): View {

        _binding = FragmentSupervisorHistoryBinding.inflate(inflater, container, false)

        return binding.root

    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        view.bindSupervisorProfileButton()
        selectedDate = PersianDate()

        buildDateStrip()

        loadHistory()

    }



    override fun onResume() {

        super.onResume()

        SupervisorViolationHistoryStore.syncStatusesFromMissionData(requireContext())

        loadHistory()

    }



    override fun onDestroyView() {

        _binding = null

        super.onDestroyView()

    }



    private fun buildDateStrip() {

        dateOptions.clear()

        val calendar = Calendar.getInstance()

        calendar.add(Calendar.DAY_OF_YEAR, -3)

        repeat(7) {

            dateOptions.add(PersianDate(calendar.time))

            calendar.add(Calendar.DAY_OF_YEAR, 1)

        }



        binding.dateStrip.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        dateOptions.forEach { date ->

            val chip = inflater.inflate(R.layout.item_supervisor_history_date, binding.dateStrip, false)

            chip.findViewById<TextView>(R.id.dayName).text = persianWeekday(date)

            val dayNumberView = chip.findViewById<TextView>(R.id.dayNumber)

            dayNumberView.text = date.shDay.toString()

            if (date.dayOfWeek() == 6) {

                dayNumberView.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))

            }

            chip.setOnClickListener {

                selectedDate = date

                updateDateStripSelection()

                loadHistory()

            }

            binding.dateStrip.addView(chip)

        }

        updateDateStripSelection()

    }



    private fun updateDateStripSelection() {

        for (i in 0 until binding.dateStrip.childCount) {

            val chip = binding.dateStrip.getChildAt(i)

            val date = dateOptions.getOrNull(i) ?: continue

            val selected = isSameDay(date, selectedDate)

            chip.setBackgroundResource(

                if (selected) R.drawable.supervisor_history_date_selected

                else R.drawable.supervisor_history_date_default,

            )

            chip.findViewById<TextView>(R.id.dayName).setTypeface(

                null,

                if (selected) Typeface.BOLD else Typeface.NORMAL,

            )

        }

    }



    fun refreshUi() {
        loadHistory()
    }

    private fun loadHistory() {

        val groups = loadHistoryGroups()

        binding.historyRecycler.layoutManager = LinearLayoutManager(requireContext())

        binding.historyRecycler.adapter = HistoryAdapter(groups)

        binding.historyEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE

        binding.historyRecycler.visibility = if (groups.isEmpty()) View.GONE else View.VISIBLE

    }



    private fun loadHistoryGroups(): List<HistoryGroup> {

        val records = SupervisorViolationHistoryStore.loadForPersianDay(

            requireContext(),

            selectedDate.shYear,

            selectedDate.shMonth,

            selectedDate.shDay,

        )

        return records
            .groupBy { it.title to it.subtitle }
            .map { (key, entries) ->
                HistoryGroup(
                    title = key.first,
                    subtitle = key.second,
                    entries = entries.sortedByDescending { it.timestampMs },
                )
            }
            .sortedByDescending { it.entries.firstOrNull()?.timestampMs ?: 0L }
    }



    private fun isSameDay(a: PersianDate, b: PersianDate): Boolean =

        a.shYear == b.shYear && a.shMonth == b.shMonth && a.shDay == b.shDay



    private fun persianWeekday(date: PersianDate): String {

        return when (date.dayOfWeek()) {

            0 -> "شنبه"

            1 -> "یکشنبه"

            2 -> "دوشنبه"

            3 -> "سه‌شنبه"

            4 -> "چهارشنبه"

            5 -> "پنج‌شنبه"

            else -> "جمعه"

        }

    }



    private fun formatRecordDateTime(timestampMs: Long): String {

        val pd = PersianDate(timestampMs)

        val date = "${pd.shYear}/${pd.shMonth}/${pd.shDay}"

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))

        return getString(R.string.supervisor_history_datetime, date, time)

    }



    data class HistoryGroup(
        val title: String,
        val subtitle: String,
        val entries: List<SupervisorViolationHistoryStore.Record>,
    )



    private inner class HistoryAdapter(

        private val groups: List<HistoryGroup>,

    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {



        inner class VH(val binding: ItemSupervisorHistoryBinding) : RecyclerView.ViewHolder(binding.root)



        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {

            val itemBinding = ItemSupervisorHistoryBinding.inflate(

                LayoutInflater.from(parent.context),

                parent,

                false,

            )

            return VH(itemBinding)

        }



        override fun onBindViewHolder(holder: VH, position: Int) {
            val group = groups[position]
            holder.binding.historyTitle.text = group.title
            val hasSubtitle = group.subtitle.isNotBlank()
            holder.binding.historySubtitleLabel.visibility =
                if (hasSubtitle) android.view.View.VISIBLE else android.view.View.GONE
            holder.binding.historySubtitle.visibility =
                if (hasSubtitle) android.view.View.VISIBLE else android.view.View.GONE
            holder.binding.historySubtitle.text = group.subtitle
            holder.binding.historyDateTimes.text = group.entries.joinToString("\n") {
                formatRecordDateTime(it.timestampMs)
            }
            bindStatusBadges(holder.binding.statusBadges, group.entries)
        }



        override fun getItemCount(): Int = groups.size



        private fun bindStatusBadges(

            container: LinearLayout,

            entries: List<SupervisorViolationHistoryStore.Record>,

        ) {

            container.removeAllViews()

            val counts = entries.groupingBy { it.status }.eachCount()

            val inflater = LayoutInflater.from(requireContext())

            val badgeOrder = listOf(

                SupervisorViolationHistoryStore.ViolationHistoryStatus.REGISTERED,

                SupervisorViolationHistoryStore.ViolationHistoryStatus.IN_PROGRESS,

                SupervisorViolationHistoryStore.ViolationHistoryStatus.CONFIRMED,

                SupervisorViolationHistoryStore.ViolationHistoryStatus.PENDING,

                SupervisorViolationHistoryStore.ViolationHistoryStatus.REJECTED,

            )

            badgeOrder.forEach { status ->

                val count = counts[status] ?: return@forEach

                val badge = inflater.inflate(R.layout.item_supervisor_history_badge, container, false)

                val badgeView = badge.findViewById<TextView>(R.id.statusBadge)

                badgeView.text = statusLabel(status, count)

                badgeView.setBackgroundResource(statusBackground(status))

                badgeView.setTextColor(statusTextColor(status))

                container.addView(badge)

            }

        }



        private fun statusLabel(

            status: SupervisorViolationHistoryStore.ViolationHistoryStatus,

            count: Int,

        ): String = when (status) {

            SupervisorViolationHistoryStore.ViolationHistoryStatus.REGISTERED ->

                getString(R.string.supervisor_history_registered_count, count)

            SupervisorViolationHistoryStore.ViolationHistoryStatus.IN_PROGRESS ->

                getString(R.string.supervisor_history_in_progress_count, count)

            SupervisorViolationHistoryStore.ViolationHistoryStatus.CONFIRMED ->

                getString(R.string.supervisor_history_confirmed_count, count)

            SupervisorViolationHistoryStore.ViolationHistoryStatus.PENDING ->

                getString(R.string.supervisor_history_pending_count, count)

            SupervisorViolationHistoryStore.ViolationHistoryStatus.REJECTED ->

                getString(R.string.supervisor_history_rejected_count, count)

        }



        private fun statusBackground(

            status: SupervisorViolationHistoryStore.ViolationHistoryStatus,

        ): Int = when (status) {

            SupervisorViolationHistoryStore.ViolationHistoryStatus.REGISTERED ->

                R.drawable.daily_plan_status_not_reviewed

            SupervisorViolationHistoryStore.ViolationHistoryStatus.IN_PROGRESS ->

                R.drawable.daily_plan_status_in_progress

            SupervisorViolationHistoryStore.ViolationHistoryStatus.CONFIRMED ->

                R.drawable.history_status_confirmed

            SupervisorViolationHistoryStore.ViolationHistoryStatus.PENDING ->

                R.drawable.history_status_pending

            SupervisorViolationHistoryStore.ViolationHistoryStatus.REJECTED ->

                R.drawable.history_status_rejected

        }



        private fun statusTextColor(

            status: SupervisorViolationHistoryStore.ViolationHistoryStatus,

        ): Int = when (status) {

            SupervisorViolationHistoryStore.ViolationHistoryStatus.REGISTERED ->

                requireContext().getColor(R.color.daily_plan_status_amber)

            SupervisorViolationHistoryStore.ViolationHistoryStatus.IN_PROGRESS ->

                requireContext().getColor(R.color.daily_plan_status_blue)

            SupervisorViolationHistoryStore.ViolationHistoryStatus.CONFIRMED ->

                requireContext().getColor(R.color.supervisor_mission_ring)

            SupervisorViolationHistoryStore.ViolationHistoryStatus.PENDING ->

                requireContext().getColor(R.color.text_secondary_dark)

            SupervisorViolationHistoryStore.ViolationHistoryStatus.REJECTED ->

                ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)

        }

    }

}

