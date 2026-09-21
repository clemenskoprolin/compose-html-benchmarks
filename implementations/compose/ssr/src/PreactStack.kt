package benchmarks.compose

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.composeHtmlToString
import org.jetbrains.compose.web.dom.*

fun renderPreactStack(): String = composeHtmlToString {
    Div { repeat(10) { PreactStackBranch(1_000) } }
}

@Composable
private fun PreactStackBranch(depth: Int) {
    if (depth == 0) {
        Div { Span(attrs = { classes("foo"); attr("data-testid", "stack") }) { Text("deep stack") } }
    } else {
        Div { PreactStackBranch(depth - 1) }
    }
}
