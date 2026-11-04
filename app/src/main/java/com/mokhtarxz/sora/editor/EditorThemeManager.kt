package com.mokhtarxz.sora.editor

import android.content.Context
import android.util.Log
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File
import java.util.Locale

/**
 * Manages TextMate themes, grammars, and language resolution for Sora Editor.
 */
object EditorThemeManager {

    private const val TAG = "EditorThemeManager"
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            // 1. Register Assets File Resolver
            val fileProviderRegistry = FileProviderRegistry.getInstance()
            fileProviderRegistry.addFileProvider(AssetsFileResolver(context.assets))

            // 2. Load VS Code Dark+ Theme
            val themeRegistry = ThemeRegistry.getInstance()
            context.assets.open("textmate/themes/dark_plus.json").use { inStream ->
                val themeSource = IThemeSource.fromInputStream(inStream, "dark_plus.json", null)
                themeRegistry.loadTheme(themeSource)
            }
            themeRegistry.setTheme("Dark+ (default dark)")

            // 3. Load Grammars Index
            val grammarRegistry = GrammarRegistry.getInstance()
            grammarRegistry.loadGrammars("textmate/languages.json")

            isInitialized = true
            Log.i(TAG, "EditorThemeManager successfully initialized TextMate grammars and theme.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed initializing TextMate editor environment: ${e.message}", e)
        }
    }

    /**
     * Resolves the appropriate TextMate language for the given file extension.
     */
    fun getLanguageForFile(file: File): Language {
        val extension = file.extension.lowercase(Locale.US)
        val name = file.name.lowercase(Locale.US)

        return try {
            when {
                extension == "dart" -> TextMateLanguage.create("source.dart", true)
                extension == "yaml" || extension == "yml" || name == "pubspec.yaml" -> TextMateLanguage.create("source.yaml", true)
                extension == "json" -> TextMateLanguage.create("source.json", true)
                extension == "md" || extension == "markdown" -> TextMateLanguage.create("text.html.markdown", true)
                else -> EmptyLanguage()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed creating TextMateLanguage for $file: ${e.message}")
            EmptyLanguage()
        }
    }
}
