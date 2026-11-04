package com.flide.editor

import java.io.File

data class EditorState(
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabIndex: Int = -1
) {
    val activeTab: EditorTab?
        get() = openTabs.getOrNull(activeTabIndex)
}
