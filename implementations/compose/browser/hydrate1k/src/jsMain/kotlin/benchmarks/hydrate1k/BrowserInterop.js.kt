package benchmarks.hydrate1k

import kotlinx.browser.window

internal actual fun hydrate1kStateFromJson(serialized: String): Hydrate1kState {
    val value = JSON.parse<dynamic>(serialized)
    val rows = (value.rows as Array<dynamic>).map { row ->
        Hydrate1kRow(id = row.id as Int, label = row.label as String)
    }
    return Hydrate1kState(rows)
}

internal actual fun markPerformance(name: String) {
    window.performance.asDynamic().mark(name)
}
