package benchmarks.datatable

import benchmarks.compose.DataTable
import benchmarks.model.dataTableDataFromJson
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.hydrateRoot

internal expect fun markPerformance(name: String)

fun main() {
    markPerformance("kt-hydrate-start")
    hydrateRoot(
        deserializeState = { serialized -> dataTableDataFromJson(Json.parseToJsonElement(serialized)) },
        onHydrationMismatch = { throw it },
    ) { data -> DataTable(data) }
    markPerformance("kt-hydrate-end")
    document.body?.setAttribute("data-app-ready", "true")
    markPerformance("app-ready")
}
