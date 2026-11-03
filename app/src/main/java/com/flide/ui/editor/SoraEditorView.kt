package com.flide.ui.editor

import android.graphics.Typeface
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.flide.editor.EditorController
import com.flide.editor.EditorThemeManager
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Embedded Sora CodeEditor with TextMate syntax highlighting, auto-indent,
 * bracket matching, and debounced auto-save.
 */
@Composable
fun SoraEditorView(
    file: File,
    controller: EditorController,
    onDirtyChanged: (Boolean) -> Unit,
    onFileSaved: (File) -> Unit,
    modifier: Modifier = Modifier,
    autoSaveDebounceMs: Long = 1500L
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var autoSaveJob = remember { mutableMapOf<String, Job?>() }

    LaunchedEffect(Unit) {
        EditorThemeManager.initialize(context)
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            controller.activeEditor = null
            controller.activeFile = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            CodeEditor(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Configure Font and Appearance
                typefaceText = Typeface.MONOSPACE
                textSize = 14f
                setColorScheme(SchemeDarcula())

                // Editor Features
                props.autoIndent = true
                props.symbolPairAutoCompletion = true
                props.useICULibToSelectWords = true
                isLineNumberEnabled = true
                setPinLineNumber(true)

                // Language & Syntax Highlighting
                setEditorLanguage(EditorThemeManager.getLanguageForFile(file))

                // Load File Content
                val initialText = if (file.exists()) {
                    try { file.readText() } catch (e: Exception) { "" }
                } else ""
                setText(initialText)

                controller.activeEditor = this
                controller.activeFile = file

                // Subscribe to Content Changes for Auto-Save
                var isSettingText = false
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    if (isSettingText) return@subscribeEvent

                    onDirtyChanged(true)

                    // Debounced Auto-Save
                    autoSaveJob[file.absolutePath]?.cancel()
                    autoSaveJob[file.absolutePath] = coroutineScope.launch {
                        delay(autoSaveDebounceMs)
                        try {
                            val currentText = text.toString()
                            file.parentFile?.mkdirs()
                            file.writeText(currentText)
                            onDirtyChanged(false)
                            onFileSaved(file)
                        } catch (e: Exception) {
                            Log.e("SoraEditorView", "Auto-save failed for ${file.name}", e)
                        }
                    }
                }
            }
        },
        update = { editor ->
            controller.activeEditor = editor
            controller.activeFile = file

            if (controller.activeFile?.absolutePath != file.absolutePath) {
                editor.setEditorLanguage(EditorThemeManager.getLanguageForFile(file))
                val content = if (file.exists()) {
                    try { file.readText() } catch (e: Exception) { "" }
                } else ""
                editor.setText(content)
            }
        }
    )
}
