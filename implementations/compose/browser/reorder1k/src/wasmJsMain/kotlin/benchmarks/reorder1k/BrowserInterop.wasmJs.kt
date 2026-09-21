@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package benchmarks.reorder1k

import kotlinx.browser.JsAny
import kotlinx.browser.JsArray
import kotlinx.browser.JsString
import kotlinx.browser.toList
import kotlin.js.toJsString

private external interface WasmReorder1kState : JsAny {
    val rows: JsArray<WasmReorder1kRow>
}

private external interface WasmReorder1kRow : JsAny {
    val id: Int
    val label: JsString
}

@JsFun("(input) => JSON.parse(input)")
private external fun parseReorder1kState(input: JsString): WasmReorder1kState

@JsFun("(name) => performance.mark(name)")
private external fun performanceMark(name: JsString)

internal actual fun reorder1kStateFromJson(serialized: String): Reorder1kState {
    val value = parseReorder1kState(serialized.toJsString())
    return Reorder1kState(
        value.rows.toList().map { row -> Reorder1kRow(row.id, row.label.toString()) },
    )
}

internal actual fun markPerformance(name: String) {
    performanceMark(name.toJsString())
}
