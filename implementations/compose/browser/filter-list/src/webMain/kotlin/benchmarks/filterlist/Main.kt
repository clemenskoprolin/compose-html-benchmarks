package benchmarks.filterlist

import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable

internal expect fun filterListStateFromJson(serialized: String): FilterListState
internal expect fun markPerformance(name: String)

fun main() {
    val serializedState = document.getElementById("search-state")?.textContent
        ?: error("Missing filter-list initial state")
    markPerformance("kt-mount-start")
    renderComposable("search-page") { FilterListApp(filterListStateFromJson(serializedState)) }
    markPerformance("kt-mount-end")
    document.body?.setAttribute("data-app-ready", "true")
    markPerformance("app-ready")
}
