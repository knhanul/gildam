package kr.co.nuni.gildam.data.list

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.TransportType

data class ActivityPlaceInfo(
    val regionName: String? = null,
    val startPlace: String? = null,
    val endPlace: String? = null,
    val placesResolved: Boolean = false
) {
    fun displayPlaces(arrowFormat: (String, String) -> String = { s, e -> "$s → $e" }): String? {
        val s = startPlace?.trim()?.takeIf { it.isNotEmpty() }
        val e = endPlace?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            s != null && e != null -> if (s == e) s else arrowFormat(s, e)
            s != null -> s
            e != null -> e
            else -> null
        }
    }
}

object RecordListFilter {
    const val SHORT_DISTANCE_METERS = 100.0
    const val SHORT_DURATION_MS = 60_000L

    fun isShortRecord(activity: ActivityEntity): Boolean =
        activity.status == ActivityStatus.COMPLETED &&
            (activity.distanceMeters < SHORT_DISTANCE_METERS || activity.totalDurationMs < SHORT_DURATION_MS)

    fun matchesQuery(
        activity: ActivityEntity,
        place: ActivityPlaceInfo?,
        query: String
    ): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (activity.title.contains(q, ignoreCase = true)) return true
        if (place?.startPlace?.contains(q, ignoreCase = true) == true) return true
        if (place?.endPlace?.contains(q, ignoreCase = true) == true) return true
        if (place?.regionName?.contains(q, ignoreCase = true) == true) return true
        return false
    }

    data class FilterResult(
        val visible: List<ActivityEntity>,
        val hiddenShort: List<ActivityEntity>
    )

    fun filter(
        activities: List<ActivityEntity>,
        places: Map<String, ActivityPlaceInfo>,
        transportFilter: TransportType?,
        query: String,
        hideShort: Boolean
    ): FilterResult {
        val matched = activities.filter { item ->
            val matchesTransport = transportFilter == null || item.transportType == transportFilter
            matchesTransport && matchesQuery(item, places[item.id], query)
        }
        if (!hideShort) return FilterResult(visible = matched, hiddenShort = emptyList())
        val (shortOnes, normalOnes) = matched.partition { isShortRecord(it) }
        return FilterResult(visible = normalOnes, hiddenShort = shortOnes)
    }
}
