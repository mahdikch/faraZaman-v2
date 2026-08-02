package Ir.co.tfs.farazaman.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import Ir.co.tfs.farazaman.R

class EndMissionBottomSheet : BottomSheetDialogFragment() {

    var onConfirm: (() -> Unit)? = null

    companion object {
        fun newInstance(onConfirm: () -> Unit): EndMissionBottomSheet {
            return EndMissionBottomSheet().apply {
                this.onConfirm = onConfirm
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_end_mission, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnEndMissionConfirm).setOnClickListener {
            dismiss()
            onConfirm?.invoke()
        }
        view.findViewById<View>(R.id.btnEndMissionCancel).setOnClickListener {
            dismiss()
        }
    }
}
