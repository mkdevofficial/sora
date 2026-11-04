package com.mokhtarxz.sora.editor

data class EditorConfig(
    val fontSizeSp: Float = 14f,
    val wordWrap: Boolean = false,
    val autoSave: Boolean = true,
    val autoSaveDebounceMs: Long = 1000L,
    val showLineNumbers: Boolean = true,
    val tabSize: Int = 2
)
