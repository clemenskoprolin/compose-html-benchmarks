package benchmarks.filterlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.dom.Article
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

// Adapted from preactjs/benchmarks apps/filter-list (MIT, commit ec93e1b).

private const val COUNT = 1000
private const val RANGE_START = 20
private const val RANGE_END = 600

data class FilterListState(val items: List<Int>)

fun filterListInitialItems(): List<Int> = List(COUNT) { it }

fun filterListFixture() = FilterListState(filterListInitialItems())

fun filterListNextItems(current: List<Int>): List<Int> {
    val currentSet = current.toHashSet()
    return filterListInitialItems().filter { id ->
        val isVisible = id in currentSet
        if (id in RANGE_START..RANGE_END) !isVisible else isVisible
    }
}

@Composable
fun FilterListApp(initialState: FilterListState) {
    var items by remember(initialState) { mutableStateOf(initialState.items) }

    Div {
        Div(attrs = { classes("items") }) {
            items.forEach { id ->
                key(id) {
                    Article { Text(id.toString()) }
                }
            }
        }
    }

    // Expose update function via a hidden trigger element (clicked by the benchmark harness).
    // We reuse the "#benchmark-filter" id convention analogous to "#benchmark-update"/
    // "#benchmark-reorder" in the other client-side benchmarks.
    org.jetbrains.compose.web.dom.Span(attrs = {
        id("benchmark-filter")
        classes("preloadicon")
        attr("aria-hidden", "true")
        onClick {
            items = filterListNextItems(items)
        }
    })
}
