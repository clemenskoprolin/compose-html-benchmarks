package benchmarks.jstachio;

import benchmarks.model.*;
import java.io.IOException;
import java.io.Writer;

/** Calls the generated renderers directly, without runtime template discovery or reflection. */
public final class JStachioRenderer {
    private JStachioRenderer() {}

    public static String render(String scenario, Object fixture) {
        var output = new StringBuilder();
        try { renderTo(scenario, fixture, output); }
        catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        return output.toString();
    }

    public static void stream(String scenario, Object fixture, Writer writer) throws IOException {
        renderTo(scenario, fixture, writer);
    }

    private static void renderTo(String scenario, Object fixture, Appendable output) throws IOException {
        switch (scenario) {
            case "tailwind-catalog" -> CatalogTemplate.of().execute(new Views.Catalog((CatalogData) fixture), output);
            case "form-app" -> FormTemplate.of().execute(new Views.Form((FormData) fixture), output);
            case "data-table" -> TableTemplate.of().execute(new Views.Table((DataTableData) fixture), output);
            case "svg-dashboard" -> SvgTemplate.of().execute(new Views.Svg((SvgDashboardData) fixture), output);
            case "content-article" -> ArticleTemplate.of().execute(new Views.Article((ArticleData) fixture), output);
            case "preact-search-results" -> SearchTemplate.of().execute(new Views.Search((PreactSearchResultsData) fixture), output);
            case "preact-text" -> TextTemplate.of().execute(new Views.Text(), output);
            case "dynamic-text" -> DynamicTextTemplate.of().execute(new Views.DynamicText((TextData) fixture), output);
            default -> throw new IllegalArgumentException("Unknown JStachio workload: " + scenario);
        }
    }
}
