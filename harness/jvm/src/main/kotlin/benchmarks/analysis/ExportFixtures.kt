// Serializes deterministic workload fixtures to JSON so every renderer receives the same input.
package benchmarks.analysis

import benchmarks.model.*
import benchmarks.hydrate1k.hydrate1kFixture
import benchmarks.update10th1k.update10th1kFixture
import benchmarks.reorder1k.reorder1kFixture
import benchmarks.filterlist.filterListFixture
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

private fun textSpec(escaped: Boolean) = mapOf("count" to 1_000, "escaped" to escaped)

fun main() {
    val mapper = ObjectMapper().registerKotlinModule()
    val allFixtures = mapOf(
        "tailwind-catalog" to catalogFixture(),
        "form-app" to formFixture(),
        "data-table" to dataTableFixture(),
        "svg-dashboard" to svgDashboardFixture(),
        "content-article" to articleFixture(),
        "hydrate1k" to hydrate1kFixture(),
        "update10th1k" to update10th1kFixture(),
        "reorder1k" to reorder1kFixture(),
        "filter-list" to filterListFixture(),
        "preact-search-results" to preactSearchResultsFixture(),
        "dynamic-text" to textSpec(escaped = false),
        "dynamic-text-escaped" to textSpec(escaped = true)
    )
    val outFile = File("workloads/jvm/src/main/resources/fixtures.json")
    outFile.parentFile.mkdirs()
    mapper.writerWithDefaultPrettyPrinter().writeValue(outFile, allFixtures)
    println("Exported fixtures to: ${outFile.absolutePath}")
}
