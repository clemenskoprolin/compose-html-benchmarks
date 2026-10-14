// Scenario catalog, fixture lookup and renderer preparation for reports and profiling.
package benchmarks.analysis

import benchmarks.compose.*
import benchmarks.model.*
import benchmarks.jstachio.JStachioRenderer
import benchmarks.jte.JteRenderer
import benchmarks.thymeleaf.ThymeleafRenderer
import java.io.Writer

private val rendererVersions = java.util.Properties().apply {
    checkNotNull(JvmSsrScenario::class.java.getResourceAsStream("/renderer-versions.properties")).use(::load)
}

internal enum class JvmSsrTarget(val argument: String, val label: String, val resultKey: String, val version: String? = null) {
    COMPOSE_JVM("compose", "Compose JVM", "rawProcesses"),
    THYMELEAF("thymeleaf", "Thymeleaf", "thymeleafProcesses", rendererVersions.getProperty("thymeleaf")),
    JSTACHIO("jstachio", "JStachio", "jstachioProcesses", rendererVersions.getProperty("jstachio")),
    JTE("jte", "jte", "jteProcesses", rendererVersions.getProperty("jte")),
}

internal fun selectedJvmSsrTargets(value: String? = System.getenv("BENCHMARK_TARGETS")): List<JvmSsrTarget> {
    val requested = value?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
    if (requested.isNullOrEmpty()) return JvmSsrTarget.entries
    val selected = JvmSsrTarget.entries.filter { it.argument in requested }
    if (selected.isNotEmpty()) return selected
    error("No JVM SSR targets selected: $value")
}

internal fun skipReason(target: JvmSsrTarget, name: String): String? = when {
    target != JvmSsrTarget.COMPOSE_JVM && name !in templateWorkloads ->
        "workload is not implemented for this renderer"
    target == JvmSsrTarget.THYMELEAF && name == "preact-stack" -> "renderer does not finish"
    target == JvmSsrTarget.JSTACHIO && name == "preact-stack" -> "recursive component workload is not implemented"
    else -> null
}

private fun templateName(name: String) = if (name == "dynamic-text-escaped") "dynamic-text" else name

internal fun jvmSsrWorkload(target: JvmSsrTarget, name: String, fixture: Any?): () -> String {
    skipReason(target, name)?.let { error("${target.label} $name is not supported: $it") }
    val template = templateName(name)
    return when (target) {
        JvmSsrTarget.COMPOSE_JVM -> composeWorkload(name, fixture)
        JvmSsrTarget.THYMELEAF -> { { ThymeleafRenderer.render(template, fixture) } }
        JvmSsrTarget.JSTACHIO -> { { JStachioRenderer.render(template, fixture) } }
        JvmSsrTarget.JTE -> { { JteRenderer.render(template, fixture) } }
    }
}

internal fun streamJvmSsrWorkload(
    target: JvmSsrTarget, name: String, fixture: Any?, writer: Writer,
    chunkSize: Int = 2048, composeOutput: String = "writer",
) {
    when (target) {
        JvmSsrTarget.COMPOSE_JVM -> if (composeOutput == "writer")
            streamComposeScenario(name, fixture, chunkSize, writer)
        else streamComposeScenario(name, fixture, chunkSize, writer::write)
        JvmSsrTarget.THYMELEAF -> ThymeleafRenderer.stream(templateName(name), fixture, writer)
        JvmSsrTarget.JSTACHIO -> JStachioRenderer.stream(templateName(name), fixture, writer)
        JvmSsrTarget.JTE -> JteRenderer.stream(templateName(name), fixture, writer)
    }
}

internal enum class JvmSsrScenario(val argument: String, val supportsTemplates: Boolean = true) {
    TAILWIND_CATALOG("tailwind-catalog"),
    FORM_APP("form-app"),
    DATA_TABLE("data-table"),
    SVG_DASHBOARD("svg-dashboard"),
    CONTENT_ARTICLE("content-article"),
    PREACT_TEXT("preact-text"),
    DYNAMIC_TEXT("dynamic-text"),
    DYNAMIC_TEXT_ESCAPED("dynamic-text-escaped"),
    PREACT_SEARCH_RESULTS("preact-search-results"),
    PREACT_STACK("preact-stack"),
    RAW_TEXT_STYLE("raw-text-style", supportsTemplates = false),
    RAW_TEXT_ELEMENTS("raw-text-elements", supportsTemplates = false),
    FRACTIONAL_STYLE("fractional-style", supportsTemplates = false),
    STYLE_HEAVY_CATALOG("style-heavy-catalog", supportsTemplates = false),
}

internal val workloadNames = JvmSsrScenario.entries.map { it.argument }
internal val templateWorkloads = JvmSsrScenario.entries.filter { it.supportsTemplates }.map { it.argument }.toSet()
internal val documentWorkloads = listOf("tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article")

internal fun workloadFixture(name: String): Any? = when (name) {
    "tailwind-catalog" -> catalogFixture()
    "form-app" -> formFixture()
    "data-table" -> dataTableFixture()
    "svg-dashboard" -> svgDashboardFixture()
    "content-article" -> articleFixture()
    "preact-search-results" -> preactSearchResultsFixture()
    "dynamic-text" -> textFixture()
    "dynamic-text-escaped" -> textFixture(escaped = true)
    "preact-text", "preact-stack" -> null
    "raw-text-style", "raw-text-elements", "fractional-style" -> null
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
    "dynamic-text", "dynamic-text-escaped" -> { { renderDynamicText(fixture as TextData) } }
    "preact-search-results" -> { { renderPreactSearchResults(fixture as PreactSearchResultsData) } }
    "preact-stack" -> ::renderPreactStack
    "fractional-style" -> ::renderFractionalStyle
    "raw-text-style" -> ::renderRawTextStyle
    "raw-text-elements" -> ::renderRawTextElements
    "style-heavy-catalog" -> { { renderStyleHeavyCatalog(fixture as CatalogData) } }
    else -> error("Unknown workload: $name")
}

internal fun composeWorkloadBody(name: String, fixture: Any?): String = when (name) {
    "tailwind-catalog" -> renderTailwindCatalogBody(fixture as CatalogData)
    "form-app" -> renderFormAppBody(fixture as FormData)
    "data-table" -> renderDataTableBody(fixture as DataTableData)
    "svg-dashboard" -> renderSvgDashboardBody(fixture as SvgDashboardData)
    "content-article" -> renderContentArticleBody(fixture as ArticleData)
    else -> error("Unknown document workload: $name")
}
