package benchmarks.compose

import androidx.compose.runtime.Composable
import benchmarks.model.TextData
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

fun renderDynamicText(data: TextData): String = renderComposeHtmlToString("dynamic-text") { DynamicText(data) }

@Composable
fun DynamicText(data: TextData) {
    Div {
        data.items.forEach { item ->
            Div {
                Span(attrs = { classes("foo"); attr("data-testid", "foo") }) { Text(item.foo) }
                Span(attrs = { classes("bar"); attr("data-testid", "bar") }) { Text(item.bar) }
            }
        }
    }
}
