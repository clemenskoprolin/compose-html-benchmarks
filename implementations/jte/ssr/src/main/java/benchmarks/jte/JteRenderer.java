package benchmarks.jte;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.output.WriterOutput;
import java.io.Writer;

/** Uses only templates compiled by Gradle; no template parsing or compilation at runtime. */
public final class JteRenderer {
    private static final TemplateEngine ENGINE = TemplateEngine.createPrecompiled(ContentType.Html);

    private JteRenderer() {}

    private static String template(String scenario) {
        return scenario + ".jte";
    }

    public static String render(String scenario, Object fixture) {
        var output = new StringOutput();
        ENGINE.render(template(scenario), fixture, output);
        return output.toString();
    }

    public static void stream(String scenario, Object fixture, Writer writer) {
        ENGINE.render(template(scenario), fixture, new WriterOutput(writer));
    }
}
