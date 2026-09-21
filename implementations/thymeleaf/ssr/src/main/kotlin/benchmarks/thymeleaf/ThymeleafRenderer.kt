package benchmarks.thymeleaf

import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templatemode.TemplateMode

/**
 * Standalone Thymeleaf renderer (no Spring Boot) for SSR benchmarks.
 * Uses classpath templates from this module's resources.
 *
 * Full-document templates (tailwind-catalog, form-app, data-table, svg-dashboard,
 * content-article) are processed in HTML mode and emit a complete <!DOCTYPE html> document.
 *
 * Fragment templates (preact-text, preact-search-results, preact-stack) emit a bare
 * HTML fragment with no wrapping document. They are processed via a fragment selector
 * so Thymeleaf does not add an implicit html/head/body structure.
 */
object ThymeleafRenderer {
    /** Templates that render a full HTML document. */
    private val fullDocumentTemplates = setOf(
        "tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article"
    )

    private val documentEngine: TemplateEngine = TemplateEngine().apply {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            templateMode = TemplateMode.HTML
            characterEncoding = "UTF-8"
            isCacheable = true
        })
    }

    /**
     * Fragment templates use XML mode so Thymeleaf does not inject an implicit
     * html/head/body wrapper around the rendered output.
     */
    private val fragmentEngine: TemplateEngine = TemplateEngine().apply {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            templateMode = TemplateMode.XML
            characterEncoding = "UTF-8"
            isCacheable = true
        })
    }

    fun render(templateName: String, variables: Map<String, Any?>): String {
        val context = Context().apply { setVariables(variables) }
        return if (templateName in fullDocumentTemplates) {
            documentEngine.process(templateName, context)
        } else {
            fragmentEngine.process(templateName, context)
        }
    }
}
