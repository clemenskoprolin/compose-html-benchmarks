@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package benchmarks.filterlist

import kotlinx.browser.JsAny
import kotlinx.browser.JsArray
import kotlinx.browser.toList
import kotlin.js.toJsString

private external interface WasmFilterListState : JsAny {
    val items: JsArray<JsNumber>
}

@JsFun("(input) => JSON.parse(input)")
private external fun parseFilterListState(input: JsString): WasmFilterListState

@JsFun("(name) => performance.mark(name)")
private external fun performanceMark(name: JsString)

internal actual fun filterListStateFromJson(serialized: String): FilterListState {
    val value = parseFilterListState(serialized.toJsString())
    return FilterListState(value.items.toList().map { it.toInt() })
}

internal actual fun markPerformance(name: String) {
    performanceMark(name.toJsString())
}
