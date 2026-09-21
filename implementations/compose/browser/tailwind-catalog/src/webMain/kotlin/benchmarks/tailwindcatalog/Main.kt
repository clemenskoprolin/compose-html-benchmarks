package benchmarks.tailwindcatalog

import benchmarks.compose.TailwindCatalog
import benchmarks.model.catalogDataFromJson
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.hydrateRoot

internal expect fun markPerformance(name: String)

fun main() {
    markPerformance("kt-hydrate-start")
    hydrateRoot(
        deserializeState = { serialized -> catalogDataFromJson(Json.parseToJsonElement(serialized)) },
        onHydrationMismatch = { throw it },
    ) { data -> TailwindCatalog(data) }
    markPerformance("kt-hydrate-end")
    document.body?.setAttribute("data-app-ready", "true")
    markPerformance("app-ready")
}
