package benchmarks.compose

import androidx.compose.runtime.Composable
import benchmarks.model.PreactSearchResultsData
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.composeHtmlToString
import org.jetbrains.compose.web.dom.*

fun renderPreactSearchResults(data: PreactSearchResultsData): String = composeHtmlToString {
    Div(attrs = { classes("search-results") }) {
        Div {
            data.items.forEach { item ->
                Div(attrs = { classes("search-results-item") }) {
                    H2 { Text(item.title) }
                    Div(attrs = { classes("lvpic", "pic", "img", "left") }) {
                        Div(attrs = { classes("lvpicinner", "full-width", "picW") }) {
                            A(href = "/buy/${item.id}", attrs = { classes("img", "imgWr2") }) {
                                Img(src = item.image, alt = item.title, attrs = { attr("loading", "lazy") })
                            }
                        }
                    }
                    Span(attrs = { classes("price") }) { Text(item.price) }
                    Button(attrs = { attr("class", "buy-now"); attr("type", "button") }) { Text("Buy now!") }
                }
            }
        }
        PreactSearchFooter(data)
    }
}

@Composable
private fun PreactSearchFooter(data: PreactSearchResultsData) {
    Footer(attrs = { id("glbfooter"); attr("role", "contentinfo"); classes("gh-w") }) {
        Div {
            Div(attrs = { id("rtm_html_1650") }) {
                Div(attrs = { id("rtm_html_1651") })
                H2(attrs = { classes("gh-ar-hdn") }) { Text("Additional site navigation") }
                Div(attrs = { id("gf-BIG"); classes("gffoot") }) {
                    Table(attrs = { classes("gf-t") }) {
                        Tbody {
                            Tr {
                                data.footerSections.forEach { section ->
                                    Td {
                                        Ul {
                                            Li(attrs = { classes("gf-li") }) {
                                                H3(attrs = { classes("gf-bttl") }) {
                                                    if (section.headingHref == null) {
                                                        Text(section.title)
                                                    } else {
                                                        A(href = section.headingHref, attrs = { classes("gf-bttl", "thrd") }) {
                                                            Text(section.title)
                                                        }
                                                    }
                                                }
                                            }
                                            section.links.forEach { link ->
                                                Li(attrs = { classes("gf-li") }) {
                                                    A(href = link.href, attrs = { classes("thrd") }) { Text(link.label) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
