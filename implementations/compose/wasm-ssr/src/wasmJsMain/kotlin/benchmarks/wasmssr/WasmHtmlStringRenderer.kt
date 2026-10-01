@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package benchmarks.compose

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.composeHtmlToString

@JsFun("() => process.env.COMPOSE_HTML_VALIDATE_STRICTLY === 'true'")
private external fun strictValidation(): Boolean

@JsFun("() => process.env.BENCHMARK_COMPOSE_STRING_RENDERING === 'unkeyed'")
private external fun unkeyedRendering(): Boolean

internal fun renderComposeHtmlToString(key: String, content: @Composable () -> Unit): String =
    composeHtmlToString(key = if (unkeyedRendering()) null else key, validateStrictly = strictValidation(), content = content)
