package benchmarks.compose

import androidx.compose.runtime.Composable
import benchmarks.model.BAVARIA_FOO
import benchmarks.model.BAVARIA_BAR
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.dom.*

// Adapted from preact-render-to-string benchmarks.

fun renderPreactText(): String = renderComposeHtmlToString("preact-text") { PreactText() }

@Composable
fun PreactText() {
    Div {
        repeat(1_000) {
            Div {
                Span(attrs = { classes("foo"); attr("data-testid", "foo") }) { Text(BAVARIA_FOO) }
                Span(attrs = { classes("bar"); attr("data-testid", "bar") }) { Text(BAVARIA_BAR) }
            }
        }
    }
}
