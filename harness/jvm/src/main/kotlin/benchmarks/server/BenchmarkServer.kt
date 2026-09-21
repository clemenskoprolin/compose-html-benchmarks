// Standalone JVM HTTP server for serving Compose SSR responses during local previewing.
package benchmarks.server

import benchmarks.compose.*
import benchmarks.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main(args: Array<String>) {
    val port = System.getenv("COMPOSE_PORT")?.toIntOrNull() ?: 8080
    val server = BenchmarkServer(port)
    server.start()
    println("Compose HTML Benchmark Server listening on http://127.0.0.1:$port")
}

class BenchmarkServer(private val port: Int) {
    private val mapper = ObjectMapper().registerKotlinModule()
    private var httpServer: HttpServer? = null

    private val fixtures = mapOf(
        "tailwind-catalog" to catalogFixture(),
        "form-app" to formFixture(),
        "data-table" to dataTableFixture(),
        "svg-dashboard" to svgDashboardFixture(),
        "content-article" to articleFixture()
    )

    fun start() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.executor = Executors.newFixedThreadPool(8)

        // Health check
        server.createContext("/health") { exchange ->
            sendResponse(exchange, 200, "text/plain", "OK".toByteArray())
        }

        // Fixtures API
        server.createContext("/api/fixtures/") { exchange ->
            val scenario = exchange.requestURI.path.substringAfter("/api/fixtures/").trim('/')
            val data = fixtures[scenario]
            if (data != null) {
                val json = mapper.writeValueAsBytes(data)
                sendResponse(exchange, 200, "application/json", json)
            } else {
                sendResponse(exchange, 404, "text/plain", "Scenario not found".toByteArray())
            }
        }

        // Compose SSR rendering endpoint: /compose/:scenario
        server.createContext("/compose/") { exchange ->
            val scenario = exchange.requestURI.path.substringAfter("/compose/").trim('/')
            val html = renderComposeScenario(scenario)
            if (html != null) {
                val bytes = html.toByteArray(Charsets.UTF_8)
                sendResponse(exchange, 200, "text/html; charset=UTF-8", bytes)
            } else {
                sendResponse(exchange, 404, "text/plain", "Scenario not found: $scenario".toByteArray())
            }
        }

        // Static files (client bundles, css)
        server.createContext("/static/") { exchange ->
            val path = exchange.requestURI.path.substringAfter("/static/")
            val file = File("static", path)
            if (file.exists() && file.isFile) {
                val contentType = when {
                    path.endsWith(".js") || path.endsWith(".mjs") -> "text/javascript"
                    path.endsWith(".wasm") -> "application/wasm"
                    path.endsWith(".css") -> "text/css"
                    else -> "application/octet-stream"
                }
                sendResponse(exchange, 200, contentType, file.readBytes())
            } else {
                sendResponse(exchange, 404, "text/plain", "Static file not found".toByteArray())
            }
        }

        server.start()
        httpServer = server
    }

    fun stop() {
        httpServer?.stop(0)
    }

    private fun renderComposeScenario(scenario: String): String? {
        val (renderedHtml, stateData) = when (scenario) {
            "tailwind-catalog" -> renderTailwindCatalog(catalogFixture()) to catalogFixture()
            "form-app" -> renderFormApp(formFixture()) to formFixture()
            "data-table" -> renderDataTable(dataTableFixture()) to dataTableFixture()
            "svg-dashboard" -> renderSvgDashboard(svgDashboardFixture()) to svgDashboardFixture()
            "content-article" -> renderContentArticle(articleFixture()) to articleFixture()
            else -> return null
        }

        val jsonState = mapper.writeValueAsString(stateData)
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")
            .replace("&", "\\u0026")

        // Inject hydration markers, state script, and client bundle script
        return """<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Compose HTML Benchmark — $scenario</title>
  <link rel="stylesheet" href="/styles.css">
</head>
<body data-scenario="$scenario" data-framework="compose" data-app-ready="true">
  <script id="initial-state" type="application/json">$jsonState</script>
  <div id="app-root">$renderedHtml</div>
  <script>window.__initialFirstElement = document.querySelector("#app-root")?.firstElementChild;</script>
  <script type="module" src="/static/client.js"></script>
</body>
</html>"""
    }

    private fun sendResponse(exchange: HttpExchange, code: Int, contentType: String, bytes: ByteArray) {
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
