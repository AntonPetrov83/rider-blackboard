package com.blackboard.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class BlackboardToolWindowFactory : ToolWindowFactory, DumbAware {
    companion object {
        /** Must match the `id` of the `<toolWindow>` in plugin.xml. */
        const val ID = "Blackboard"
    }


    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = BlackboardPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false).apply {
            setDisposer(panel)
            preferredFocusableComponent = panel.preferredFocusComponent
        }
        toolWindow.contentManager.addContent(content)
    }
}
