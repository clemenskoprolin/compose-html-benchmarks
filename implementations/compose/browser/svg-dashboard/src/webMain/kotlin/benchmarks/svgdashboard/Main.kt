package benchmarks.svgdashboard

import benchmarks.compose.SvgDashboard
import benchmarks.model.svgDashboardDataFromJson
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.hydrateRoot

internal expect fun markPerformance(name: String)

fun main() {
    markPerformance("kt-hydrate-start")
    hydrateRoot(
        deserializeState = { serialized -> svgDashboardDataFromJson(Json.parseToJsonElement(serialized)) },
        onHydrationMismatch = { throw it },
    ) { data -> SvgDashboard(data) }
    markPerformance("kt-hydrate-end")
    document.body?.setAttribute("data-app-ready", "true")
    markPerformance("app-ready")
}
