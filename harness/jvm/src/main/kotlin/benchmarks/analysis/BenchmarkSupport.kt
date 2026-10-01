package benchmarks.analysis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.lang.management.ManagementFactory

internal val benchmarkJson = jacksonObjectMapper()

internal fun count(name: String, default: Int, minimum: Int = 1): Int =
    (System.getenv(name)?.toIntOrNull() ?: if (System.getenv(name) == null) default else error("Invalid $name"))
        .also { require(it >= minimum) { "$name must be >= $minimum" } }

internal fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
}

internal class ThreadAllocation {
    private val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    val supported: Boolean get() = bean?.isThreadAllocatedMemorySupported == true

    init {
        if (bean?.isThreadAllocatedMemorySupported == true && !bean.isThreadAllocatedMemoryEnabled) {
            bean.isThreadAllocatedMemoryEnabled = true
        }
    }

    // Keep this primitive: boxing a snapshot would itself be counted as SSR allocation.
    fun read(): Long = if (supported)
        bean!!.getThreadAllocatedBytes(Thread.currentThread().threadId()) else -1L
}

// Launch and decode a fresh worker; measurement stays entirely inside its entry point.
internal inline fun <reified T> runJvmWorker(
    mainClass: String,
    arguments: List<String>,
    output: File,
    failureMessage: String,
    jvmArguments: List<String> = emptyList(),
): T {
    val java = File(System.getProperty("java.home"), "bin/java").absolutePath
    val command = listOf(java, "-Xss16m") + jvmArguments +
        listOf("-cp", System.getProperty("java.class.path"), mainClass, "--worker") +
        arguments + output.absolutePath
    val process = ProcessBuilder(command).inheritIO().start()
    check(process.waitFor() == 0) { failureMessage }
    val result = benchmarkJson.readValue<T>(output)
    output.delete()
    return result
}
