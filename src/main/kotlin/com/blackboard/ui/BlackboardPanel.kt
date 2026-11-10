package com.blackboard.ui

import com.blackboard.BlackboardBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * Main Blackboard UI. For now a placeholder in the center and an empty status line at the bottom;
 * the editor (step 4) and the save status (step 5) will replace them.
 */
class BlackboardPanel(@Suppress("unused") private val project: Project) :
    JBPanel<BlackboardPanel>(BorderLayout()), Disposable {

    private val placeholder = JBLabel(BlackboardBundle.message("panel.placeholder"), SwingConstants.CENTER).apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private val statusLabel = JBLabel().apply {
        font = JBFont.small()
        border = JBUI.Borders.empty(2, 8)
        accessibleContext.accessibleName = BlackboardBundle.message("status.accessible.name")
    }

    val preferredFocusComponent: JComponent
        get() = placeholder

    init {
        accessibleContext.accessibleName = BlackboardBundle.message("panel.accessible.name")
        add(placeholder, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    override fun dispose() {
        // Editor and coroutine scope will be released here in later steps.
    }
}
