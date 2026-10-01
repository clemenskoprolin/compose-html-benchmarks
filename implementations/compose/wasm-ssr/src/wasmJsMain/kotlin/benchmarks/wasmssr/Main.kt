@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package benchmarks.wasmssr

import benchmarks.compose.*
import benchmarks.model.*
import kotlinx.serialization.json.*
import kotlin.js.JsString
import kotlin.js.toJsString

@JsFun("(index) => process.argv[index]")
private external fun argument(index: Int): JsString

@JsFun("(path) => process.getBuiltinModule('node:fs').readFileSync(path, 'utf8')")
private external fun readText(path: JsString): JsString

@JsFun("() => performance.now()")
private external fun now(): Double

private fun scenarioRenderer(name: String, fixtures: JsonObject): () -> String = when (name) {
    "tailwind-catalog" -> {
        val data = catalogDataFromJson(fixtures.getValue(name))
        val render: () -> String = { renderTailwindCatalog(data) }
        render
    }
    "form-app" -> {
        val data = formDataFromJson(fixtures.getValue(name))
        val render: () -> String = { renderFormApp(data) }
        render
    }
    "data-table" -> {
        val data = dataTableDataFromJson(fixtures.getValue(name))
        val render: () -> String = { renderDataTable(data) }
        render
    }
    "svg-dashboard" -> {
        val data = svgDashboardDataFromJson(fixtures.getValue(name))
        val render: () -> String = { renderSvgDashboard(data) }
        render
    }
    "content-article" -> {
        val data = articleDataFromJson(fixtures.getValue(name))
        val render: () -> String = { renderContentArticle(data) }
        render
    }
    "preact-text" -> ::renderPreactText
    "preact-search-results" -> {
        val data = preactSearchResultsDataFromJson(fixtures.getValue(name))
        val render: () -> String = { renderPreactSearchResults(data) }
        render
    }
    "preact-stack" -> ::renderPreactStack
    "raw-text-style" -> ::renderRawTextStyle
    "raw-text-elements" -> ::renderRawTextElements
    "style-heavy-catalog" -> {
        val data = catalogDataFromJson(fixtures.getValue("tailwind-catalog"))
        val render: () -> String = { renderStyleHeavyCatalog(data) }
        render
    }
    else -> error("Unknown Wasm SSR scenario: $name")
}

fun main() {
    val name = argument(2).toString()
    val warmups = argument(3).toString().toInt()
    val trials = argument(4).toString().toInt()
    val iterations = argument(5).toString().toInt()
    require(warmups >= 0 && trials > 0 && iterations > 0)

    val fixtures = Json.parseToJsonElement(readText("workloads/jvm/src/main/resources/fixtures.json".toJsString()).toString()).jsonObject
    val expected = readText("build/ssr-html/$name.html".toJsString()).toString()
    val render = scenarioRenderer(name, fixtures)
    val firstStart = now()
    val firstHtml = render()
    val firstMs = now() - firstStart
    check(firstHtml == expected) { "$name: Wasm SSR output differs from the prepared JVM output" }

    repeat(warmups) { check(render() == firstHtml) }
    val samples = List(trials) {
        var html = ""
        val start = now()
        repeat(iterations) { html = render() }
        val elapsed = (now() - start) / iterations
        check(html == firstHtml) { "$name: output changed during measurement" }
        elapsed
    }
    println(buildJsonObject {
        put("scenario", name)
        put("initializationAndFirstMs", firstMs)
        put("samples", JsonArray(samples.map(::JsonPrimitive)))
        put("outputBytes", firstHtml.encodeToByteArray().size)
    })
}
