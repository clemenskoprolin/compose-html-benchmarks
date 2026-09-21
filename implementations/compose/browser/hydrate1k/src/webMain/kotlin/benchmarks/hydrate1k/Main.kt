package benchmarks.hydrate1k

import kotlinx.browser.document
import org.jetbrains.compose.web.hydrateRoot

internal expect fun hydrate1kStateFromJson(serialized: String): Hydrate1kState

internal expect fun markPerformance(name: String)

fun main() {
    markPerformance("kt-hydrate-start")
    hydrateRoot(
        deserializeState = ::hydrate1kStateFromJson,
        onHydrationMismatch = { throw it },
    ) { initialState ->
        Hydrate1kTable(initialState)
    }
    markPerformance("kt-hydrate-end")
    document.body?.setAttribute("data-app-ready", "true")
    markPerformance("app-ready")
}
