package benchmarks.compose

import benchmarks.model.CatalogData
import org.jetbrains.compose.web.composeHtmlToString

fun renderTailwindCatalog(data: CatalogData): String = composeHtmlToString { TailwindCatalogDocument(data) }
fun renderTailwindCatalogBody(data: CatalogData): String = composeHtmlToString { TailwindCatalog(data) }
