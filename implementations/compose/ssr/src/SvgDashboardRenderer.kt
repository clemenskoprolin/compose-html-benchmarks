package benchmarks.compose

import benchmarks.model.SvgDashboardData

fun renderSvgDashboard(data: SvgDashboardData): String =
    renderComposeHtmlToString("svg-dashboard") { SvgDashboardDocument(data) }

fun renderSvgDashboardBody(data: SvgDashboardData): String =
    renderComposeHtmlToString("svg-dashboard-body") { SvgDashboard(data) }
