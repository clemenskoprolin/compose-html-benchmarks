package benchmarks.analysis

import benchmarks.jstachio.JStachioRenderer
import benchmarks.jte.JteRenderer
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
                val render = jvmSsrWorkload(target, name, fixture)
                val first = render()
                assertEquals(first, render(), "$target $name subsequent output")
                val writer = StringWriter()
                when (target) {
                    JvmSsrTarget.JSTACHIO -> JStachioRenderer.stream(name, fixture, writer)
                    JvmSsrTarget.JTE -> JteRenderer.stream(name, fixture, writer)
                    else -> error("Unexpected target")
                }
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
        for (target in targets) {
            val html = jvmSsrWorkload(target, "preact-stack", null)()
            assertEquals(10011, Regex("<div>").findAll(html).count())
            assertEquals(10011, Regex("</div>").findAll(html).count())
            assertEquals(10, Regex("data-testid=\"stack\"").findAll(html).count())
        }
    }

    @Test
    fun targetSelectionAndUnsupportedScenariosAreExplicit() {
        assertEquals(targets, selectedJvmSsrTargets("jstachio, jte"))
        assertEquals(listOf(JvmSsrTarget.COMPOSE_JVM), selectedJvmSsrTargets("compose-jvm"))
        assertEquals(JvmSsrTarget.entries, selectedJvmSsrTargets(null))
        for (target in targets) {
            assertFalse(supportsJvmSsrWorkload(target, "raw-text-style"))
            assertFailsWith<IllegalArgumentException> { jvmSsrWorkload(target, "raw-text-style", null) }
        }
    }
}
