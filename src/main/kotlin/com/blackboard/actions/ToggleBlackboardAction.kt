package com.blackboard.actions

import com.blackboard.ui.BlackboardPanel
import com.blackboard.ui.BlackboardToolWindowFactory
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities

/**
 * Opens Blackboard with the caret in the note, or hides it and returns focus to where the user was typing before.
 *
 * - hidden, or shown but not focused → show if needed and focus the note editor;
 * - shown and focused → hide, return focus to the component focused before Blackboard was opened by this action.
 *
 * Opening by clicking the stripe button does not go through this action, so the platform's standard focus
 * behaviour applies there.
 */
class ToggleBlackboardAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(BlackboardToolWindowFactory.ID) ?: return
        if (toolWindow.isVisible && toolWindow.isActive) {
            panel(toolWindow)?.rememberViewState()
            toolWindow.hide { restoreFocus(project, toolWindow) }
        } else {
            // Hidden, or shown without focus: in both cases the user wants to type in the note now.
            rememberFocusOwner(project, toolWindow)
            toolWindow.activate({ panel(toolWindow)?.focusEditor() }, true, true)
        }
    }

    private fun panel(toolWindow: ToolWindow): BlackboardPanel? =
        toolWindow.contentManager.contents.firstNotNullOfOrNull { it.component as? BlackboardPanel }

    /** Stores the current focus owner unless it is Blackboard itself (then the previous target is kept). */
    private fun rememberFocusOwner(project: Project, toolWindow: ToolWindow) {
        val owner = IdeFocusManager.getInstance(project).focusOwner ?: return
        if (isInside(owner, toolWindow)) return
        project.putUserData(PREVIOUS_FOCUS, WeakReference(owner))
    }

    private fun restoreFocus(project: Project, toolWindow: ToolWindow) {
        val previous = project.getUserData(PREVIOUS_FOCUS)?.get()
        project.putUserData(PREVIOUS_FOCUS, null)
        if (previous != null && previous.isShowing && !isInside(previous, toolWindow)) {
            IdeFocusManager.getInstance(project).requestFocusInProject(previous, project)
        } else {
            // Opened from the stripe, or the old editor tab was closed meanwhile: fall back to the active editor.
            ToolWindowManager.getInstance(project).activateEditorComponent()
        }
    }

    private fun isInside(component: Component, toolWindow: ToolWindow): Boolean =
        SwingUtilities.isDescendingFrom(component, toolWindow.component)

    private companion object {
        /** Weak, so a closed editor tab is not kept alive by Blackboard. */
        val PREVIOUS_FOCUS = Key.create<WeakReference<Component>>("Blackboard.previousFocus")
    }
}
