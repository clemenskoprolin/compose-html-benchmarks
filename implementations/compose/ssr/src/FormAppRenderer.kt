package benchmarks.compose

import benchmarks.model.FormData
import org.jetbrains.compose.web.composeHtmlToString

fun renderFormApp(data: FormData): String = composeHtmlToString { FormAppDocument(data) }
fun renderFormAppBody(data: FormData): String = composeHtmlToString { FormApp(data) }
