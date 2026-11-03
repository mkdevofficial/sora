package com.flide.editor

import java.io.File

/**
 * Represents an open document tab in the editor.
 */
data class EditorTab(
    val file: File,
    val isDirty: Boolean = false,
    val title: String = file.name
)
