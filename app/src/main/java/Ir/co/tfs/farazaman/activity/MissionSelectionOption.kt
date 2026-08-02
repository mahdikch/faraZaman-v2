package Ir.co.tfs.farazaman.activity

import Ir.co.tfs.farazaman.data.model.DropdownItem

data class MissionSelectionOption(
    val value: Int,
    val text: String,
) {
    val id: Int get() = value
    val title: String get() = text

    companion object {
        fun fromDropdown(item: DropdownItem) = MissionSelectionOption(
            value = item.value,
            text = item.text,
        )
    }
}
