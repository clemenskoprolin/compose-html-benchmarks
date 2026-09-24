package benchmarks.compose

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.composeHtmlToString
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

const val COMPOSE_HTML_STRING_RENDERING_PROPERTY = "benchmarks.compose.stringRendering"

// Resolve this at runtime so the benchmark also compiles against older Compose HTML checkouts.
private val keyedRenderer: Method? by lazy {
    Class.forName("org.jetbrains.compose.web.ComposeHtmlToStringKt").methods.singleOrNull {
        it.name == "composeHtmlToString" &&
            it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE, String::class.java, Function2::class.java))
    }
}

private val stringRenderingMode: String by lazy {
    val requested = System.getProperty(COMPOSE_HTML_STRING_RENDERING_PROPERTY)
        ?: System.getenv("BENCHMARK_COMPOSE_STRING_RENDERING")
        ?: if (keyedRenderer != null) "keyed" else "unkeyed"
    require(requested == "keyed" || requested == "unkeyed") {
        "BENCHMARK_COMPOSE_STRING_RENDERING must be keyed or unkeyed"
    }
    require(requested != "keyed" || keyedRenderer != null) {
        "Keyed rendering requires a Compose HTML checkout with the composeHtmlToString key parameter"
    }
    requested
}

fun composeHtmlStringRenderingMode(): String = stringRenderingMode

internal fun renderComposeHtmlToString(key: String, content: @Composable () -> Unit): String {
    if (stringRenderingMode == "unkeyed") return composeHtmlToString(content = content)
    try {
        return keyedRenderer!!.invoke(null, true, key, content) as String
    } catch (failure: InvocationTargetException) {
        throw failure.targetException
    }
}
