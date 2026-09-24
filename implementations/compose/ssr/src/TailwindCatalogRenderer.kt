package benchmarks.compose

import benchmarks.model.CatalogData

fun renderTailwindCatalog(data: CatalogData): String =
    renderComposeHtmlToString("tailwind-catalog") { TailwindCatalogDocument(data) }

fun renderTailwindCatalogBody(data: CatalogData): String =
    renderComposeHtmlToString("tailwind-catalog-body") { TailwindCatalog(data) }
