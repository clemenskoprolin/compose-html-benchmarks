package benchmarks.reorder1k

import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable

internal expect fun reorder1kStateFromJson(serialized: String): Reorder1kState
internal expect fun markPerformance(name: String)

fun main() {
    val serializedState = document.getElementById("search-state")?.textContent
        ?: error("Missing reorder1k initial state")
    markPerformance("kt-mount-start")
    renderComposable("search-page") { Reorder1kTable(reorder1kStateFromJson(serializedState)) }
    markPerformance("kt-mount-end")
    document.body?.setAttribute("data-app-ready", "true")
    markPerformance("app-ready")
}
