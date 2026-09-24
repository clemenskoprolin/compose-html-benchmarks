package benchmarks.compose

import benchmarks.model.ArticleData

fun renderContentArticle(data: ArticleData): String =
    renderComposeHtmlToString("content-article") { ContentArticleDocument(data) }

fun renderContentArticleBody(data: ArticleData): String =
    renderComposeHtmlToString("content-article-body") { ContentArticle(data) }
