package com.flide.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * Implements Termux TerminalSessionClient and TerminalViewClient
 * to bridge the native terminal emulator with Android and Jetpack Compose.
 */
class FlideTerminalClient(
    private val context: Context,
    var terminalView: TerminalView? = null,
    var onTitleChange: ((String) -> Unit)? = null,
    var onSessionFinish: ((TerminalSession) -> Unit)? = null
) : TerminalSessionClient, TerminalViewClient {

    var isCtrlActive: Boolean = false
    var isAltActive: Boolean = false

    // --- TerminalSessionClient ---

    override fun onTextChanged(session: TerminalSession) {
        terminalView?.onScreenUpdated()
    }

    override fun onTitleChanged(session: TerminalSession) {
        session.title?.let { onTitleChange?.invoke(it) }
    }

    override fun onSessionFinished(session: TerminalSession) {
        onSessionFinish?.invoke(session)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("terminal", text)
            clipboard?.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.w(TAG, "Failed copying to clipboard", e)
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            val text = item?.text?.toString()
            if (!text.isNullOrEmpty()) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                session.write(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed pasting from clipboard", e)
        }
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int? = null

    // --- TerminalViewClient ---

    override fun onScale(scale: Float): Float {
        val tv = terminalView ?: return scale
        if (scale < 0.9f || scale > 1.1f) {
            val change = if (scale > 1.0f) 2 else -2
            val current = tv.textSize
            val newSize = (current + change).coerceIn(16, 80)
            tv.setTextSize(newSize)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        terminalView?.let { tv ->
            tv.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(e: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean {
        val ctrl = isCtrlActive
        if (ctrl) {
            isCtrlActive = false
        }
        return ctrl
    }

    override fun readAltKey(): Boolean {
        val alt = isAltActive
        if (alt) {
            isAltActive = false
        }
        return alt
    }

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

    override fun onEmulatorSet() {}

    // --- Logging methods for both interfaces ---

    override fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, "Stack trace", e)
    }

    companion object {
        private const val TAG = "FlideTerminalClient"
    }
}

fun TerminalSession.writeString(text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    write(bytes, 0, bytes.size)
}
