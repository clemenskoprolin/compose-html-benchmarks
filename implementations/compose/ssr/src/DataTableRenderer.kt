package benchmarks.compose

import benchmarks.model.DataTableData

fun renderDataTable(data: DataTableData): String =
    renderComposeHtmlToString("data-table") { DataTableDocument(data) }

fun renderDataTableBody(data: DataTableData): String =
    renderComposeHtmlToString("data-table-body") { DataTable(data) }
