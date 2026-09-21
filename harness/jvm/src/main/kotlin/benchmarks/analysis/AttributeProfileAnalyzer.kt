// Inspects rendered HTML attributes across scenarios for the standalone analysis report.
package benchmarks.analysis

import benchmarks.compose.*
import benchmarks.model.*
import org.jsoup.Jsoup

/**
 * Analyzes the attribute profiles of rendered HTML from each benchmark scenario.
 * Outputs statistics about attribute diversity, density, and categorization.
 */
fun main() {
    val scenarios = listOf(
        Scenario("Tailwind Catalog") {
            renderTailwindCatalog(catalogFixture())
        },
        Scenario("Form App") {
            renderFormApp(formFixture())
        },
        Scenario("Data Table") {
            renderDataTable(dataTableFixture())
        },
        Scenario("SVG Dashboard") {
            renderSvgDashboard(svgDashboardFixture())
        },
        Scenario("Content Article") {
            renderContentArticle(articleFixture())
        },
    )

    println("=" .repeat(100))
    println("COMPOSE HTML SSR — ATTRIBUTE PROFILE ANALYSIS")
    println("=" .repeat(100))
    println()

    val profiles = scenarios.map { scenario ->
        val html = scenario.render()
        val profile = analyzeHtml(html)
        printProfile(scenario.name, profile)
        scenario.name to profile
    }

    println()
    printComparisonTable(profiles)
}

private data class Scenario(val name: String, val render: () -> String)

private data class AttributeProfile(
    val totalElements: Int,
    val totalAttributes: Int,
    val totalAttributeNameChars: Int,
    val uniqueAttributeNames: Set<String>,
    val classAttributeCount: Int,
    val totalClassTokens: Int,
    val ariaAttributeCount: Int,
    val dataAttributeCount: Int,
    val formAttributeCount: Int, // type, name, value, placeholder, required, etc.
    val linkMediaAttributeCount: Int, // href, src, alt, width, height, loading, target, rel
    val svgAttributeCount: Int, // viewBox, d, cx, cy, r, fill, stroke, etc.
    val idAttributeCount: Int,
    val roleAttributeCount: Int,
    val otherAttributeCount: Int,
    val documentSizeBytes: Int,
    val textNodeCount: Int,
)

private val FORM_ATTRS = setOf(
    "type", "name", "value", "placeholder", "required", "disabled", "readonly",
    "checked", "selected", "multiple", "autocomplete", "autofocus", "form",
    "formaction", "formmethod", "formenctype", "formtarget", "formnovalidate",
    "min", "max", "minlength", "maxlength", "pattern", "step", "size",
    "accept", "capture", "list", "method", "action", "enctype", "novalidate",
    "for", "cols", "rows", "wrap",
)

private val LINK_MEDIA_ATTRS = setOf(
    "href", "src", "alt", "width", "height", "loading", "target", "rel",
    "download", "ping", "hreflang", "srcset", "sizes", "crossorigin",
    "referrerpolicy", "decoding", "fetchpriority",
)

private val SVG_ATTRS = setOf(
    "viewbox", "d", "cx", "cy", "r", "rx", "ry", "x", "y", "x1", "y1", "x2", "y2",
    "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
    "stroke-dasharray", "stroke-dashoffset", "opacity", "fill-opacity",
    "stroke-opacity", "transform", "text-anchor", "dominant-baseline",
    "font-size", "font-weight", "font-family", "offset", "stop-color",
    "stop-opacity", "gradientunits", "gradienttransform", "spreadmethod",
    "patternunits", "patterntransform", "preserveaspectratio", "points",
    "marker-start", "marker-mid", "marker-end", "clip-path", "mask",
    "xmlns", "xmlns:xlink",
)

