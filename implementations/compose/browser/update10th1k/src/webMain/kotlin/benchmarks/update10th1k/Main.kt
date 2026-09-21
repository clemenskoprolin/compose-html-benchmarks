package benchmarks.update10th1k

import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable

internal expect fun update10th1kStateFromJson(serialized: String): Update10th1kState
internal expect fun markPerformance(name: String)

fun main() {
    val serializedState = document.getElementById("search-state")?.textContent
        ?: error("Missing update10th1k initial state")
    markPerformance("kt-mount-start")
    renderComposable("search-page") { Update10th1kTable(update10th1kStateFromJson(serializedState)) }
    markPerformance("kt-mount-end")
    document.body?.setAttribute("data-app-ready", "true")
    markPerformance("app-ready")
}
