package com.blackboard.ui

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme
import java.awt.Font

/**
 * [delegate] (colors, highlighting) with the IDE default font from Settings | Editor | Font instead of the
 * color scheme's own font. A scheme often overrides the font with a larger one for code; the terminal starts from
 * the default font, so notes set this way look like the terminal.
 *
 * Read-only view: wrap it with [com.intellij.openapi.editor.ex.EditorEx.createBoundColorSchemeDelegate], which keeps
 * per-editor font changes (zoom) local instead of writing them into the global scheme.
 */
class DefaultFontColorScheme(delegate: EditorColorsScheme) : DelegateColorScheme(delegate) {

    private val font: FontPreferences = AppEditorFontOptions.getInstance().fontPreferences

    override fun getFontPreferences(): FontPreferences = font

    override fun getEditorFontName(): String = font.fontFamily

    override fun getEditorFontSize(): Int = font.getSize(font.fontFamily)

    override fun getEditorFontSize2D(): Float = font.getSize2D(font.fontFamily)

    override fun getLineSpacing(): Float = font.lineSpacing

    override fun getFont(key: EditorFontType): Font {
        val style = when (key) {
            EditorFontType.BOLD, EditorFontType.CONSOLE_BOLD -> Font.BOLD
            EditorFontType.ITALIC, EditorFontType.CONSOLE_ITALIC -> Font.ITALIC
            EditorFontType.BOLD_ITALIC, EditorFontType.CONSOLE_BOLD_ITALIC -> Font.BOLD or Font.ITALIC
            else -> Font.PLAIN
        }
        return Font(font.fontFamily, style, editorFontSize).deriveFont(editorFontSize2D)
    }
}
