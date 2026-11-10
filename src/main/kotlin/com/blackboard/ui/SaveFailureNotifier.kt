package com.blackboard.ui

import com.blackboard.BlackboardBundle
import com.blackboard.services.FailureAlertPolicy
import com.blackboard.services.SaveStatus
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.StateFlow

/**
 * Shows a single balloon when saving keeps failing (see [FailureAlertPolicy]) and expires it once saving recovers.
 * Successful saves are never announced.
 */
class SaveFailureNotifier(
    private val project: Project,
    private val retry: () -> Unit,
) {
    private val policy = FailureAlertPolicy()
    private var notification: Notification? = null

    /** Suspends until the collecting scope is cancelled. */
    suspend fun watch(status: StateFlow<SaveStatus>) {
        try {
            status.collect { onStatus(it) }
        } finally {
            expire()
        }
    }

    private fun onStatus(status: SaveStatus) {
        when (policy.onStatus(status)) {
            FailureAlertPolicy.Decision.NOTIFY -> notify(status as SaveStatus.Failed)
            FailureAlertPolicy.Decision.EXPIRE -> expire()
            FailureAlertPolicy.Decision.NONE -> Unit
        }
    }

    private fun notify(status: SaveStatus.Failed) {
        expire()
        val content = BlackboardBundle.message(
            if (status.duringLoad) "notification.load.failed.content" else "notification.save.failed.content",
            status.error.message ?: status.error.javaClass.simpleName,
        )
        notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(BlackboardBundle.message("notification.save.failed.title"), content, NotificationType.ERROR)
            .addAction(NotificationAction.createSimpleExpiring(BlackboardBundle.message("status.retry")) { retry() })
            .also { it.notify(project) }
    }

    private fun expire() {
        notification?.expire()
        notification = null
    }

    companion object {
        const val NOTIFICATION_GROUP_ID = "Blackboard"
    }
}
