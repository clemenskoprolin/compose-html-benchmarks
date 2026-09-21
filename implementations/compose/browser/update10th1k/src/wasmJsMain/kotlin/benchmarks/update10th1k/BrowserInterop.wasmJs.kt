@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package benchmarks.update10th1k

import kotlinx.browser.JsAny
import kotlinx.browser.JsArray
import kotlinx.browser.JsString
import kotlinx.browser.toList
import kotlin.js.toJsString

private external interface WasmUpdate10th1kState : JsAny {
    val rows: JsArray<WasmUpdate10th1kRow>
}

private external interface WasmUpdate10th1kRow : JsAny {
    val id: Int
    val label: JsString
}

@JsFun("(input) => JSON.parse(input)")
private external fun parseUpdate10th1kState(input: JsString): WasmUpdate10th1kState

@JsFun("(name) => performance.mark(name)")
private external fun performanceMark(name: JsString)

internal actual fun update10th1kStateFromJson(serialized: String): Update10th1kState {
    val value = parseUpdate10th1kState(serialized.toJsString())
    return Update10th1kState(
        value.rows.toList().map { row -> Update10th1kRow(row.id, row.label.toString()) },
    )
}

internal actual fun markPerformance(name: String) {
    performanceMark(name.toJsString())
}
