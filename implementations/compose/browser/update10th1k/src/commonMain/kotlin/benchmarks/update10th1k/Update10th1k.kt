package benchmarks.update10th1k

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Tr

data class Update10th1kState(val rows: List<Update10th1kRow>)
data class Update10th1kRow(val id: Int, val label: String)

private val adjectives = listOf("pretty", "large", "big", "small", "tall", "short", "long", "handsome", "plain", "quaint", "clean", "elegant", "easy", "angry", "crazy", "helpful", "mushy", "odd", "unsightly", "adorable", "important", "inexpensive", "cheap", "expensive", "fancy")
private val colours = listOf("red", "yellow", "blue", "green", "pink", "brown", "purple", "brown", "white", "black", "orange")
private val nouns = listOf("table", "chair", "house", "bbq", "desk", "car", "pony", "cookie", "sandwich", "burger", "pizza", "mouse", "keyboard")

fun update10th1kFixture() = Update10th1kState(
    (1..1_000).map { id ->
        Update10th1kRow(id, "${adjectives[(id * 17) % adjectives.size]} ${colours[(id * 31) % colours.size]} ${nouns[(id * 47) % nouns.size]}")
    },
)

@Composable
fun Update10th1kTable(initialState: Update10th1kState) {
    var rows by remember(initialState) { mutableStateOf(initialState.rows) }
    var selectedRowId by remember { mutableStateOf<Int?>(null) }
    val update = {
        rows = rows.mapIndexed { index, row -> if (index % 10 == 0) row.copy(label = "${row.label} !!!") else row }
    }

    Div(attrs = { classes("container") }) {
        Table(attrs = { classes("table", "table-hover", "table-striped", "test-data") }) {
            Tbody {
                rows.forEach { row -> key(row.id) {
                    Tr(attrs = { if (selectedRowId == row.id) classes("danger") }) {
                        Td(attrs = { classes("col-md-1") }) { Text(row.id.toString()) }
                        Td(attrs = { classes("col-md-4") }) { A(attrs = { onClick { selectedRowId = row.id } }) { Text(row.label) } }
                        Td(attrs = { classes("col-md-1") }) { A { Span(attrs = { classes("glyphicon", "glyphicon-remove"); attr("aria-hidden", "true") }) } }
                        Td(attrs = { classes("col-md-6") })
                    }
                } }
            }
        }
        Span(attrs = {
            id("benchmark-update")
            classes("preloadicon", "glyphicon", "glyphicon-remove")
            attr("aria-hidden", "true")
            onClick { update() }
        })
    }
}
