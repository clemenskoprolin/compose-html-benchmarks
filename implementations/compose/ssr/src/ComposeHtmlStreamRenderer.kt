package benchmarks.compose

import androidx.compose.runtime.Composable
import benchmarks.model.*
import java.lang.reflect.InvocationTargetException
import java.io.Writer
import kotlinx.browser.dom.Element
import org.jetbrains.compose.web.dom.TagElement
import org.jetbrains.compose.web.dom.Text

// Keep the other benchmark modes buildable against Compose HTML versions without streaming.
private val streamRenderer by lazy {
    runCatching {
        Class.forName("org.jetbrains.compose.web.ComposeHtmlToStreamKt").methods.single {
            it.name == "composeHtmlToStream" && it.parameterCount == 6
        }
    }.getOrNull() ?: error("This Compose HTML checkout does not provide composeHtmlToStream")
}

private fun scenarioContent(name: String, data: Any?): @Composable () -> Unit = {
    when (name) {
        "tailwind-catalog" -> TailwindCatalogDocument(data as CatalogData)
        "form-app" -> FormAppDocument(data as FormData)
        "data-table" -> DataTableDocument(data as DataTableData)
        "svg-dashboard" -> SvgDashboardDocument(data as SvgDashboardData)
        "content-article" -> ContentArticleDocument(data as ArticleData)
        "preact-text" -> PreactText()
        "dynamic-text", "dynamic-text-escaped" -> DynamicText(data as TextData)
        "preact-search-results" -> PreactSearchResults(data as PreactSearchResultsData)
        "preact-stack" -> PreactStack()
        "fractional-style" -> FractionalStyle()
        "stream-pre", "stream-textarea" -> TagElement<Element>(
            if (name == "stream-pre") "pre" else "textarea", null,
        ) {
            Text("\n")
            repeat(256) { Text(STREAMING_STRESS_TEXT) }
        }
        else -> error("Unknown streaming scenario: $name")
    }

}

private val STREAMING_STRESS_TEXT = "a&😀α".repeat(512)

private val writerRenderer by lazy {
    Class.forName("org.jetbrains.compose.web.ComposeHtmlToWriterKt").methods.single {
        it.name == "composeHtmlToStream" && it.parameterCount == 6
    }
}

fun streamComposeScenario(name: String, data: Any?, chunkSize: Int, sink: (String) -> Unit) =
    invokeStreamRenderer(name, data, chunkSize, sink, false)

fun streamComposeScenario(name: String, data: Any?, chunkSize: Int, writer: Writer) =
    invokeStreamRenderer(name, data, chunkSize, writer, true)

private fun invokeStreamRenderer(name: String, data: Any?, chunkSize: Int, output: Any, writer: Boolean) {
    val content = scenarioContent(name, data)
    val key = if (System.getenv("BENCHMARK_REUSE_KEY") == "false") null else "stream-$name"
    val strict = System.getenv("COMPOSE_HTML_VALIDATE_STRICTLY")?.trim()?.lowercase() in setOf("true", "1")
    try {
        (if (writer) writerRenderer else streamRenderer).invoke(null, output, chunkSize, true, key, strict, content)
    } catch (failure: InvocationTargetException) {
        throw failure.targetException
    }
}
