package benchmarks.contentarticle

import benchmarks.compose.ContentArticle
import benchmarks.model.articleDataFromJson
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.hydrateRoot

internal expect fun markPerformance(name: String)

fun main() {
    markPerformance("kt-hydrate-start")
    hydrateRoot(
        deserializeState = { serialized -> articleDataFromJson(Json.parseToJsonElement(serialized)) },
        onHydrationMismatch = { throw it },
    ) { data -> ContentArticle(data) }
    markPerformance("kt-hydrate-end")
    document.body?.setAttribute("data-app-ready", "true")
    markPerformance("app-ready")
}
