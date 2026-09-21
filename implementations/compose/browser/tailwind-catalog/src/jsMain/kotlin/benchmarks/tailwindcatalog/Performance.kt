package benchmarks.tailwindcatalog
import kotlinx.browser.window
internal actual fun markPerformance(name: String) { window.performance.asDynamic().mark(name) }
