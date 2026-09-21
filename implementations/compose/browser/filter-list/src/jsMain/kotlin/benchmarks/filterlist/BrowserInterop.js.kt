package benchmarks.filterlist

import kotlinx.browser.window

internal actual fun filterListStateFromJson(serialized: String): FilterListState {
    val value = JSON.parse<dynamic>(serialized)
    val items = (value.items as Array<dynamic>).map { it as Int }
    return FilterListState(items)
}

internal actual fun markPerformance(name: String) {
    window.performance.asDynamic().mark(name)
}
