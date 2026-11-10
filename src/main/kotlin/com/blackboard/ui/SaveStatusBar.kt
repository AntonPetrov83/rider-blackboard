package com.blackboard.ui

import com.blackboard.BlackboardBundle
import com.blackboard.services.SaveStatus
import com.intellij.icons.AllIcons
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.FlowLayout
import javax.swing.Icon

/**
 * Save status, kept out of the way: nothing at all when the note is saved, a small "unsaved" dot over the editor
 * while saving ([savingIndicator]), and a text line at the bottom of the panel only when the user has to act
 * (failure with Retry, conflict). Problems are always spelled out in text, never shown by an icon or color alone.
 * Must be updated on the EDT.
 */
class SaveStatusBar(private val onRetry: () -> Unit) : JBPanel<SaveStatusBar>(FlowLayout(FlowLayout.LEFT, 0, 0)) {

    /** The same dot as on editor tabs with unsaved changes; place it over the editor (see [EditorOverlayPane]). */
    val savingIndicator = JBLabel(AllIcons.General.Modified).apply {
        toolTipText = BlackboardBundle.message("status.saving")
        accessibleContext.accessibleName = BlackboardBundle.message("status.accessible.value", toolTipText)
        isVisible = false
    }

    private val label = JBLabel().apply {
        font = JBFont.small()
        foreground = UIUtil.getContextHelpForeground()
        iconTextGap = JBUI.scale(4)
    }

    private val retryLink = ActionLink(BlackboardBundle.message("status.retry")) { onRetry() }.apply {
        font = JBFont.small()
        border = JBUI.Borders.emptyLeft(8)
        accessibleContext.accessibleName = BlackboardBundle.message("status.retry.accessible.name")
    }

    private var shown: SaveStatus? = null

    init {
        border = JBUI.Borders.empty(2, 8, 3, 8)
        isOpaque = false
        add(label)
        add(retryLink)
        getAccessibleContext().accessibleName = BlackboardBundle.message("status.accessible.name")
        update(SaveStatus.Loading)
    }

    fun update(status: SaveStatus) {
        if (status == shown) return
        shown = status
        savingIndicator.isVisible = status == SaveStatus.Saving
        when (status) {
            SaveStatus.Loading, SaveStatus.Saved, SaveStatus.Saving -> isVisible = false
            is SaveStatus.Failed -> {
                val text = BlackboardBundle.message(if (status.duringLoad) "status.load.failed" else "status.save.failed")
                showProblem(text, AllIcons.General.Error, status.error.message, retry = true)
            }
            SaveStatus.Conflict -> showProblem(BlackboardBundle.message("status.conflict"), AllIcons.General.Warning, null, retry = false)
        }
    }

    private fun showProblem(text: String, icon: Icon, details: String?, retry: Boolean) {
        label.text = text
        label.icon = icon
        label.toolTipText = details
        label.accessibleContext.accessibleName = BlackboardBundle.message("status.accessible.value", text)
        label.accessibleContext.accessibleDescription = details
        retryLink.isVisible = retry
        isVisible = true
        revalidate()
        repaint()
    }
}
