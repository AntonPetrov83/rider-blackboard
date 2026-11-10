package com.blackboard.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLayeredPane

/**
 * Shows the editor full-size with a small [overlay] floating in its bottom-right corner (left of the vertical
 * scroll bar), so a transient indicator takes no layout space and never shifts the text.
 */
class EditorOverlayPane(private val editor: EditorEx, private val overlay: JComponent) : JBLayeredPane() {

    init {
        isFullOverlayLayout = false
        add(editor.component, JLayeredPane.DEFAULT_LAYER as Any)
        add(overlay, JLayeredPane.PALETTE_LAYER as Any)
    }

    override fun doLayout() {
        editor.component.setBounds(0, 0, width, height)
        val size = overlay.preferredSize
        val scrollBar = editor.scrollPane.verticalScrollBar
        val scrollBarWidth = if (scrollBar.isVisible) scrollBar.width else 0
        val inset = JBUI.scale(6)
        overlay.setBounds(width - scrollBarWidth - inset - size.width, height - inset - size.height, size.width, size.height)
    }

    override fun getPreferredSize(): Dimension = editor.component.preferredSize

    override fun getMinimumSize(): Dimension = editor.component.minimumSize
}
