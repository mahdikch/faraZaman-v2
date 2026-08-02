package Ir.co.tfs.farazaman.supervisor

import android.view.View
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.activity.SupervisorProfileActivity

fun View.bindSupervisorProfileButton() {
    findViewById<View?>(R.id.btnProfile)?.setOnClickListener {
        SupervisorProfileActivity.open(context)
    }
}
