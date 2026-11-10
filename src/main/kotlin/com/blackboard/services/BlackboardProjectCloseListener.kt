package com.blackboard.services

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener

/**
 * Starts saving the note as soon as a project begins to close (also on IDE exit), so the write runs in the
 * background while the platform closes the project, and [BlackboardService.dispose] normally does not have to
 * block for it. Projects in which Blackboard was never opened are skipped: the service is not created for them.
 */
class BlackboardProjectCloseListener : ProjectCloseListener {
    override fun projectClosing(project: Project) {
        project.serviceIfCreated<BlackboardService>()?.flushAsync()
    }
}
