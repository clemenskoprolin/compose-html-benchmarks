package benchmarks.compose

import benchmarks.model.DataTableData
import org.jetbrains.compose.web.composeHtmlToString

fun renderDataTable(data: DataTableData): String = composeHtmlToString { DataTableDocument(data) }
fun renderDataTableBody(data: DataTableData): String = composeHtmlToString { DataTable(data) }
