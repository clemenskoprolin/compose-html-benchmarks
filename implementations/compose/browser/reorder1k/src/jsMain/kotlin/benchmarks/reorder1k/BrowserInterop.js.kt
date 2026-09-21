package benchmarks.reorder1k

import kotlinx.browser.window

internal actual fun reorder1kStateFromJson(serialized: String): Reorder1kState {
    val value = JSON.parse<dynamic>(serialized)
    val rows = (value.rows as Array<dynamic>).map { row ->
        Reorder1kRow(id = row.id as Int, label = row.label as String)
    }
    return Reorder1kState(rows)
}

internal actual fun markPerformance(name: String) {
    window.performance.asDynamic().mark(name)
}
