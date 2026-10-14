package benchmarks.analysis

import benchmarks.compose.renderFractionalStyle
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class FractionalStyleSsrTest {
    @Test
    fun fractionalStylesAlternateOrdinaryValuesAndPriorityResetWithPropertyRemoval() {
        val html = renderFractionalStyle()
        assertEquals(html, renderFractionalStyle())
        val styled = Jsoup.parseBodyFragment(html).select("div[style]")
        assertEquals(256, styled.size)
        for ((index, element) in styled.withIndex()) {
            val style = element.attr("style")
            assertContains(style, "width:")
            assertContains(style, "height:")
            assertContains(style, "opacity:")
            assertContains(style, "--progress:")
            assertFalse(style.contains("margin-left:"), "Existing property must be removed at $index")
            assertFalse(style.contains("!important"), "Normal assignment must reset priority at $index")
            assertEquals(index.toString(), element.text())
        }
        val first = styled[0].attr("style")
        assertContains(first, "width: 0.125px")
        assertContains(first, "height: 0.03125em")
        assertContains(first, "opacity: 0.03125")
        assertContains(first, "--progress: 0.015625")
        assertContains(first, "line-height: 1.390625")
        assertContains(styled[1].attr("style"), "line-height: 1.65625")
        assertFalse(JvmSsrScenario.FRACTIONAL_STYLE.supportsTemplates)
        for (target in JvmSsrTarget.entries.filter { it != JvmSsrTarget.COMPOSE_JVM }) {
            assertNotNull(skipReason(target, "fractional-style"))
        }
    }
}
