package benchmarks.analysis

import benchmarks.model.*
import org.jsoup.Jsoup
import java.io.StringWriter
import kotlin.test.*

class JvmTemplateRenderersTest {
    private val targets = listOf(JvmSsrTarget.JSTACHIO, JvmSsrTarget.JTE)

    @Test
    fun allSharedWorkloadsRenderDeterministicallyAndStreamTheSameHtml() {
        for (name in templateWorkloads) {
            val fixture = workloadFixture(name)
            for (target in targets) {
                if (skipReason(target, name) != null) continue
                val render = jvmSsrWorkload(target, name, fixture)
                val first = render()
                assertEquals(first, render(), "$target $name subsequent output")
                val writer = StringWriter()
                streamJvmSsrWorkload(target, name, fixture, writer)
                assertEquals(first, writer.toString(), "$target $name streaming output")
                assertTrue(first.isNotEmpty())
            }
        }
    }

    @Test
    fun renderersUseTheSuppliedFixtureAndEscapeTextAndAttributes() {
        val title = "<script>\"quoted\" & 'single' 😀</script>"
        val data = catalogFixture().copy(title = title)
        for (target in targets) {
            val html = jvmSsrWorkload(target, "tailwind-catalog", data)()
            val document = Jsoup.parse(html)
            assertEquals(title, document.selectFirst("h1")!!.text(), "$target text escaping")
            assertTrue(document.select("script").isEmpty())
            val other = jvmSsrWorkload(target, "tailwind-catalog", data.copy(title = "different"))()
            assertEquals("different", Jsoup.parse(other).selectFirst("h1")!!.text())
        }
    }

    @Test
    fun formVariantsPreserveOptionalAndBooleanAttributesAndRadioOptions() {
        val base = formFixture().sections.first().fields.first()
        val value = "<>&\"' 😀"
        val fields = listOf(
            base.copy(id = "text", value = value, placeholder = "", required = true, disabled = true,
                readOnly = true, minLength = 0, maxLength = 20, pattern = value, helpText = "", errorMessage = value),
            base.copy(id = "textarea", type = FormFieldType.TEXTAREA, value = value),
            base.copy(id = "checkbox", type = FormFieldType.CHECKBOX, checked = true),
            base.copy(id = "select", type = FormFieldType.SELECT, multiple = true,
                options = listOf(SelectOption(value, value, true, true))),
            base.copy(id = "radio", type = FormFieldType.RADIO,
                options = listOf(SelectOption(value, value, true, false), SelectOption("two", "Two", false, true))),
        )
        val fixture = formFixture().copy(isSubmitting = true,
            sections = listOf(FormSection("test", "Test", fields)))
        for (target in targets) {
            val document = Jsoup.parse(jvmSsrWorkload(target, "form-app", fixture)())
            val input = document.getElementById("text")!!
            assertEquals(value, input.attr("value"))
            assertTrue(input.hasAttr("required") && input.hasAttr("disabled") && input.hasAttr("readonly"))
            assertTrue(input.hasAttr("placeholder"))
            assertEquals("", input.attr("placeholder"))
            assertEquals("0", input.attr("minlength"))
            assertEquals(value, input.attr("pattern"))
            assertEquals("help-text error-text", input.attr("aria-describedby"))
            assertEquals(value, document.getElementById("textarea")!!.text())
            assertTrue(document.getElementById("checkbox")!!.hasAttr("checked"))
            assertTrue(document.getElementById("select")!!.hasAttr("multiple"))
            assertEquals(value, document.selectFirst("#select option")!!.attr("value"))
            assertEquals(2, document.select("input[type=radio]").size)
            assertEquals("radio-opt-1", document.select("input[type=radio]")[1].id())
            assertTrue(document.selectFirst("button[type=submit]")!!.hasAttr("disabled"))
        }
    }

    @Test
    fun tableAriaBooleansAreTextValuesAndAttributeContentIsEscaped() {
        val text = "<>&\"' 😀"
        val fixture = dataTableFixture().copy(searchQuery = text, activeFilters = mapOf(text to text))
        for (target in targets) {
            val document = Jsoup.parse(jvmSsrWorkload(target, "data-table", fixture)())
            assertEquals(text, document.getElementById("table-search")!!.attr("value"))
            val rows = document.select("tbody tr")
            assertEquals("false", rows[0].attr("aria-selected"))
            assertEquals("true", rows[9].attr("aria-selected"))
            assertEquals(text, document.selectFirst(".filter-badge")!!.attr("data-filter-key"))
        }
    }

    @Test
    fun deepStackHasTenCompleteBranches() {
        assertNotNull(skipReason(JvmSsrTarget.JSTACHIO, "preact-stack"))
        assertFailsWith<IllegalStateException> {
            jvmSsrWorkload(JvmSsrTarget.JSTACHIO, "preact-stack", null)
        }
        for (target in targets.filter { skipReason(it, "preact-stack") == null }) {
            val html = jvmSsrWorkload(target, "preact-stack", null)()
            assertEquals(10011, Regex("<div>").findAll(html).count())
            assertEquals(10011, Regex("</div>").findAll(html).count())
            assertEquals(10, Regex("data-testid=\"stack\"").findAll(html).count())
        }
    }

    @Test
    fun dynamicTextUsesRuntimeValuesAndStreamsTheSameEscapedHtmlForEveryJvmRenderer() {
        val fixture = TextData(listOf(
            TextPair("<script>\"quoted\" & 'single' 😀 café α</script>", "second > first & é"),
            TextPair("another row", "different text"),
        ))
        for (name in listOf("dynamic-text", "dynamic-text-escaped")) {
            for (target in JvmSsrTarget.entries) {
                val html = jvmSsrWorkload(target, name, fixture)()
                val document = Jsoup.parse(html)
                assertTrue(document.select("script").isEmpty(), "$target $name unsafe text")
                assertEquals(fixture.items.map { it.foo }, document.select("span.foo").map { it.wholeText() })
                assertEquals(fixture.items.map { it.bar }, document.select("span.bar").map { it.wholeText() })
                val changed = fixture.copy(items = listOf(TextPair("changed", "updated")))
                assertEquals("changed", Jsoup.parse(jvmSsrWorkload(target, name, changed)())
                    .selectFirst("span.foo")!!.text())
                val counter = StreamingCounter(0, collectHtml = true)
                val sink = BufferedUtf8Sink(counter, 7) // Also split multibyte code points between byte deliveries.
                streamJvmSsrWorkload(target, name, fixture, sink.writer, chunkSize = 16)
                sink.finish()
                assertEquals(html, counter.html(), "$target $name UTF-8 output")
                assertEquals(html.toByteArray(Charsets.UTF_8).size, counter.bytes)
            }
        }
    }

    @Test
    fun targetSelectionAndUnsupportedScenariosAreExplicit() {
        assertEquals(targets, selectedJvmSsrTargets("jstachio, jte"))
        assertEquals(listOf(JvmSsrTarget.COMPOSE_JVM), selectedJvmSsrTargets("compose"))
        assertEquals(JvmSsrTarget.entries, selectedJvmSsrTargets(null))
        for (target in targets) {
            assertNotNull(skipReason(target, "raw-text-style"))
            assertFailsWith<IllegalStateException> { jvmSsrWorkload(target, "raw-text-style", null) }
        }
    }
}
