package dev.easonhuang.heartwood.data

/** Converts a canonical internal metric value to the user's chosen unit system. */
fun Metric.convertValue(value: Float, system: UnitSystem): Float {
    if (system == UnitSystem.METRIC) return value
    return when (this) {
        Metric.DISTANCE -> value * KM_TO_MILES
        Metric.WEIGHT -> value * KG_TO_LBS
        Metric.HEIGHT -> value * CM_TO_INCHES
        Metric.HYDRATION -> value * LITERS_TO_FL_OZ
        else -> value
    }
}

/** Converts a value from the user's unit system back to internal metric units. */
fun Metric.toCanonicalValue(value: Float, system: UnitSystem): Float {
    if (system == UnitSystem.METRIC) return value
    return when (this) {
        Metric.DISTANCE -> value / KM_TO_MILES
        Metric.WEIGHT -> value / KG_TO_LBS
        Metric.HEIGHT -> value / CM_TO_INCHES
        Metric.HYDRATION -> value / LITERS_TO_FL_OZ
        else -> value
    }
}

const val KM_TO_MILES = 0.621371f
const val KG_TO_LBS = 2.20462f
const val CM_TO_INCHES = 0.393701f
const val LITERS_TO_FL_OZ = 33.814f

/** Shared value formatting so the dashboard, summary and export all read the same. */
fun Metric.formatValue(value: Float): String = when (this) {
    Metric.STEPS, Metric.FLOORS -> "%,d".format(value.toLong())
    Metric.DISTANCE, Metric.HYDRATION -> "%.2f".format(value)
    Metric.VO2MAX, Metric.WEIGHT, Metric.SLEEP, Metric.HEIGHT -> "%.1f".format(value)
    else -> "%.0f".format(value)
}

fun Metric.formatValue(value: Float, system: UnitSystem): String =
    formatValue(convertValue(value, system))
