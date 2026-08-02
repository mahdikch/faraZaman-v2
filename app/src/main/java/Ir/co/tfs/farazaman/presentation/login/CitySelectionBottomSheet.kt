package Ir.co.tfs.farazaman.presentation.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.auth.OidcAuthManager
import org.json.JSONArray
import org.json.JSONObject

class CitySelectionBottomSheet : BottomSheetDialogFragment() {

    var onCitySelected: ((LoginActivity.ProvinceInstance) -> Unit)? = null

    companion object {
        private const val ARG_CITIES_JSON = "cities_json"
        private const val ARG_IS_LOADING = "is_loading"

        fun newInstance(
            cities: List<LoginActivity.ProvinceInstance>,
            isLoading: Boolean,
        ): CitySelectionBottomSheet {
            val citiesJson = JSONArray().apply {
                cities.forEach { city ->
                    put(
                        JSONObject().apply {
                            put("uid", city.uid)
                            put("title", city.title)
                            put("baseAddress", city.baseAddress)
                            put("enabled", city.enabled)
                            put("clientId", city.clientId)
                            put("scope", city.scope)
                            put("loginMode", city.loginMode)
                        },
                    )
                }
            }.toString()
            return CitySelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CITIES_JSON, citiesJson)
                    putBoolean(ARG_IS_LOADING, isLoading)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.bottom_sheet_city_selection, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.citiesRecyclerView)
        val loadingView = view.findViewById<ProgressBar>(R.id.citiesLoading)
        val emptyView = view.findViewById<TextView>(R.id.citiesEmpty)

        val isLoading = arguments?.getBoolean(ARG_IS_LOADING, false) == true
        val cities = parseCities(arguments?.getString(ARG_CITIES_JSON).orEmpty())

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        when {
            isLoading -> {
                loadingView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.GONE
            }
            cities.isEmpty() -> {
                loadingView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
            else -> {
                loadingView.visibility = View.GONE
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter = CitySelectionAdapter(cities) { city ->
                    onCitySelected?.invoke(city)
                    dismiss()
                }
                applyRecyclerHeight(recyclerView, cities.size)
            }
        }
    }

    private fun applyRecyclerHeight(recyclerView: RecyclerView, itemCount: Int) {
        val itemHeight = resources.getDimensionPixelSize(R.dimen.city_selection_item_height)
        val maxListHeight = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        recyclerView.layoutParams = recyclerView.layoutParams.apply {
            height = (itemCount * itemHeight).coerceAtMost(maxListHeight)
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun parseCities(json: String): List<LoginActivity.ProvinceInstance> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        LoginActivity.ProvinceInstance(
                            uid = obj.optLong("uid"),
                            title = obj.optString("title"),
                            baseAddress = obj.optString("baseAddress"),
                            enabled = obj.optBoolean("enabled", true),
                            clientId = obj.optString("clientId", OidcAuthManager.DEFAULT_CLIENT_ID),
                            scope = obj.optString("scope", OidcAuthManager.DEFAULT_SCOPES),
                            loginMode = obj.optInt("loginMode", LoginModeHelper.MODE_WEB),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
