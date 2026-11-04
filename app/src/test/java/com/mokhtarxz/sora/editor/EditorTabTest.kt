package com.mokhtarxz.sora.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorTabTest {

    @Test
    fun testEditorTab_initialState() {
        val file = File("/home/flide/projects/my_app/lib/main.dart")
        val tab = EditorTab(file = file)

        assertEquals("main.dart", tab.title)
        assertEquals(file, tab.file)
        assertFalse(tab.isDirty)
    }

    @Test
    fun testEditorTab_dirtyStateChange() {
        val file = File("/home/flide/projects/my_app/pubspec.yaml")
        val tab = EditorTab(file = file, isDirty = false)
        val modifiedTab = tab.copy(isDirty = true)

        assertTrue(modifiedTab.isDirty)
        assertEquals("pubspec.yaml", modifiedTab.title)
    }
}
