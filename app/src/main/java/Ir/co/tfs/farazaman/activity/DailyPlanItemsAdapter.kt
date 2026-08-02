package Ir.co.tfs.farazaman.activity

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.supervisor.DailyPlanItem

class DailyPlanItemsAdapter(
    private val items: List<DailyPlanItem>,
    private val onMapClick: (Int) -> Unit,
    private val onViolationClick: (Int) -> Unit,
) : RecyclerView.Adapter<DailyPlanItemsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val violationBadge: TextView = view.findViewById(R.id.violationBadge)
        val taskTitle: TextView = view.findViewById(R.id.taskTitle)
        val taskSubtitle: TextView = view.findViewById(R.id.taskSubtitle)
        val btnShowOnMap: TextView = view.findViewById(R.id.btnShowOnMap)
        val btnRegisterViolation: TextView = view.findViewById(R.id.btnRegisterViolation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_plan_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.taskTitle.text = item.title
        holder.taskSubtitle.text = item.subtitle
        holder.taskSubtitle.visibility =
            if (item.subtitle.isBlank()) View.GONE else View.VISIBLE

        if (item.violationCount > 0) {
            holder.violationBadge.text =
                ctx.getString(R.string.daily_plan_violation_count, item.violationCount)
            holder.violationBadge.setBackgroundResource(R.drawable.daily_plan_violation_count_bg)
            holder.violationBadge.setTextColor(
                ContextCompat.getColor(ctx, R.color.daily_plan_status_amber),
            )
        } else {
            holder.violationBadge.text = ctx.getString(R.string.daily_plan_no_violation)
            holder.violationBadge.setBackgroundResource(R.drawable.daily_plan_no_violation_bg)
            holder.violationBadge.setTextColor(
                ContextCompat.getColor(ctx, R.color.text_secondary_dark),
            )
        }

        holder.btnShowOnMap.setOnClickListener { onMapClick(position) }
        holder.btnRegisterViolation.isEnabled = item.canRegisterViolation
        holder.btnRegisterViolation.isClickable = item.canRegisterViolation
        holder.btnRegisterViolation.alpha = if (item.canRegisterViolation) 1f else 0.4f
        holder.btnRegisterViolation.setOnClickListener {
            if (item.canRegisterViolation) onViolationClick(position)
        }
    }

    override fun getItemCount(): Int = items.size
}
