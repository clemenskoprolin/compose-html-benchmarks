package benchmarks.compose

import benchmarks.model.SvgDashboardData
import org.jetbrains.compose.web.composeHtmlToString

fun renderSvgDashboard(data: SvgDashboardData): String = composeHtmlToString { SvgDashboardDocument(data) }
fun renderSvgDashboardBody(data: SvgDashboardData): String = composeHtmlToString { SvgDashboard(data) }
