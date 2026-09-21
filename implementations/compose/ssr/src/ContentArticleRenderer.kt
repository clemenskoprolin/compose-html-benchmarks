package benchmarks.compose

import benchmarks.model.ArticleData
import org.jetbrains.compose.web.composeHtmlToString

fun renderContentArticle(data: ArticleData): String = composeHtmlToString { ContentArticleDocument(data) }
fun renderContentArticleBody(data: ArticleData): String = composeHtmlToString { ContentArticle(data) }
