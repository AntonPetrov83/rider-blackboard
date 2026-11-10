package com.blackboard.services

import com.blackboard.storage.BlackboardStorage
import com.blackboard.ui.SaveFailureNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Per-project note state: one instance per open project, so notes of different projects never mix.
 * Loading starts when the service is first requested; pending changes are flushed when the project closes.
 */
@Service(Service.Level.PROJECT)
class BlackboardService(project: Project, private val cs: CoroutineScope) : Disposable {

    private val core = BlackboardCore(storageFactory = { BlackboardStorage.forProject(project) }, scope = cs)

    init {
        core.load()
        // Project-level, so a persistent failure is reported even while the tool window is hidden.
        cs.launch { SaveFailureNotifier(project, core::retrySave).watch(core.status) }
    }

    val status: StateFlow<SaveStatus> get() = core.status

    val isLoaded: Boolean get() = core.isLoaded

    val currentRevision: Long get() = core.currentRevision

    fun currentText(): String = core.currentText()

    /** [text] must be an immutable snapshot, e.g. `Document.getImmutableCharSequence()`. */
    fun onTextChanged(text: CharSequence) = core.onTextChanged(text)

    fun retrySave() = core.retrySave()

    suspend fun flush() = core.flush()

    /** A scope that lives no longer than the project; the caller must cancel it when its UI is disposed. */
    fun createChildScope(name: String): CoroutineScope = cs.childScope(name)

    /** Called when the project closes: writes unsaved text, waiting at most [FLUSH_TIMEOUT_MS]. */
    override fun dispose() {
        core.flushBlocking(FLUSH_TIMEOUT_MS)
    }

    companion object {
        private const val FLUSH_TIMEOUT_MS = 3_000L

        fun getInstance(project: Project): BlackboardService = project.service()
    }
}
