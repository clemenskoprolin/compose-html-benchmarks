// Produces the prepared JVM renderer HTML files used for verification and browser hydration.
package benchmarks.analysis

import benchmarks.hydrate1k.hydrate1kFixture
import benchmarks.hydrate1k.renderHydrate1k
import java.io.File

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
    for (target in JvmSsrTarget.entries.filter { it != JvmSsrTarget.COMPOSE_JVM }) {
        for (name in templateWorkloads) {
            // Thymeleaf's recursive stack template does not complete in practical time.
            if (skippedJvmSsrWorkload(target, name) != null) continue
            File(outDir, "$name.${target.argument}.html").writeText(jvmSsrWorkload(target, name, workloadFixture(name))())
        }
    }

    for ((name, html) in scenarios) {
        val file = File(outDir, "$name.html")
        file.writeText(html)
        println("Exported Compose SSR HTML for $name: ${html.length} chars -> ${file.path}")
    }
}
