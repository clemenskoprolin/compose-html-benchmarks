package benchmarks.update10th1k

import kotlinx.browser.window

internal actual fun update10th1kStateFromJson(serialized: String): Update10th1kState {
    val value = JSON.parse<dynamic>(serialized)
    val rows = (value.rows as Array<dynamic>).map { row ->
        Update10th1kRow(id = row.id as Int, label = row.label as String)
    }
    return Update10th1kState(rows)
}

internal actual fun markPerformance(name: String) {
    window.performance.asDynamic().mark(name)
}
