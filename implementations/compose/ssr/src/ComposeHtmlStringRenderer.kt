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
            (it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE, String::class.java, Function2::class.java)) ||
                it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE, String::class.java, java.lang.Boolean.TYPE, Function2::class.java)))
    }
}

private val directRenderer: Method? by lazy {
    runCatching {
        Class.forName("org.jetbrains.compose.web.ComposeHtmlToStringDirectKt").methods.single {
            it.name == "composeHtmlToStringDirect" && it.parameterCount == 4
        }
    }.getOrNull()
}

private val singlePassRenderer: Method? by lazy {
    runCatching {
        Class.forName("org.jetbrains.compose.web.ComposeHtmlToStringSinglePassKt").methods.single {
            it.name == "composeHtmlToStringSinglePass" && it.parameterCount == 4
        }
    }.getOrNull()
}

private val streamingRenderer: Method? by lazy {
    runCatching {
        Class.forName("org.jetbrains.compose.web.ComposeHtmlToStringStreamingKt").methods.single {
            it.name == "composeHtmlToStringStreaming" && it.parameterCount == 4
        }
    }.getOrNull()
}

private val stringRenderingMode: String by lazy {
    val requested = System.getProperty(COMPOSE_HTML_STRING_RENDERING_PROPERTY)
        ?: System.getenv("BENCHMARK_COMPOSE_STRING_RENDERING")
        ?: if (keyedRenderer != null) "keyed" else "unkeyed"
    require(requested in setOf("keyed", "unkeyed", "direct", "single-pass", "streaming")) {
        "BENCHMARK_COMPOSE_STRING_RENDERING must be keyed, unkeyed, direct, single-pass or streaming"
    }
    require(requested != "keyed" || keyedRenderer != null) {
        "Keyed rendering requires a Compose HTML checkout with the composeHtmlToString key parameter"
    }
    require(requested != "direct" || directRenderer != null) {
        "Direct rendering requires composeHtmlToStringDirect"
    }
    require(requested != "single-pass" || singlePassRenderer != null) {
        "Single-pass rendering requires composeHtmlToStringSinglePass"
    }
    require(requested != "streaming" || streamingRenderer != null) {
        "Streaming rendering requires composeHtmlToStringStreaming"
    }
    requested
}

fun composeHtmlStringRenderingMode(): String = stringRenderingMode

internal fun renderComposeHtmlToString(key: String, content: @Composable () -> Unit): String {
    if (stringRenderingMode == "unkeyed") return composeHtmlToString(content = content)
    try {
        val renderer = when (stringRenderingMode) {
            "streaming" -> streamingRenderer!!
            "single-pass" -> singlePassRenderer!!
            "direct" -> directRenderer!!
            else -> keyedRenderer!!
        }
        return if (renderer.parameterCount == 4) {
            val strict = System.getenv("COMPOSE_HTML_VALIDATE_STRICTLY")?.trim()?.lowercase() in setOf("true", "1")
            renderer.invoke(null, true, if (System.getenv("BENCHMARK_REUSE_KEY") == "false") null else key, strict, content) as String
        } else {
            renderer.invoke(null, true, key, content) as String
        }
    } catch (failure: InvocationTargetException) {
        throw failure.targetException
    }
}
