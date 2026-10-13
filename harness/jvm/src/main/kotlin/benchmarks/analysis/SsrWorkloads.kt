// Scenario catalog, fixture lookup and renderer preparation for reports and profiling.
package benchmarks.analysis

import benchmarks.compose.*
import benchmarks.model.*
import benchmarks.jstachio.JStachioRenderer
import benchmarks.jte.JteRenderer
import benchmarks.thymeleaf.ThymeleafRenderer

internal enum class JvmSsrTarget(val argument: String, val label: String, val resultKey: String, val version: String? = null) {
    COMPOSE_JVM("compose", "Compose JVM", "rawProcesses"),
    THYMELEAF("thymeleaf", "Thymeleaf", "thymeleafProcesses", "3.1.3.RELEASE"),
    JSTACHIO("jstachio", "JStachio", "jstachioProcesses", "1.3.7"),
    JTE("jte", "jte", "jteProcesses", "3.2.4"),
}

internal fun selectedJvmSsrTargets(value: String? = System.getenv("BENCHMARK_TARGETS")): List<JvmSsrTarget> {
    val requested = value?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
    if (requested.isNullOrEmpty()) return JvmSsrTarget.entries
    val selected = JvmSsrTarget.entries.filter { it.argument in requested ||
        (it == JvmSsrTarget.COMPOSE_JVM && "compose-jvm" in requested) }
    if (selected.isNotEmpty()) return selected
    // Browser target selections also reach the companion JVM report.
    return JvmSsrTarget.entries.filter { it != JvmSsrTarget.THYMELEAF || "no-thymeleaf" !in requested }
}

internal fun supportsJvmSsrWorkload(target: JvmSsrTarget, name: String): Boolean =
    target == JvmSsrTarget.COMPOSE_JVM || name in templateWorkloads

internal fun skippedJvmSsrWorkload(target: JvmSsrTarget, name: String): String? =
    if (target == JvmSsrTarget.THYMELEAF) skippedThymeleafWorkloads[name] else null

internal fun jvmSsrWorkload(target: JvmSsrTarget, name: String, fixture: Any?): () -> String {
    require(supportsJvmSsrWorkload(target, name)) { "${target.label} does not support $name" }
    skippedJvmSsrWorkload(target, name)?.let { error("${target.label} $name is not supported: $it") }
    return when (target) {
        JvmSsrTarget.COMPOSE_JVM -> composeWorkload(name, fixture)
        JvmSsrTarget.THYMELEAF -> checkNotNull(thymeleafWorkload(name, fixture))
        JvmSsrTarget.JSTACHIO -> { { JStachioRenderer.render(name, fixture) } }
        JvmSsrTarget.JTE -> { { JteRenderer.render(name, fixture) } }
    }
}

internal enum class JvmSsrScenario(val argument: String, val supportsTemplates: Boolean = true) {
    TAILWIND_CATALOG("tailwind-catalog"),
    FORM_APP("form-app"),
    DATA_TABLE("data-table"),
    SVG_DASHBOARD("svg-dashboard"),
    CONTENT_ARTICLE("content-article"),
    PREACT_TEXT("preact-text"),
    PREACT_SEARCH_RESULTS("preact-search-results"),
    PREACT_STACK("preact-stack"),
    RAW_TEXT_STYLE("raw-text-style", supportsTemplates = false),
    RAW_TEXT_ELEMENTS("raw-text-elements", supportsTemplates = false),
    STYLE_HEAVY_CATALOG("style-heavy-catalog", supportsTemplates = false),
}

internal val workloadNames = JvmSsrScenario.entries.map { it.argument }
internal val templateWorkloads = JvmSsrScenario.entries.filter { it.supportsTemplates }.map { it.argument }.toSet()
internal val thymeleafWorkloads = templateWorkloads
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
    return jvmSsrWorkload(target, name, workloadFixture(name))
}
