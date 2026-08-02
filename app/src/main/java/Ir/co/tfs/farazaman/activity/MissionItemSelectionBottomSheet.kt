package Ir.co.tfs.farazaman.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import Ir.co.tfs.farazaman.R

class MissionItemSelectionBottomSheet : BottomSheetDialogFragment() {

    private var sheetTitle: String = ""
    private var items: List<MissionSelectionOption> = emptyList()
    var onItemSelected: ((MissionSelectionOption) -> Unit)? = null

    companion object {
        fun newInstance(
            title: String,
            items: List<MissionSelectionOption>,
        ): MissionItemSelectionBottomSheet {
            return MissionItemSelectionBottomSheet().apply {
                sheetTitle = title
                this.items = items
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.bottom_sheet_mission_item_selection, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.selectionTitle).text = sheetTitle
        val recyclerView = view.findViewById<RecyclerView>(R.id.selectionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = MissionSelectionAdapter(items) { item ->
            onItemSelected?.invoke(item)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }
}
