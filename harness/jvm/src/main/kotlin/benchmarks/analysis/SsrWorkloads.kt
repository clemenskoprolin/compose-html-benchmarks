// Scenario catalog, fixture lookup and renderer preparation for reports and profiling.
package benchmarks.analysis

import benchmarks.compose.*
import benchmarks.model.*
import benchmarks.thymeleaf.ThymeleafRenderer

internal enum class JvmSsrTarget(val argument: String) {
    COMPOSE_JVM("compose"),
    THYMELEAF("thymeleaf"),
}

internal enum class JvmSsrScenario(val argument: String, val supportsThymeleaf: Boolean = true) {
    TAILWIND_CATALOG("tailwind-catalog"),
    FORM_APP("form-app"),
    DATA_TABLE("data-table"),
    SVG_DASHBOARD("svg-dashboard"),
    CONTENT_ARTICLE("content-article"),
    PREACT_TEXT("preact-text"),
    PREACT_SEARCH_RESULTS("preact-search-results"),
    PREACT_STACK("preact-stack"),
    RAW_TEXT_STYLE("raw-text-style", supportsThymeleaf = false),
    RAW_TEXT_ELEMENTS("raw-text-elements", supportsThymeleaf = false),
    STYLE_HEAVY_CATALOG("style-heavy-catalog", supportsThymeleaf = false),
}

internal val workloadNames = JvmSsrScenario.entries.map { it.argument }
internal val thymeleafWorkloads = JvmSsrScenario.entries.filter { it.supportsThymeleaf }.map { it.argument }.toSet()
internal val skippedThymeleafWorkloads = mapOf("preact-stack" to "renderer does not finish")
internal val documentWorkloads = listOf("tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article")

internal fun workloadFixture(name: String): Any? = when (name) {
    "tailwind-catalog" -> catalogFixture()
    "form-app" -> formFixture()
    "data-table" -> dataTableFixture()
    "svg-dashboard" -> svgDashboardFixture()
    "content-article" -> articleFixture()
    "preact-search-results" -> preactSearchResultsFixture()
    "preact-text", "preact-stack" -> null
    "raw-text-style", "raw-text-elements" -> null
    "style-heavy-catalog" -> catalogFixture()
    else -> error("Unknown workload: $name")
}

internal fun composeWorkload(name: String, fixture: Any?): () -> String = when (name) {
    "tailwind-catalog" -> { { renderTailwindCatalog(fixture as CatalogData) } }
    "form-app" -> { { renderFormApp(fixture as FormData) } }
    "data-table" -> { { renderDataTable(fixture as DataTableData) } }
    "svg-dashboard" -> { { renderSvgDashboard(fixture as SvgDashboardData) } }
    "content-article" -> { { renderContentArticle(fixture as ArticleData) } }
    "preact-text" -> ::renderPreactText
    "preact-search-results" -> { { renderPreactSearchResults(fixture as PreactSearchResultsData) } }
    "preact-stack" -> ::renderPreactStack
    "raw-text-style" -> ::renderRawTextStyle
    "raw-text-elements" -> ::renderRawTextElements
    "style-heavy-catalog" -> { { renderStyleHeavyCatalog(fixture as CatalogData) } }
    else -> error("Unknown workload: $name")
}

internal fun thymeleafWorkload(name: String, fixture: Any?): (() -> String)? = when (name) {
    in thymeleafWorkloads -> { { ThymeleafRenderer.render(name, mapOf("data" to fixture)) } }
    else -> null
}

internal fun composeWorkloadBody(name: String, fixture: Any?): String = when (name) {
    "tailwind-catalog" -> renderTailwindCatalogBody(fixture as CatalogData)
    "form-app" -> renderFormAppBody(fixture as FormData)
    "data-table" -> renderDataTableBody(fixture as DataTableData)
    "svg-dashboard" -> renderSvgDashboardBody(fixture as SvgDashboardData)
    "content-article" -> renderContentArticleBody(fixture as ArticleData)
    else -> error("Unknown document workload: $name")
}

internal fun prepareJvmSsrWorkload(target: JvmSsrTarget, scenario: JvmSsrScenario): () -> String {
    val name = scenario.argument
    if (target == JvmSsrTarget.THYMELEAF) {
        skippedThymeleafWorkloads[name]?.let { reason ->
            error("Thymeleaf $name is not supported: $reason")
        }
    }
    val fixture = workloadFixture(name)
    return if (target == JvmSsrTarget.THYMELEAF) {
        thymeleafWorkload(name, fixture) ?: error("Unknown Thymeleaf workload: $name")
    } else {
        composeWorkload(name, fixture)
    }
}
