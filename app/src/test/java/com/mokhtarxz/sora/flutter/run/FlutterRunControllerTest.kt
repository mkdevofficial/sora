package com.mokhtarxz.sora.flutter.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class FlutterRunControllerTest {

    private val ansiRegex = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")

    @Test
    fun testStripAnsi_removesEscapeSequences() {
        val colored = "\u001B[32mFlutter Web Server is running at http://127.0.0.1:8080\u001B[0m"
        val stripped = colored.replace(ansiRegex, "")
        assertEquals("Flutter Web Server is running at http://127.0.0.1:8080", stripped)
    }

    @Test
    fun testWebServerUrlPattern_matchesStandardOutputs() {
        val port = 8080
        val pattern = Pattern.compile("https?://(?:127\\.0\\.0\\.1|localhost):$port(?:/[^\\s]*)?")

        val line1 = "Flutter Web Server is running at http://127.0.0.1:8080"
        val matcher1 = pattern.matcher(line1)
        assertTrue(matcher1.find())
        assertEquals("http://127.0.0.1:8080", matcher1.group(0))

        val line2 = "Serving at http://localhost:8080"
        val matcher2 = pattern.matcher(line2)
        assertTrue(matcher2.find())
        assertEquals("http://localhost:8080", matcher2.group(0))

        val line3 = "Web server available at http://127.0.0.1:8080/"
        val matcher3 = pattern.matcher(line3)
        assertTrue(matcher3.find())
        assertEquals("http://127.0.0.1:8080/", matcher3.group(0))

        // VM Service on different port should not match web port
        val vmServiceLine = "The Dart VM service is available at: http://127.0.0.1:34567/ws"
        val matcherVm = pattern.matcher(vmServiceLine)
        assertFalse(matcherVm.find())
    }

    @Test
    fun testHotReloadPattern_detectsReloadCompletion() {
        val line = "Reloaded 1 of 572 libraries in 241ms."
        assertTrue(line.contains("Reloaded") && line.contains("in") && line.contains("ms"))
    }

    @Test
    fun testHotRestartPattern_detectsRestartCompletion() {
        val line = "Restarted application in 1,234ms."
        assertTrue(line.contains("Restarted application") || line.contains("Restarted in"))
    }
}
