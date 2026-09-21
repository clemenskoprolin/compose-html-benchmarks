@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package benchmarks.hydrate1k

import kotlinx.browser.JsAny
import kotlinx.browser.JsArray
import kotlinx.browser.JsString
import kotlinx.browser.toList
import kotlin.js.toJsString

private external interface WasmHydrate1kState : JsAny {
    val rows: JsArray<WasmHydrate1kRow>
}

private external interface WasmHydrate1kRow : JsAny {
    val id: Int
    val label: JsString
}

@JsFun("(input) => JSON.parse(input)")
private external fun parseHydrate1kState(input: JsString): WasmHydrate1kState

@JsFun("(name) => performance.mark(name)")
private external fun performanceMark(name: JsString)

internal actual fun hydrate1kStateFromJson(serialized: String): Hydrate1kState {
    val value = parseHydrate1kState(serialized.toJsString())
    return Hydrate1kState(
        value.rows.toList().map { row -> Hydrate1kRow(row.id, row.label.toString()) },
    )
}

internal actual fun markPerformance(name: String) {
    performanceMark(name.toJsString())
}
