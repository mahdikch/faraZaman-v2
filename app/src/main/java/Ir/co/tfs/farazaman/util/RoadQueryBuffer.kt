package Ir.co.tfs.farazaman.util

object RoadQueryBuffer {

    const val BUFFER_GOOD = 10
    const val BUFFER_DEFAULT = 30

    /** دقت کمتر از این مقدار (متر) «خوب» در نظر گرفته می‌شود. */
    const val GOOD_ACCURACY_THRESHOLD_METERS = 20f

    fun forAccuracy(accuracyMeters: Float?): Int {
        if (accuracyMeters == null || accuracyMeters <= 0f || !accuracyMeters.isFinite()) {
            return BUFFER_DEFAULT
        }
        return if (accuracyMeters <= GOOD_ACCURACY_THRESHOLD_METERS) BUFFER_GOOD else BUFFER_DEFAULT
    }
}
