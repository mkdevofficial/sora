package com.flide.editor

import android.util.Log
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import java.io.File

/**
 * Controller for programmatic operations on the active Sora CodeEditor instance.
 */
class EditorController {

    var activeEditor: CodeEditor? = null
    var activeFile: File? = null

    fun undo() {
        try {
            activeEditor?.undo()
        } catch (e: Exception) {
            Log.w(TAG, "Undo error: ${e.message}")
        }
    }

    fun redo() {
        try {
            activeEditor?.redo()
        } catch (e: Exception) {
            Log.w(TAG, "Redo error: ${e.message}")
        }
    }

    fun canUndo(): Boolean = activeEditor?.canUndo() ?: false
    fun canRedo(): Boolean = activeEditor?.canRedo() ?: false

    /**
     * Inserts text or symbol at the current cursor location.
     */
    fun insertSymbol(symbol: String) {
        val editor = activeEditor ?: return
        try {
            val cursor = editor.cursor
            editor.text.insert(cursor.leftLine, cursor.leftColumn, symbol)
        } catch (e: Exception) {
            Log.w(TAG, "Insert symbol error: ${e.message}")
        }
    }

    /**
     * Navigates cursor to 1-based line number.
     */
    fun gotoLine(line: Int) {
        val editor = activeEditor ?: return
        try {
            val totalLines = editor.lineCount
            val targetLine = (line - 1).coerceIn(0, (totalLines - 1).coerceAtLeast(0))
            editor.setSelection(targetLine, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Goto line error: ${e.message}")
        }
    }

    /**
     * Searches document for query text.
     */
    fun search(query: String, matchCase: Boolean = false, regex: Boolean = false) {
        val editor = activeEditor ?: return
        if (query.isEmpty()) {
            stopSearch()
            return
        }
        try {
            val searcher = editor.searcher
            val type = if (regex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION else EditorSearcher.SearchOptions.TYPE_NORMAL
            searcher.search(query, EditorSearcher.SearchOptions(type, !matchCase))
            searcher.gotoNext()
        } catch (e: Exception) {
            Log.w(TAG, "Search error: ${e.message}")
        }
    }

    fun findNext() {
        try {
            activeEditor?.searcher?.gotoNext()
        } catch (e: Exception) {
            Log.w(TAG, "findNext error: ${e.message}")
        }
    }

    fun findPrevious() {
        try {
            activeEditor?.searcher?.gotoPrevious()
        } catch (e: Exception) {
            Log.w(TAG, "findPrevious error: ${e.message}")
        }
    }

    fun replace(replacement: String) {
        try {
            activeEditor?.searcher?.replaceThis(replacement)
        } catch (t: Throwable) {
            try {
                activeEditor?.searcher?.replaceCurrentMatch(replacement)
            } catch (e: Exception) {
                Log.w(TAG, "replace error: ${e.message}")
            }
        }
    }

    fun replaceAll(replacement: String) {
        try {
            activeEditor?.searcher?.replaceAll(replacement)
        } catch (e: Exception) {
            Log.w(TAG, "replaceAll error: ${e.message}")
        }
    }

    fun stopSearch() {
        try {
            activeEditor?.searcher?.stopSearch()
        } catch (e: Exception) {
            Log.w(TAG, "stopSearch error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "EditorController"
    }
}
