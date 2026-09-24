package benchmarks.compose

import benchmarks.model.FormData

fun renderFormApp(data: FormData): String =
    renderComposeHtmlToString("form-app") { FormAppDocument(data) }

fun renderFormAppBody(data: FormData): String =
    renderComposeHtmlToString("form-app-body") { FormApp(data) }
