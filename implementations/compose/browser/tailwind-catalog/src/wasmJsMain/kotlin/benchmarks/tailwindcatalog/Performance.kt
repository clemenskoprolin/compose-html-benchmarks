@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package benchmarks.tailwindcatalog
import kotlin.js.JsString
import kotlin.js.toJsString
@JsFun("(name) => performance.mark(name)")
private external fun performanceMark(name: JsString)
internal actual fun markPerformance(name: String) { performanceMark(name.toJsString()) }
