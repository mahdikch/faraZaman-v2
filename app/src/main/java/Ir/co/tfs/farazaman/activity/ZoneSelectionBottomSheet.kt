package Ir.co.tfs.farazaman.activity

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.model.VehicleZoneWorkItem
import Ir.co.tfs.farazaman.service.remote.VehicleZoneWorkService
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ZoneSelectionBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "ZoneSelectionBottomSheet"
        
        fun newInstance(onZoneSelected: (VehicleZoneWorkItem) -> Unit): ZoneSelectionBottomSheet {
            val fragment = ZoneSelectionBottomSheet()
            fragment.onZoneSelectedListener = onZoneSelected
            return fragment
        }
    }

    @Inject
    lateinit var vehicleZoneWorkService: VehicleZoneWorkService

    private var onZoneSelectedListener: ((VehicleZoneWorkItem) -> Unit)? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_zone_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.zones_recycler_view)
        loadingProgress = view.findViewById(R.id.loading_progress)
        emptyView = view.findViewById(R.id.empty_view)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Fetch zones
        fetchVehicleZones()
    }

    private fun fetchVehicleZones() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Fetching vehicle zones...")
                val response = vehicleZoneWorkService.getVehicleZoneWork()

                if (response.isSuccessful) {
                    val data = response.body()
                    Log.d(TAG, "Response successful: ${data != null}")

                    if (data != null) {
                        val zones = data.assetVehicleZoneWorkUseCase.data
                        Log.d(TAG, "Zones count: ${zones.size}")

                        if (zones.isNotEmpty()) {
                            showZones(zones)
                        } else {
                            showEmpty()
                        }
                    } else {
                        showEmpty()
                    }
                } else {
                    Log.e(TAG, "Response error: ${response.code()} - ${response.message()}")
                    Toast.makeText(
                        requireContext(),
                        "خطا در دریافت محدوده‌ها: ${response.message()}",
                        Toast.LENGTH_LONG
                    ).show()
                    showEmpty()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching zones", e)
                Toast.makeText(
                    requireContext(),
                    "خطا در دریافت محدوده‌ها: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                showEmpty()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showZones(zones: List<VehicleZoneWorkItem>) {
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        val adapter = ZoneWorkAdapter(zones) { selectedZone ->
            Log.d(TAG, "Zone selected: ${selectedZone.zoneWorkVehicleID}")
            onZoneSelectedListener?.invoke(selectedZone)
            dismiss()
        }
        recyclerView.adapter = adapter
    }

    private fun showEmpty() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
    }

    private fun showLoading(isLoading: Boolean) {
        loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isLoading) View.GONE else recyclerView.visibility
        emptyView.visibility = if (isLoading) View.GONE else emptyView.visibility
    }
}

