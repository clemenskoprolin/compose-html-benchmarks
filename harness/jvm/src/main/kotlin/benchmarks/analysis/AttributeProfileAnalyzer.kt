// Inspects rendered HTML attributes across scenarios for the standalone analysis report.
package benchmarks.analysis

import org.jsoup.Jsoup

/**
 * Analyzes the attribute profiles of rendered HTML from each benchmark scenario.
 * Outputs statistics about attribute diversity, density, and categorization.
 */
fun main() {
    val scenarioLabels = listOf("Tailwind Catalog", "Form App", "Data Table", "SVG Dashboard", "Content Article")

    println("=" .repeat(100))
    println("COMPOSE HTML SSR — ATTRIBUTE PROFILE ANALYSIS")
    println("=" .repeat(100))
    println()

    val profiles = documentWorkloads.zip(scenarioLabels).map { (scenario, label) ->
        val html = composeWorkload(scenario, workloadFixture(scenario))()
        val profile = analyzeHtml(html)
        printProfile(label, profile)
        label to profile
    }

    println()
    printComparisonTable(profiles)
}

private data class AttributeProfile(
    val totalElements: Int,
    val totalAttributes: Int,
    val totalAttributeNameChars: Int,
    val uniqueAttributeNames: Set<String>,
    val totalClassTokens: Int,
    val categories: Map<String, Int>,
    val documentSizeBytes: Int,
    val textNodeCount: Int,
)

// Labels retain the report's column spacing; keys are shared by counting and presentation.
private val categoryLabels = linkedMapOf(
    "class" to "class:      ", "aria" to "aria-*:     ", "data" to "data-*:     ",
    "form" to "form:       ", "link" to "link/media: ", "svg" to "SVG:        ",
    "id" to "id:         ", "role" to "role:       ", "other" to "other:      ",
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
    var totalClassTokens = 0
    val categories = categoryLabels.mapValues { 0 }.toMutableMap()
    var textNodeCount = 0

    for (element in allElements) {
        for (attr in element.attributes()) {
            val name = attr.key.lowercase()
            totalAttributes++
            totalAttrNameChars += name.length
            uniqueNames.add(name)

            val category = when {
                name == "class" -> "class"
                name.startsWith("aria-") -> "aria"
                name.startsWith("data-") -> "data"
                name == "id" -> "id"
                name == "role" -> "role"
                name in FORM_ATTRS -> "form"
                name in LINK_MEDIA_ATTRS -> "link"
                name in SVG_ATTRS -> "svg"
                else -> "other"
            }
            categories[category] = categories.getValue(category) + 1
            if (category == "class") {
                totalClassTokens += attr.value.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
            }
        }
        textNodeCount += element.textNodes().size
    }

    return AttributeProfile(
        totalElements = allElements.size,
        totalAttributes = totalAttributes,
        totalAttributeNameChars = totalAttrNameChars,
        uniqueAttributeNames = uniqueNames,
        totalClassTokens = totalClassTokens,
        categories = categories,
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
    for ((category, label) in categoryLabels) {
        val count = p.categories.getValue(category)
        val tokens = if (category == "class") " — ${p.totalClassTokens} tokens" else ""
        println("    $label$count (${pct(count)})$tokens")
    }
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
                *listOf("class", "aria", "data", "form", "link", "svg")
                    .map { pct(p.categories.getValue(it)) }.toTypedArray()
            )
        )
    }
    println("-".repeat(100))
}
