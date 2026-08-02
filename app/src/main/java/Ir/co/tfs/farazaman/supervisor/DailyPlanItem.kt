package Ir.co.tfs.farazaman.supervisor

data class DailyPlanItem(
    val title: String,
    val subtitle: String,
    val encryption: String,
    val violationCount: Int,
    val canRegisterViolation: Boolean = true,
)