private fun analyzeHtml(html: String): AttributeProfile {
    val doc = Jsoup.parse(html)
    // These renderers emit complete HTML documents. Jsoup's #root is not an HTML element.
    val allElements = doc.allElements.filter { it !== doc }
    var totalAttributes = 0
    var totalAttrNameChars = 0
    val uniqueNames = mutableSetOf<String>()
    var classCount = 0
    var totalClassTokens = 0
    var ariaCount = 0
    var dataCount = 0
    var formCount = 0
    var linkMediaCount = 0
    var svgCount = 0
    var idCount = 0
    var roleCount = 0
    var otherCount = 0
    var textNodeCount = 0

    for (element in allElements) {
        for (attr in element.attributes()) {
            val name = attr.key.lowercase()
            totalAttributes++
            totalAttrNameChars += name.length
            uniqueNames.add(name)

            when {
                name == "class" -> {
                    classCount++
                    totalClassTokens += attr.value.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                }
                name.startsWith("aria-") -> ariaCount++
                name.startsWith("data-") -> dataCount++
                name == "id" -> idCount++
                name == "role" -> roleCount++
                name in FORM_ATTRS -> formCount++
                name in LINK_MEDIA_ATTRS -> linkMediaCount++
                name.lowercase() in SVG_ATTRS -> svgCount++
                else -> otherCount++
            }
        }
        textNodeCount += element.textNodes().size
    }

    return AttributeProfile(
        totalElements = allElements.size,
        totalAttributes = totalAttributes,
        totalAttributeNameChars = totalAttrNameChars,
        uniqueAttributeNames = uniqueNames,
        classAttributeCount = classCount,
        totalClassTokens = totalClassTokens,
        ariaAttributeCount = ariaCount,
        dataAttributeCount = dataCount,
        formAttributeCount = formCount,
        linkMediaAttributeCount = linkMediaCount,
        svgAttributeCount = svgCount,
        idAttributeCount = idCount,
        roleAttributeCount = roleCount,
        otherAttributeCount = otherCount,
        documentSizeBytes = html.toByteArray(Charsets.UTF_8).size,
        textNodeCount = textNodeCount,
    )
}

private fun printProfile(name: String, p: AttributeProfile) {
    println("--- $name ---")
    println("  Elements: ${p.totalElements}")
    println("  Total attributes: ${p.totalAttributes}")
    println("  Unique attribute names: ${p.uniqueAttributeNames.size}")
    println("  Attribute name chars: ${p.totalAttributeNameChars}")
    println("  Document size: ${p.documentSizeBytes} bytes")
    println("  Text nodes: ${p.textNodeCount}")
    println("  Breakdown:")
    val total = p.totalAttributes.toDouble()
    fun pct(n: Int) = if (total > 0) "%.1f%%".format(n / total * 100) else "0%"
    println("    class:      ${p.classAttributeCount} (${pct(p.classAttributeCount)}) — ${p.totalClassTokens} tokens")
    println("    aria-*:     ${p.ariaAttributeCount} (${pct(p.ariaAttributeCount)})")
    println("    data-*:     ${p.dataAttributeCount} (${pct(p.dataAttributeCount)})")
    println("    form:       ${p.formAttributeCount} (${pct(p.formAttributeCount)})")
    println("    link/media: ${p.linkMediaAttributeCount} (${pct(p.linkMediaAttributeCount)})")
    println("    SVG:        ${p.svgAttributeCount} (${pct(p.svgAttributeCount)})")
    println("    id:         ${p.idAttributeCount} (${pct(p.idAttributeCount)})")
    println("    role:       ${p.roleAttributeCount} (${pct(p.roleAttributeCount)})")
    println("    other:      ${p.otherAttributeCount} (${pct(p.otherAttributeCount)})")
    println("  All names: ${p.uniqueAttributeNames.sorted().joinToString(", ")}")
    println()
}

private fun printComparisonTable(profiles: List<Pair<String, AttributeProfile>>) {
    println("COMPARISON TABLE")
    println("-".repeat(100))
    val header = "%-20s %8s %8s %8s %8s %8s %8s %8s %8s %8s".format(
        "Scenario", "Elements", "Attrs", "Unique", "class%", "aria%", "data%", "form%", "link%", "svg%"
    )
    println(header)
    println("-".repeat(100))
    for ((name, p) in profiles) {
        val total = p.totalAttributes.toDouble()
        fun pct(n: Int) = if (total > 0) "%.1f".format(n / total * 100) else "0.0"
        println(
            "%-20s %8d %8d %8d %7s%% %7s%% %7s%% %7s%% %7s%% %7s%%".format(
                name, p.totalElements, p.totalAttributes, p.uniqueAttributeNames.size,
                pct(p.classAttributeCount), pct(p.ariaAttributeCount), pct(p.dataAttributeCount),
                pct(p.formAttributeCount), pct(p.linkMediaAttributeCount), pct(p.svgAttributeCount)
            )
        )
    }
    println("-".repeat(100))
}
