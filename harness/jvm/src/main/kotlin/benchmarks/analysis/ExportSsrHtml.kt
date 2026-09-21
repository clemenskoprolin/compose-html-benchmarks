// Produces the prepared Compose and Thymeleaf HTML files used for verification and browser hydration.
package benchmarks.analysis

import benchmarks.compose.*
import benchmarks.model.*
import benchmarks.hydrate1k.hydrate1kFixture
import benchmarks.hydrate1k.renderHydrate1k
import java.io.File
import benchmarks.thymeleaf.ThymeleafRenderer

fun main() {
    val outDir = File("build/ssr-html")
    outDir.mkdirs()

    val scenarios = mapOf(
        "tailwind-catalog" to renderTailwindCatalog(catalogFixture()),
        "form-app" to renderFormApp(formFixture()),
        "data-table" to renderDataTable(dataTableFixture()),
        "svg-dashboard" to renderSvgDashboard(svgDashboardFixture()),
        "content-article" to renderContentArticle(articleFixture()),
        "preact-text" to renderPreactText(),
        "preact-search-results" to renderPreactSearchResults(preactSearchResultsFixture()),
        "preact-stack" to renderPreactStack(),
        "hydrate1k" to renderHydrate1k(hydrate1kFixture()),
        // update10th1k measures client-side mounting; the server root is intentionally empty.
        "update10th1k" to "",
        // reorder1k measures client-side mounting; the server root is intentionally empty.
        "reorder1k" to "",
        // filter-list measures client-side mounting and patching; the server root is intentionally empty.
        "filter-list" to ""
    )

    val bodies = mapOf(
        "tailwind-catalog" to renderTailwindCatalogBody(catalogFixture()),
        "form-app" to renderFormAppBody(formFixture()),
        "data-table" to renderDataTableBody(dataTableFixture()),
        "svg-dashboard" to renderSvgDashboardBody(svgDashboardFixture()),
        "content-article" to renderContentArticleBody(articleFixture()),
    )
    for ((name, html) in bodies) File(outDir, "$name.body.html").writeText(html)

    val thymeleafScenarios = mapOf(
        "tailwind-catalog" to mapOf("data" to catalogFixture()),
        "form-app" to mapOf("data" to formFixture()),
        "data-table" to mapOf("data" to dataTableFixture()),
        "svg-dashboard" to mapOf("data" to svgDashboardFixture()),
        "content-article" to mapOf("data" to articleFixture()),
        "preact-text" to emptyMap(),
        "preact-search-results" to mapOf("data" to preactSearchResultsFixture()),
        "preact-stack" to emptyMap(),
    )
    for ((name, variables) in thymeleafScenarios) {
        File(outDir, "$name.thymeleaf.html").writeText(ThymeleafRenderer.render(name, variables))
    }

    for ((name, html) in scenarios) {
        val file = File(outDir, "$name.html")
        file.writeText(html)
        println("Exported Compose SSR HTML for $name: ${html.length} chars -> ${file.path}")
    }
}
