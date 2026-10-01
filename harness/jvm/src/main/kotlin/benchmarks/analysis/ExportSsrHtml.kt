// Produces the prepared Compose and Thymeleaf HTML files used for verification and browser hydration.
package benchmarks.analysis

import benchmarks.hydrate1k.hydrate1kFixture
import benchmarks.hydrate1k.renderHydrate1k
import java.io.File
import benchmarks.thymeleaf.ThymeleafRenderer

fun main() {
    val outDir = File("build/ssr-html")
    outDir.mkdirs()

    val scenarios = workloadNames.associateWith { name -> composeWorkload(name, workloadFixture(name))() } + mapOf(
        "hydrate1k" to renderHydrate1k(hydrate1kFixture()),
        // These workloads measure client-side mounting; the server root is intentionally empty.
        "update10th1k" to "",
        "reorder1k" to "",
        "filter-list" to ""
    )

    for (name in documentWorkloads) {
        File(outDir, "$name.body.html").writeText(composeWorkloadBody(name, workloadFixture(name)))
    }
    for (name in thymeleafWorkloads) {
        val fixture = workloadFixture(name)
        val variables = if (fixture == null) emptyMap() else mapOf("data" to fixture)
        File(outDir, "$name.thymeleaf.html").writeText(ThymeleafRenderer.render(name, variables))
    }

    for ((name, html) in scenarios) {
        val file = File(outDir, "$name.html")
        file.writeText(html)
        println("Exported Compose SSR HTML for $name: ${html.length} chars -> ${file.path}")
    }
}
