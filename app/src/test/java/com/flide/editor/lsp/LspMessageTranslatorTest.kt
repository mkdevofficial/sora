package com.flide.editor.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LspMessageTranslatorTest {

    private val androidPath = "/data/user/0/com.flide/files/projects/counter_app"
    private val guestPath = "/home/flide/projects/counter_app"

    @Test
    fun testTranslateOutgoing_replacesAndroidPathsWithGuestPaths() {
        val translator = LspMessageTranslator(androidPath, guestPath)
        val outgoingJson = """{"jsonrpc":"2.0","method":"initialize","params":{"rootUri":"file:///data/user/0/com.flide/files/projects/counter_app"}}"""
        val translated = translator.translateOutgoing(outgoingJson)

        assertTrue(translated.contains("file:///home/flide/projects/counter_app"))
        assertFalse(translated.contains("/data/user/0/com.flide"))
    }

    @Test
    fun testTranslateIncoming_replacesGuestPathsWithAndroidPaths() {
        val translator = LspMessageTranslator(androidPath, guestPath)
        val incomingJson = """{"jsonrpc":"2.0","method":"textDocument/publishDiagnostics","params":{"uri":"file:///home/flide/projects/counter_app/lib/main.dart","diagnostics":[]}}"""
        val translated = translator.translateIncoming(incomingJson)

        assertTrue(translated.contains("file:///data/user/0/com.flide/files/projects/counter_app/lib/main.dart"))
        assertFalse(translated.contains("/home/flide/projects"))
    }

    @Test
    fun testReadAndWriteMessage_roundTrip() {
        val outputStream = ByteArrayOutputStream()
        val originalBody = """{"jsonrpc":"2.0","id":1,"result":{"capabilities":{}}}"""

        LspMessageTranslator.writeMessage(outputStream, originalBody)
        val messageBytes = outputStream.toByteArray()

        val inputStream = ByteArrayInputStream(messageBytes)
        val readBody = LspMessageTranslator.readMessage(inputStream)

        assertNotNull(readBody)
        assertEquals(originalBody, readBody)
    }

    @Test
    fun testDetectProgress_indexingNotifications() {
        var progressTitle: String? = null
        var isProgressDone: Boolean? = null

        val translator = LspMessageTranslator(androidPath, guestPath) { title, isDone ->
            progressTitle = title
            isProgressDone = isDone
        }

        val beginJson = """{"jsonrpc":"2.0","method":"$/progress","params":{"token":"1","value":{"kind":"begin","title":"Analyzing"}}}"""
        translator.translateIncoming(beginJson)
        assertEquals("Analyzing", progressTitle)
        assertEquals(false, isProgressDone)

        val endJson = """{"jsonrpc":"2.0","method":"$/progress","params":{"token":"1","value":{"kind":"end"}}}"""
        translator.translateIncoming(endJson)
        assertEquals(true, isProgressDone)
    }
}
